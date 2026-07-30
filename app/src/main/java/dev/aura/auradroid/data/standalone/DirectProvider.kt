package dev.aura.auradroid.data.standalone

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val JSON = "application/json; charset=utf-8".toMediaType()

/** A tool call the model wants run, as it came off the wire. */
data class ToolInvocation(
    val id: String,
    val name: String,
    /** Raw JSON arguments. Parsed by the executor, which knows the schema. */
    val arguments: String,
)

/**
 * One turn of a conversation, as the API wants it.
 *
 * Covers all four roles rather than just user and assistant, because a tool
 * loop has to send back what it did: the assistant turn that requested the
 * calls, and a tool turn per result. Sending only the prose would leave the
 * model asking for the same tool again on the next pass, forever.
 */
data class Turn(
    val role: String,
    val content: String? = null,
    /** Base64 JPEG bytes, on user turns, for vision-capable models. */
    val images: List<String> = emptyList(),
    /** On an assistant turn: what it asked to run. */
    val toolCalls: List<ToolInvocation> = emptyList(),
    /** On a tool turn: which call this is the result of. */
    val toolCallId: String? = null,
)

/** A tool offered to the model, with a JSON Schema for its arguments. */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/** What the phone sees while a reply arrives. */
sealed interface Chunk {
    data class Text(val text: String) : Chunk
    /** The model wants these run before it can continue. */
    data class Calls(val calls: List<ToolInvocation>) : Chunk
    data class Failed(val message: String) : Chunk
    data object Done : Chunk
}

/**
 * Talks to a model directly from the phone, for standalone mode.
 *
 * OpenAI-compatible rather than provider-specific: DeepSeek, Groq, OpenRouter,
 * Together and a local llama.cpp all speak it, so one client covers every key
 * the user is likely to already have, and switching provider is a base URL
 * rather than a code change.
 *
 * Streaming, because a phone on mobile data waiting silently for twenty
 * seconds looks broken, and the first words arriving is the difference between
 * thinking and hung.
 */
