package dev.aura.auradroid.data.repository

import com.google.gson.Gson
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.network.PlanStep
import dev.aura.auradroid.data.network.ServerEvent
import dev.aura.auradroid.data.network.StepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Folds the server's event stream into the Room-backed conversation.
 *
 * Deliberately no schema change: [dev.aura.auradroid.data.model.Message] already
 * carries `content`, `isStreaming`, `metadata`, `toolCalls`, and `errorMessage`,
 * and AuraDatabase is version 1 with no destructive-migration fallback — adding
 * a column would crash at runtime for anyone with the app already installed.
 *
 * Transient state (thinking, context health, the pending approval) lives in
 * StateFlows instead of the database. Persisting "the agent is thinking" would
 * mean replaying a stale spinner on every app restart.
 */
class EventSink(
    private val repository: MessageStore,
    private val gson: Gson,
) {
    /** True between `thinking` and the first text/tool event. */
    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    /** Set while the agent is blocked waiting for the user to allow or deny. */
    private val _pendingConfirm = MutableStateFlow<PendingConfirm?>(null)
    val pendingConfirm: StateFlow<PendingConfirm?> = _pendingConfirm.asStateFlow()

    /** Latest token accounting, for the status chip. Never persisted. */
    private val _contextHealth = MutableStateFlow<ContextSnapshot?>(null)
    val contextHealth: StateFlow<ContextSnapshot?> = _contextHealth.asStateFlow()

    /** The assistant message currently being streamed into, if any. */
    private var streamingMessageId: Long? = null
    private val streamBuffer = StringBuilder()
    private var lastFlushAt = 0L

    /**
     * Whether this turn already streamed assistant text.
     *
     * Deliberately turn-scoped rather than derived from [streamingMessageId]:
     * the server sends `text_end` before `done`, and `text_end` finalises the
     * stream, so by the time `done` arrives there is no live stream to observe.
     * Reading it there would conclude nothing was streamed and write the `done`
     * summary as a second copy of the reply. Cleared when the turn ends.
     */
    private var streamedThisTurn = false

    /**
     * Row holding the tool call awaiting its result.
     *
     * The server sends no correlation id — its own web UI pairs each tool_call
     * with the next tool_result positionally, and this mirrors that. It is
     * correct because the agent runs one tool at a time.
     */
    private var pendingToolMessageId: Long? = null

    /** Row holding the current plan; updated in place as steps progress. */
    private var planMessageId: Long? = null
    private var planSteps: List<PlanStep> = emptyList()
    private var planGoal: String? = null

    suspend fun handle(sessionId: String, event: ServerEvent) {
        when (event) {
            is ServerEvent.Connected -> Unit

            is ServerEvent.Thinking -> _thinking.value = true

            is ServerEvent.Text -> {
                _thinking.value = false
                appendStreamedText(sessionId, event.text)
            }

            is ServerEvent.TextEnd -> finishStream()

            is ServerEvent.ToolCall -> {
                _thinking.value = false
                val payload = ToolPayload(
                    name = event.name,
                    input = event.input?.toString(),
                    status = "RUNNING",
                )
                pendingToolMessageId = repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.TOOL,
                    content = event.name,
                    toolCalls = gson.toJson(payload),
                ).id
            }

            is ServerEvent.ToolResult -> updatePendingTool { existing ->
                existing.copy(status = "COMPLETED", result = event.result, ms = event.ms)
            }

            is ServerEvent.ToolBlocked -> updatePendingTool { existing ->
                existing.copy(status = "BLOCKED", result = event.reason)
            }

            is ServerEvent.ConfirmRequest ->
                _pendingConfirm.value = PendingConfirm(event.id, event.message)

            is ServerEvent.ConfirmTimeout -> {
                // The server already denied it; clear the sheet so the user is
                // not left answering a question that no longer matters.
                if (_pendingConfirm.value?.id == event.id) _pendingConfirm.value = null
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "Approval timed out — denied.",
                )
            }

            is ServerEvent.PlanCreating -> {
                _thinking.value = false
                planSteps = emptyList()
                planGoal = null
                planMessageId = repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "Planning…",
                    metadata = gson.toJson(PlanPayload(null, emptyList())),
                ).id
            }

            is ServerEvent.PlanCreated -> {
                planGoal = event.plan.goal
                planSteps = event.plan.steps
                writePlan(sessionId)
            }

            is ServerEvent.StepStarted -> {
                planSteps = planSteps.map {
                    if (it.id == event.step.id) it.copy(status = StepStatus.RUNNING) else it
                }
                writePlan(sessionId)
            }

            is ServerEvent.StepCompleted -> {
                planSteps = planSteps.map {
                    if (it.id == event.step.id) {
                        it.copy(
                            status = if (event.result != null) StepStatus.DONE else StepStatus.FAILED,
                            result = event.result,
                        )
                    } else {
                        it
                    }
                }
                writePlan(sessionId)
            }

            is ServerEvent.Artifact -> {
                _thinking.value = false
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = event.name,
                    metadata = gson.toJson(
                        ArtifactPayload(
                            id = event.id,
                            name = event.name,
                            content = event.content,
                            contentType = event.contentType,
                        )
                    ),
                )
            }

            is ServerEvent.PlanDone -> {
                writePlan(sessionId)
                planMessageId = null
                _thinking.value = false
                event.outcome?.takeIf { it.isNotBlank() }?.let {
                    repository.addMessage(sessionId, MessageRole.ASSISTANT, it)
                }
            }

            is ServerEvent.ContextHealth -> {
                _contextHealth.value = ContextSnapshot(
                    estimatedTokens = event.estimatedTokens,
                    contextWindow = event.contextWindow,
                    usagePercent = event.usagePercent,
                    totalCostUsd = event.totalCostUsd,
                    turnCount = event.turnCount,
                )
            }

            is ServerEvent.Compaction ->
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "Context compacted.",
                )

            is ServerEvent.Warning ->
                repository.addMessage(sessionId, MessageRole.SYSTEM, event.message)

            is ServerEvent.Error -> {
                finishStream()
                _thinking.value = false
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = event.message,
                    errorMessage = event.message,
                )
            }

            is ServerEvent.Done -> {
                // `done` carries the full summary text, which is normally the
                // same text already streamed. Write it only when this turn
                // streamed nothing — otherwise it renders as a second copy of
                // the reply the user just watched arrive.
                finishStream()
                _thinking.value = false
                _pendingConfirm.value = null
                if (!streamedThisTurn) {
                    event.text?.takeIf { it.isNotBlank() }?.let {
                        repository.addMessage(sessionId, MessageRole.ASSISTANT, it)
                    }
                }
                streamedThisTurn = false
            }

            is ServerEvent.ResetOk -> resetTransient()

            is ServerEvent.Unknown -> Unit
        }
    }

    /** Called when the socket drops, so a half-written message is not left live. */
    suspend fun onDisconnected() {
        finishStream()
        // The turn is over as far as this socket is concerned; leaving the flag
        // set would suppress the summary of whatever turn follows the reconnect.
        streamedThisTurn = false
        _thinking.value = false
        _pendingConfirm.value = null
    }

    fun clearPendingConfirm() {
        _pendingConfirm.value = null
    }

    private fun resetTransient() {
        streamingMessageId = null
        streamBuffer.setLength(0)
        streamedThisTurn = false
        pendingToolMessageId = null
        planMessageId = null
        planSteps = emptyList()
        _thinking.value = false
        _pendingConfirm.value = null
    }

    // ── Streaming ───────────────────────────────────────────────────────────

    /**
     * Text arrives token by token. Writing each one would be hundreds of
     * database round-trips per response, so buffer in memory and flush on a
     * ~10/sec budget; [finishStream] writes whatever is left.
     */
    private suspend fun appendStreamedText(sessionId: String, chunk: String) {
        streamBuffer.append(chunk)
        streamedThisTurn = true

        val existingId = streamingMessageId
        if (existingId == null) {
            // First chunk: create the row, which is itself the first render.
            streamingMessageId = repository.addMessage(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = streamBuffer.toString(),
                isStreaming = true,
            ).id
            lastFlushAt = System.currentTimeMillis()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastFlushAt >= FLUSH_INTERVAL_MS) {
            lastFlushAt = now
            repository.updateStreamingMessage(existingId, streamBuffer.toString(), isStreaming = true)
        }
    }

    private suspend fun finishStream() {
        val id = streamingMessageId ?: return
        repository.updateStreamingMessage(id, streamBuffer.toString(), isStreaming = false)
        streamingMessageId = null
        streamBuffer.setLength(0)
    }

    // ── Tools and plans ─────────────────────────────────────────────────────

    private suspend fun updatePendingTool(transform: (ToolPayload) -> ToolPayload) {
        val id = pendingToolMessageId ?: return
        val message = repository.getMessage(id) ?: return
        val existing = message.toolCalls
            ?.let { runCatching { gson.fromJson(it, ToolPayload::class.java) }.getOrNull() }
            ?: ToolPayload(name = message.content, input = null, status = "RUNNING")

        repository.updateMessage(
            message.copy(toolCalls = gson.toJson(transform(existing))),
        )
        pendingToolMessageId = null
    }

    private suspend fun writePlan(sessionId: String) {
        val id = planMessageId ?: return
        val message = repository.getMessage(id) ?: return
        repository.updateMessage(
            message.copy(
                content = planGoal ?: "Plan",
                metadata = gson.toJson(PlanPayload(planGoal, planSteps.map { it.toSnapshot() })),
            ),
        )
    }

    private companion object {
        /** ~10 writes/sec while streaming. */
        const val FLUSH_INTERVAL_MS = 100L
    }
}

data class PendingConfirm(val id: String, val message: String)

data class ContextSnapshot(
    val estimatedTokens: Int,
    val contextWindow: Int,
    val usagePercent: Double,
    val totalCostUsd: Double,
    val turnCount: Int,
)

/** Serialized into Message.toolCalls. */
data class ToolPayload(
    val name: String,
    val input: String?,
    val status: String,
    val result: String? = null,
    val ms: Long? = null,
)

/** Serialized into Message.metadata for the plan row. */
data class PlanPayload(
    val goal: String?,
    val steps: List<StepSnapshot>,
)

/** Serialized into Message.metadata for an artifact row. */
data class ArtifactPayload(
    val id: String,
    val name: String,
    val content: String,
    val contentType: String,
)

data class StepSnapshot(
    val id: String,
    val specialist: String,
    val task: String,
    val status: String,
    val result: String?,
)

private fun PlanStep.toSnapshot() = StepSnapshot(
    id = id,
    specialist = specialist,
    task = task,
    status = status.name,
    result = result,
)
