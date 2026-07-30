package dev.aura.auradroid.data.network

import com.google.gson.JsonObject

/**
 * One message from `aura serve`, parsed off its `type` field.
 *
 * The wire protocol is defined by src/server/index.ts in aura-code. Every event
 * that server can emit has a case here; anything unrecognised becomes [Unknown]
 * rather than throwing, so a newer desktop talking to an older phone degrades
 * instead of crashing.
 */
sealed interface ServerEvent {

    /** Handshake accepted. */
    data object Connected : ServerEvent

    /** The agent is working but has not produced text yet. */
    data object Thinking : ServerEvent

    /** A chunk of assistant text. These arrive rapidly and must be coalesced. */
    data class Text(val text: String) : ServerEvent

    /** The current assistant message is complete. */
    data object TextEnd : ServerEvent

    /**
     * A tool is about to run.
     *
     * The server sends no correlation id, and its own web UI pairs each
     * [ToolCall] with the next [ToolResult] using a single variable. We mirror
     * that rather than inventing an id scheme the server would not honour.
     */
    data class ToolCall(val name: String, val input: JsonObject?) : ServerEvent

    /** The pending tool finished. [ms] is its wall-clock duration. */
    data class ToolResult(val name: String, val result: String, val ms: Long?) : ServerEvent

    /** The pending tool was refused by the permission system. */
    data class ToolBlocked(val name: String, val reason: String) : ServerEvent

    /** The agent needs approval before running something. Blocks until answered. */
    data class ConfirmRequest(val id: String, val message: String) : ServerEvent

    /** Nobody answered a [ConfirmRequest] in time; the server denied it. */
    data class ConfirmTimeout(val id: String) : ServerEvent

    /** Orchestrator is decomposing the task. */
    data object PlanCreating : ServerEvent

    data class PlanCreated(val plan: Plan) : ServerEvent
    data class StepStarted(val step: PlanStep) : ServerEvent
    data class StepCompleted(val step: PlanStep, val result: String?) : ServerEvent
    data class PlanDone(val outcome: String?, val success: Boolean) : ServerEvent

    /** Token accounting for the live session. */
    data class ContextHealth(
        val estimatedTokens: Int,
        val contextWindow: Int,
        val usagePercent: Double,
        val totalCostUsd: Double,
        val turnCount: Int,
        val dashboard: Boolean,
    ) : ServerEvent

    /** History was compacted to reclaim context. */
    data class Compaction(val raw: JsonObject) : ServerEvent

    data class Warning(val message: String) : ServerEvent
    data class Error(val message: String) : ServerEvent

    /** The task finished. */
    data class Done(
        val text: String?,
        val success: Boolean,
        val turns: Int?,
        val toolCount: Int?,
    ) : ServerEvent

    /** Server-side history was cleared. */
    data object ResetOk : ServerEvent

    /** A `type` this build does not know. Kept so the stream never fails hard. */
    data class Unknown(val type: String) : ServerEvent
}

data class Plan(
    val id: String?,
    val goal: String?,
    val steps: List<PlanStep>,
)

data class PlanStep(
    val id: String,
    val specialist: String,
    val task: String,
    /** Local view state, not sent by the server — see AuraSocket's plan tracking. */
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
)

enum class StepStatus { PENDING, RUNNING, DONE, FAILED }
