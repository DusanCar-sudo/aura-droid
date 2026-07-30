package dev.aura.auradroid.data.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Turns a raw server frame into a [ServerEvent].
 *
 * Separate from [AuraSocket] so the wire format can be tested against captured
 * server output without standing up a socket. Every branch here corresponds to
 * a `send(ws, { type: ... })` in aura-code's src/server/index.ts.
 */
object EventParser {

fun parse(raw: String): ServerEvent? {
    val obj = try {
        JsonParser.parseString(raw).asJsonObject
    } catch (_: Exception) {
        return null
    }
    val type = obj.get("type")?.asString ?: return null

    fun str(key: String): String? =
        obj.get(key)?.takeIf { !it.isJsonNull }?.asString

    return when (type) {
        "connected"    -> ServerEvent.Connected
        "thinking"     -> ServerEvent.Thinking
        "text"         -> ServerEvent.Text(str("text").orEmpty())
        "text_end"     -> ServerEvent.TextEnd
        "reset_ok"     -> ServerEvent.ResetOk
        "plan_creating"-> ServerEvent.PlanCreating

        "tool_call" -> ServerEvent.ToolCall(
            name = str("name").orEmpty(),
            input = obj.getAsJsonObject("input"),
        )

        "tool_result" -> ServerEvent.ToolResult(
            name = str("name").orEmpty(),
            result = str("result").orEmpty(),
            ms = obj.get("ms")?.takeIf { !it.isJsonNull }?.asLong,
        )

        "tool_blocked" -> ServerEvent.ToolBlocked(
            name = str("name").orEmpty(),
            reason = str("reason").orEmpty(),
        )

        "confirm_request" -> ServerEvent.ConfirmRequest(
            id = str("id") ?: return null,
            message = str("message").orEmpty(),
        )

        "confirm_timeout" -> ServerEvent.ConfirmTimeout(str("id") ?: return null)

        "plan_created" -> ServerEvent.PlanCreated(parsePlan(obj.getAsJsonObject("plan")))

        "step_started" -> parseStep(obj.getAsJsonObject("step"))
            ?.let { ServerEvent.StepStarted(it) }

        "step_completed" -> parseStep(obj.getAsJsonObject("step"))
            ?.let { ServerEvent.StepCompleted(it, str("result")) }

        "plan_done" -> ServerEvent.PlanDone(
            outcome = str("outcome"),
            success = obj.get("success")?.asBoolean ?: false,
        )

        "context_bar", "context_dashboard" -> {
            val h = obj.getAsJsonObject("health") ?: return null
            ServerEvent.ContextHealth(
                estimatedTokens = h.get("estimatedTokens")?.asInt ?: 0,
                contextWindow = h.get("contextWindow")?.asInt ?: 0,
                usagePercent = h.get("usagePercent")?.asDouble ?: 0.0,
                totalCostUsd = h.get("totalCostUsd")?.asDouble ?: 0.0,
                turnCount = h.get("turnCount")?.asInt ?: 0,
                dashboard = type == "context_dashboard",
            )
        }

        "compaction" -> ServerEvent.Compaction(obj)
        "warning"    -> ServerEvent.Warning(str("message").orEmpty())
        "error"      -> ServerEvent.Error(str("message").orEmpty())

        "done" -> ServerEvent.Done(
            text = str("text"),
            success = obj.get("success")?.asBoolean ?: false,
            turns = obj.get("turns")?.takeIf { !it.isJsonNull }?.asInt,
            toolCount = obj.get("toolCount")?.takeIf { !it.isJsonNull }?.asInt,
        )

        else -> ServerEvent.Unknown(type)
    }
}

private fun parsePlan(obj: JsonObject?): Plan {
    if (obj == null) return Plan(null, null, emptyList())
    val steps = obj.getAsJsonArray("steps")
        ?.mapNotNull { parseStep(it.asJsonObject) }
        ?: emptyList()
    return Plan(
        id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
        goal = obj.get("goal")?.takeIf { !it.isJsonNull }?.asString,
        steps = steps,
    )
}

private fun parseStep(obj: JsonObject?): PlanStep? {
    if (obj == null) return null
    val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return null
    return PlanStep(
        id = id,
        specialist = obj.get("specialist")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
        task = obj.get("task")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
    )
}
}
