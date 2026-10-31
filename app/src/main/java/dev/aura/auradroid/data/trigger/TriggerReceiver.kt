package dev.aura.auradroid.data.trigger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import dev.aura.auradroid.data.trigger.TriggerRule.Companion.DEFAULT_INTERVAL_MINUTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The clock half of triggering: one repeating exact alarm per enabled TIME
 * rule, re-armed after each fire so an edit or reboot cannot strand the
 * schedule. Intervals, not cron lines — "every N minutes" covers the tasks
 * actually wanted on a phone, and needs no cron parser to trust.
 */
@AndroidEntryPoint
class TriggerReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TriggerRepository
    @Inject lateinit var engine: TriggerEngine

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED -> rescheduleAll(context)
            else -> {
                val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: return
                engine.fire(ruleId, "scheduled time")
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repository.rulesNow().filter { it.enabled && it.isTime }.forEach { arm(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_RULE_ID = "rule_id"

        /** (Re)arm the repeating alarm for [rule]. Safe to call redundantly. */
        fun arm(context: Context, rule: TriggerRule) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                rule.id.hashCode(),
                Intent(context, TriggerReceiver::class.java).putExtra(EXTRA_RULE_ID, rule.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val interval = (rule.intervalMinutes.takeIf { it > 0 } ?: DEFAULT_INTERVAL_MINUTES) * 60_000L
            val at = System.currentTimeMillis() + interval
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }

        fun disarm(context: Context, ruleId: String) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                ruleId.hashCode(),
                Intent(context, TriggerReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
            pending?.let(manager::cancel)
        }
    }
}
