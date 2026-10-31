package dev.aura.auradroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.aura.auradroid.data.trigger.TriggerReceiver
import dev.aura.auradroid.data.trigger.TriggerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AuraApplication : Application() {

    @Inject lateinit var triggerRepository: TriggerRepository

    override fun onCreate() {
        super.onCreate()
        // Re-arm every time rule on process start: alarms die with the
        // process, and waiting for the next boot to notice would miss them.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            triggerRepository.rulesNow()
                .filter { it.enabled && it.isTime }
                .forEach { TriggerReceiver.arm(this@AuraApplication, it) }
        }
    }
}