@Singleton
class DirectProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // No read timeout: a model thinking is not a stalled socket.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun stream(
        apiKey: String,
        baseUrl: String,
        model: String,
        system: String,
        history: List<Turn>,
        tools: List<ToolSpec> = emptyList(),
    ): Flow<Chunk> = callbackFlow {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)
            add("messages", messagesFor(system, history))
            if (tools.isNotEmpty()) {
                add("tools", toolsFor(tools))
                addProperty("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url(endpointFor(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val call = client.newCall(request)
        try {
            call.execute().use { res ->
                if (!res.isSuccessful) {
                    // The provider's own message is far more useful than a
                    // status code — it says "insufficient balance" or "model
                    // not found", which is exactly what the user must fix.
                    trySend(Chunk.Failed(describeFailure(res.code, res.body?.string())))
                    close()
                    return@use
                }
                val source = res.body?.source()
                if (source == null) {
                    trySend(Chunk.Failed("Empty response from the provider."))
                    close()
                    return@use
                }

                // Tool calls arrive split across deltas — the name in one, the
                // arguments a character at a time in the next twenty — keyed by
                // index rather than id, since only the first delta carries an
                // id. Assembled here and emitted once at the end of the turn.
                val pending = sortedMapOf<Int, PartialCall>()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break

                    val delta = deltaOf(payload) ?: continue
                    textOf(delta)?.takeIf { it.isNotEmpty() }?.let { trySend(Chunk.Text(it)) }
                    accumulateCalls(delta, pending)
                }

                val calls = pending.values.mapNotNull { it.toInvocation() }
                if (calls.isNotEmpty()) trySend(Chunk.Calls(calls))
                trySend(Chunk.Done)
                close()
            }
        } catch (e: Exception) {
            trySend(Chunk.Failed(e.message ?: "Could not reach the provider."))
            close()
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    // ── Request shaping ─────────────────────────────────────────────────────

    private fun messagesFor(system: String, history: List<Turn>): JsonArray = JsonArray().apply {
        if (system.isNotBlank()) {
            add(
                JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", system)
                },
            )
        }
        for (turn in history) add(messageFor(turn))
    }

    private fun messageFor(turn: Turn): JsonObject = JsonObject().apply {
        addProperty("role", turn.role)

        // Images force the content-parts form. Text-only turns stay as a plain
        // string: a handful of OpenAI-compatible servers still reject the array
        // form, and there is no reason to send it when nothing needs it.
        if (turn.images.isNotEmpty()) {
            add(
                "content",
                JsonArray().apply {
                    turn.content?.takeIf { it.isNotBlank() }?.let { text ->
                        add(
                            JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", text)
                            },
                        )
                    }
                    for (image in turn.images) {
                        add(
                            JsonObject().apply {
                                addProperty("type", "image_url")
                                add(
                                    "image_url",
                                    JsonObject().apply {
                                        addProperty("url", "data:image/jpeg;base64,$image")
                                    },
                                )
                            },
                        )
                    }
                },
            )
        } else {
            // Never null: an assistant turn that only asked for tools has no
            // prose, and some servers reject a missing content field outright.
            addProperty("content", turn.content.orEmpty())
        }

        turn.toolCallId?.let { addProperty("tool_call_id", it) }

        if (turn.toolCalls.isNotEmpty()) {
            add(
                "tool_calls",
                JsonArray().apply {
                    for (call in turn.toolCalls) {
                        add(
                            JsonObject().apply {
                                addProperty("id", call.id)
                                addProperty("type", "function")
                                add(
                                    "function",
                                    JsonObject().apply {
                                        addProperty("name", call.name)
                                        addProperty("arguments", call.arguments)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun toolsFor(tools: List<ToolSpec>): JsonArray = JsonArray().apply {
        for (tool in tools) {
            add(
                JsonObject().apply {
                    addProperty("type", "function")
                    add(
                        "function",
                        JsonObject().apply {
                            addProperty("name", tool.name)
                            addProperty("description", tool.description)
                            add("parameters", tool.parameters)
                        },
                    )
                },
            )
        }
    }

    // ── Response parsing ────────────────────────────────────────────────────

    /** The `delta` object of the first choice, or null for anything unusable. */
    private fun deltaOf(payload: String): JsonObject? = try {
        JsonParser.parseString(payload).asJsonObject
            .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("delta")
    } catch (_: Exception) {
        // A malformed chunk mid-stream should drop that chunk, not the reply.
        null
    }

    private fun textOf(delta: JsonObject): String? =
        delta.get("content")?.takeIf { !it.isJsonNull }?.asString

    private fun accumulateCalls(delta: JsonObject, into: MutableMap<Int, PartialCall>) {
        val array = delta.getAsJsonArray("tool_calls") ?: return
        for (element in array) {
            val obj = element.asJsonObject
            // Index is what ties fragments together. Absent, assume a single
            // call — which is what servers that omit it are describing.
            val index = obj.get("index")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val slot = into.getOrPut(index) { PartialCall() }

            obj.get("id")?.takeIf { !it.isJsonNull }?.let { slot.id = it.asString }
            obj.getAsJsonObject("function")?.let { function ->
                function.get("name")?.takeIf { !it.isJsonNull }?.let { slot.name = it.asString }
                function.get("arguments")?.takeIf { !it.isJsonNull }?.let {
                    slot.arguments.append(it.asString)
                }
            }
        }
    }

    private class PartialCall {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()

        fun toInvocation(): ToolInvocation? {
            val resolved = name ?: return null
            return ToolInvocation(
                // Some servers never send an id for a single call; the loop
                // only needs it to pair a result back, so any stable value does.
                id = id ?: "call_${resolved}_${arguments.length}",
                name = resolved,
                arguments = arguments.toString().ifBlank { "{}" },
            )
        }
    }

    private fun describeFailure(code: Int, body: String?): String {
        val detail = try {
            JsonParser.parseString(body ?: "").asJsonObject
                .getAsJsonObject("error")?.get("message")?.asString
        } catch (_: Exception) {
            null
        }
        return when {
            detail != null -> detail
            code == 401 -> "The API key was rejected."
            code == 404 -> "That model or base URL was not found."
            code == 429 -> "Rate limited by the provider."
            else -> "Provider returned $code."
        }
    }

    private companion object {
        /**
         * Accepts a base URL with or without the version and path, because
         * providers document it inconsistently and a user pasting the exact
         * string from their dashboard should not get a 404 for it.
         */
        fun endpointFor(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> "$trimmed/v1/chat/completions"
            }
        }
    }
}
