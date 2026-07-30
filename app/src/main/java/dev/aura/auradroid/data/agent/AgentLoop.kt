package dev.aura.auradroid.data.agent

import dev.aura.auradroid.data.standalone.Chunk
import dev.aura.auradroid.data.standalone.DirectProvider
import dev.aura.auradroid.data.standalone.ToolInvocation
import dev.aura.auradroid.data.standalone.Turn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** What the screen needs to know while the agent works. */
sealed interface AgentEvent {
    /** A fragment of prose, as it streams. */
    data class Text(val text: String) : AgentEvent

    /** A tool is about to run. [label] is what to show in the transcript. */
    data class ToolStarted(val id: String, val name: String, val label: String) : AgentEvent

    data class ToolFinished(
        val id: String,
        val name: String,
        val outcome: ToolOutcome,
        val ms: Long,
    ) : AgentEvent

    data class Failed(val message: String) : AgentEvent
    data object Done : AgentEvent
}

/**
 * Runs the model until it stops asking for tools.
 *
 * This is the difference between a chat window and an agent: a plain call
 * returns whatever the model can say in one breath, while this one hands back
 * every tool result and lets it keep going, so "check what's in my workspace
 * and summarise it" becomes three round-trips it drives itself.
 *
 * Bounded by [maxSteps] rather than trusted to terminate. A model that gets
 * confused about whether a tool succeeded will retry it indefinitely, and on a
 * phone that is someone's battery and their API credit, spent silently.
 */
@Singleton
class AgentLoop @Inject constructor(
    private val provider: DirectProvider,
    private val tools: PhoneTools,
) {

    fun run(
        apiKey: String,
        baseUrl: String,
        model: String,
        system: String,
        history: List<Turn>,
        sessionId: String?,
        maxSteps: Int = DEFAULT_MAX_STEPS,
        /** Asked before a tool that can change something outside the model. */
        approve: suspend (name: String, description: String) -> Boolean,
    ): Flow<AgentEvent> = flow {
        val conversation = history.toMutableList()

        for (step in 1..maxSteps) {
            val spoken = StringBuilder()
            var requested: List<ToolInvocation> = emptyList()
            var failure: String? = null

            provider.stream(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                system = system,
                history = conversation,
                tools = tools.specs(),
            ).collect { chunk ->
                when (chunk) {
                    is Chunk.Text -> {
                        spoken.append(chunk.text)
                        emit(AgentEvent.Text(chunk.text))
                    }
                    is Chunk.Calls -> requested = chunk.calls
                    is Chunk.Failed -> failure = chunk.message
                    Chunk.Done -> Unit
                }
            }

            failure?.let {
                emit(AgentEvent.Failed(it))
                return@flow
            }

            // Nothing more to run: whatever it just said is the answer.
            if (requested.isEmpty()) {
                emit(AgentEvent.Done)
                return@flow
            }

            // The assistant turn goes back verbatim, tool calls included. Sending
            // only the prose would leave the model unable to see that it already
            // asked, and it would ask again on the next pass.
            conversation += Turn(
                role = "assistant",
                content = spoken.toString().takeIf { it.isNotBlank() },
                toolCalls = requested,
            )

            for (call in requested) {
                val label = tools.describe(call.name, call.arguments)
                emit(AgentEvent.ToolStarted(call.id, call.name, label))

                val startedAt = System.currentTimeMillis()
                val outcome = if (tools.needsApproval(call.name) && !approve(call.name, label)) {
                    // A refusal is a result, not an error. Told plainly, the
                    // model works around it; left as a failure it retries.
                    ToolOutcome(
                        output = "The person declined to run this. Do not try it again — " +
                            "carry on without it or ask them what to do instead.",
                        failed = true,
                        summary = "declined",
                    )
                } else {
                    runCatching { tools.run(call.name, call.arguments, sessionId) }
                        .getOrElse { ToolOutcome("Tool failed: ${it.message}", failed = true) }
                }

                emit(
                    AgentEvent.ToolFinished(
                        id = call.id,
                        name = call.name,
                        outcome = outcome,
                        ms = System.currentTimeMillis() - startedAt,
                    ),
                )

                conversation += Turn(
                    role = "tool",
                    content = outcome.output,
                    toolCallId = call.id,
                )
            }
        }

        // Out of steps with tools still pending. Said out loud rather than
        // stopping quietly, because from the outside those look identical.
        emit(
            AgentEvent.Failed(
                "Stopped after $maxSteps steps without finishing. Ask again with a " +
                    "narrower request, or say what to do next.",
            ),
        )
    }

    private companion object {
        /**
         * Enough for read-think-write-verify, which covers nearly everything
         * asked of a phone, without letting a confused model run all afternoon.
         */
        const val DEFAULT_MAX_STEPS = 8
    }
}
