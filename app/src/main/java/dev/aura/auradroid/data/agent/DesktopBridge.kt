package dev.aura.auradroid.data.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import dev.aura.auradroid.data.network.Endpoint
import dev.aura.auradroid.data.network.EventParser
import dev.aura.auradroid.data.network.PinnedTls
import dev.aura.auradroid.data.network.ServerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot handoff from the phone-side agent to the paired desktop Aura server.
 *
 * This does not grant Android more privileges. It asks the already-paired
 * desktop agent to do work where the project and shell actually live, using the
 * same pinned endpoint and token as the normal remote chat path.
 */
@Singleton
class DesktopBridge @Inject constructor(
    private val gson: Gson,
) {

    suspend fun ask(endpoint: Endpoint, task: String): ToolOutcome = withContext(Dispatchers.IO) {
        val cleanTask = task.trim()
        if (cleanTask.isBlank()) {
            return@withContext ToolOutcome("ask_desktop needs a task.", failed = true)
        }

        val done = CompletableDeferred<ToolOutcome>()
        val text = StringBuilder()
        val tools = mutableListOf<String>()
        var socket: WebSocket? = null

        val request = Request.Builder().url(endpoint.wsUrl).build()
        socket = clientFor(endpoint).newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val payload = JsonObject().apply {
                        addProperty("type", "task")
                        addProperty("task", cleanTask)
                    }
                    webSocket.send(gson.toJson(payload))
                }

                override fun onMessage(webSocket: WebSocket, raw: String) {
                    when (val event = EventParser.parse(raw)) {
                        is ServerEvent.Text -> text.append(event.text)
                        is ServerEvent.ToolCall -> tools += "running ${event.name}"
                        is ServerEvent.ToolResult -> tools +=
                            "finished ${event.name}: ${event.result.lineSequence().firstOrNull().orEmpty()}"
                        is ServerEvent.ToolBlocked -> tools +=
                            "blocked ${event.name}: ${event.reason}"
                        is ServerEvent.ConfirmRequest -> {
                            tools += "approved desktop prompt: ${event.message.take(120)}"
                            webSocket.send(
                                gson.toJson(
                                    JsonObject().apply {
                                        addProperty("type", "confirm_response")
                                        addProperty("id", event.id)
                                        addProperty("approved", true)
                                    },
                                ),
                            )
                        }
                        is ServerEvent.Error -> complete(
                            done,
                            ToolOutcome("Desktop error: ${event.message}", failed = true),
                        )
                        is ServerEvent.Done -> complete(
                            done,
                            desktopOutcome(
                                text = event.text?.takeIf { it.isNotBlank() } ?: text.toString(),
                                tools = tools,
                                success = event.success,
                            ),
                        )
                        else -> Unit
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    complete(
                        done,
                        ToolOutcome(
                            "Could not reach the paired desktop: ${t.message ?: "connection failed"}",
                            failed = true,
                        ),
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!done.isCompleted) {
                        complete(done, ToolOutcome("Desktop connection closed: $reason", failed = true))
                    }
                }
            },
        )

        val outcome = withTimeoutOrNull(DESKTOP_TIMEOUT_MS) { done.await() }
            ?: ToolOutcome("Desktop task timed out after 2 minutes.", failed = true)
        socket?.close(1000, "desktop handoff complete")
        outcome
    }

    private fun clientFor(endpoint: Endpoint): OkHttpClient {
        val builder = if (endpoint.secure) {
            PinnedTls.pinned(
                endpoint.certSha256 ?: error("Secure endpoint has no pinned certificate."),
            )
        } else {
            OkHttpClient.Builder()
        }
        return builder
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun complete(done: CompletableDeferred<ToolOutcome>, outcome: ToolOutcome) {
        if (!done.isCompleted) done.complete(outcome)
    }

    private fun desktopOutcome(
        text: String,
        tools: List<String>,
        success: Boolean,
    ): ToolOutcome {
        val body = buildString {
            if (text.isNotBlank()) append(text.trim())
            if (tools.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Desktop activity:\n")
                tools.take(MAX_TOOL_LINES).forEach { append("- ").append(it.take(180)).append('\n') }
            }
        }.ifBlank { "Desktop finished without text." }

        return ToolOutcome(
            output = body.take(MAX_OUTPUT),
            failed = !success,
            summary = if (success) "desktop completed" else "desktop failed",
        )
    }

    private companion object {
        const val DESKTOP_TIMEOUT_MS = 120_000L
        const val MAX_OUTPUT = 24_000
        const val MAX_TOOL_LINES = 12
    }
}
