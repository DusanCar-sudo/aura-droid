package dev.aura.auradroid.ui.screens.triggers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.trigger.TriggerEngine
import dev.aura.auradroid.data.trigger.TriggerReceiver
import dev.aura.auradroid.data.trigger.TriggerRepository
import dev.aura.auradroid.data.trigger.TriggerRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TriggersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TriggerRepository,
    private val engine: TriggerEngine,
) : ViewModel() {

    val rules = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(rule: TriggerRule) = viewModelScope.launch {
        repository.add(rule)
        if (rule.enabled && rule.isTime) TriggerReceiver.arm(context, rule)
    }

    fun toggle(rule: TriggerRule) = viewModelScope.launch {
        val updated = rule.copy(enabled = !rule.enabled)
        repository.update(updated)
        if (updated.enabled && updated.isTime) TriggerReceiver.arm(context, updated)
        else TriggerReceiver.disarm(context, updated.id)
    }

    fun delete(rule: TriggerRule) = viewModelScope.launch {
        TriggerReceiver.disarm(context, rule.id)
        repository.remove(rule.id)
    }

    fun runNow(rule: TriggerRule) = engine.fire(rule.id, "manual run from Triggers")
}
