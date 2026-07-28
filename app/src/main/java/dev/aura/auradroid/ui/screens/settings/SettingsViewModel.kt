package dev.aura.auradroid.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.model.AuraConfig
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.repository.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: dev.aura.auradroid.data.repository.AuraRepository
) : ViewModel() {

    private val _config = MutableStateFlow(
        AuraConfig(
            model = "deepseek/deepseek-chat",
            providers = emptyList(),
            baseUrl = null,
            apiKey = null,
            mode = SessionMode.CODER,
            maxTurns = 50,
            autoApprove = false,
            enableGazelle = true,
            enableArchimedes = false
        )
    )
    val config: StateFlow<AuraConfig> = _config.asStateFlow()

    val availableModels = listOf(
        "deepseek/deepseek-chat",
        "deepseek/deepseek-reasoner",
        "claude/claude-sonnet-4-5-20251001",
        "claude/claude-opus-4-5-20250514",
        "gpt/gpt-4o",
        "gpt/gpt-4o-mini",
        "gemini/gemini-2.5-flash",
        "ollama/llama-3.3-70b"
    )

    val availableProviders = listOf(
        "DeepSeek",
        "Anthropic",
        "OpenAI",
        "Google",
        "Ollama",
        "OpenRouter"
    )

    fun updateApiKey(key: String) {
        _config.value = _config.value.copy(apiKey = key)
    }

    fun updateBaseUrl(url: String) {
        _config.value = _config.value.copy(baseUrl = url.ifBlank { null })
    }

    fun updateModel(model: String) {
        _config.value = _config.value.copy(model = model)
    }

    fun updateProvider(provider: String) {
        _config.value = _config.value.copy(provider = provider)
    }

    fun updateAutoApprove(enabled: Boolean) {
        _config.value = _config.value.copy(autoApprove = enabled)
    }

    fun updateEnableGazelle(enabled: Boolean) {
        _config.value = _config.value.copy(enableGazelle = enabled)
    }

    fun updateMaxTurns(turns: Int) {
        _config.value = _config.value.copy(maxTurns = turns)
    }

    fun saveConfig() {
        viewModelScope.launch {
            // Save to preferences or secure storage
        }
    }
}
