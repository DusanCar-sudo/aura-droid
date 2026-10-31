package dev.aura.auradroid.data.trigger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.MainActivity
import dev.aura.auradroid.data.agent.AgentEvent
import dev.aura.auradroid.data.agent.AgentLoop
import dev.aura.auradroid.data.agent.Persona
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.repository.AuraRepository
import dev.aura.auradroid.data.security.TokenVault
import dev.aura.auradroid.data.settings.ApprovalRepository
import dev.aura.auradroid.data.standalone.Turn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a fired rule into a real conversation: new session, the rule's prompt
 * as the user turn, the agent loop as usual, and a notification carrying the
 * answer. This is the piece that lets Aura act when nobody is looking at her.
 *
 * Background runs cannot pop the approval dialog, so a tool that wants
 * approval runs only when "auto-approve all" is already on; otherwise it is
 * denied and the answer says so. Silence is not an option the user chose.
 */
@Singleton
class TriggerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TriggerRepository,
    private val store: AuraRepository,
    private val agent: AgentLoop,
    private val memory: AgentMemory,
    private val vault: TokenVault,
    private val approvals: ApprovalRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Rule ids already firing right now, so a doubled alarm cannot double her. */
    private val inFlight = mutableSetOf<String>()

    /** Last fire per event rule, for the cooldown that stops a chatty app looping her. */
    private val lastFired = mutableMapOf<String, Long>()

    fun fire(ruleId: String, reason: String) {
        scope.launch {
            val rule = repository.ruleById(ruleId) ?: return@launch
            if (!rule.enabled) return@launch
            synchronized(inFlight) {
                // One run per rule at a time; skip rather than queue so a slow
                // model cannot stack up delayed copies of the same task.
                if (!inFlight.add(ruleId)) return@launch
            }
            try {
                run(rule, reason)
            } finally {
                synchronized(inFlight) { inFlight.remove(ruleId) }
            }
        }
    }

    /** True when [rule] may fire now — event rules cool down so a noisy app cannot loop her. */
    suspend fun cooledDown(rule: TriggerRule): Boolean {
        if (rule.isTime) return true
        val last = synchronized(lastFired) { lastFired[rule.id] } ?: 0L
        return System.currentTimeMillis() - last >= TriggerRule.EVENT_COOLDOWN_MS
    }

    suspend fun markFired(rule: TriggerRule) {
        if (!rule.isTime) synchronized(lastFired) { lastFired[rule.id] = System.currentTimeMillis() }
    }

    private suspend fun run(rule: TriggerRule, reason: String) {
        val creds = vault.loadStandalone()
        if (creds == null || creds.model.isBlank()) {
            notify(rule.label, "Not run: standalone model is not configured.")
            return
        }
        val autoApprove = approvals.autoApproveAll.first()

        val sessionId = store.createSession(
            title = rule.label,
            model = creds.model,
            provider = "trigger",
        ).id

        val prompt = "This task was started automatically. Trigger: $reason.\n\n${rule.prompt}"
        store.addMessage(sessionId, MessageRole.USER, "[trigger] $reason\n${rule.prompt}")

        val system = Persona.systemPrompt(
            mode = SessionMode.CODER,
            memoryBlock = memory.promptBlock(),
            toolsEnabled = true,
        )

        val answer = StringBuilder()
        var failure: String? = null
        agent.run(
            apiKey = creds.apiKey,
            baseUrl = creds.baseUrl,
            model = creds.model,
            system = system,
            history = listOf(Turn(role = "user", content = prompt)),
            sessionId = sessionId,
            approve = { _, _ -> autoApprove },
        ).collect { event ->
            when (event) {
                is AgentEvent.Text -> answer.append(event.text)
                is AgentEvent.Failed -> failure = event.message
                else -> Unit
            }
        }

        val text = failure ?: answer.toString().ifBlank { "(no reply)" }
        store.addMessage(sessionId, MessageRole.ASSISTANT, text)
        notify(rule.label, text)
    }

    private fun notify(title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Triggers", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            title.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            manager.notify(
                title.hashCode(),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(context.applicationInfo.icon)
                    .setContentTitle(title)
                    .setContentText(body.lineSequence().firstOrNull().orEmpty().take(200))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(2000)))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "aura_triggers"
    }
}
