package dev.aura.auradroid.data.trigger

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The world half of triggering: when any app posts a notification, every
 * enabled NOTIFICATION rule that matches its package and/or text fires —
 * once, per cooldown, so a chatty app cannot run her in circles.
 */
@AndroidEntryPoint
class AuraNotificationListener : NotificationListenerService() {

    @Inject lateinit var repository: TriggerRepository
    @Inject lateinit var engine: TriggerEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val text = sequenceOf(
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE),
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT),
        ).filterNotNull().joinToString(" — ")
        if (text.isBlank()) return
        val sourcePackage = sbn.packageName

        scope.launch {
            repository.rulesNow()
                .filter { it.enabled && engine.cooledDown(it) && it.matches(sourcePackage, text) }
                .forEach { rule ->
                    engine.markFired(rule)
                    engine.fire(rule.id, "notification from $sourcePackage: \"$text\"")
                }
        }
    }
}
