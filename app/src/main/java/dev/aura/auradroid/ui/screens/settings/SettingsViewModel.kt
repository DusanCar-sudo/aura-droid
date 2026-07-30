package dev.aura.auradroid.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.network.AuraHttp
import dev.aura.auradroid.data.security.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val host: String? = null,
    val port: Int? = null,
    val projectName: String? = null,
    val model: String? = null,
    val reachable: Boolean? = null,
    /** Short form of the pinned certificate; null on the loopback path. */
    val identity: String? = null,
    /** Standalone: the phone talks to a model directly, with no desktop. */
    val standalone: Boolean = false,
    val baseUrl: String = "",
    /** The model used in standalone; distinct from the desktop's [model]. */
    val standaloneModel: String = "",
    /** True once a key is stored; the key itself is never read back into UI. */
    val hasApiKey: Boolean = false,
    /** How many things the agent has remembered, for the memory row. */
    val memoryCount: Int = 0,
    val saveError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vault: TokenVault,
    private val http: AuraHttp,
    private val memory: AgentMemory,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Read first, whatever mode this phone is in. The setup form is
            // prefilled from these, and a form that forgets the endpoint every
            // time is why the API key kept getting retyped.
            val (lastBase, lastModel) = vault.lastProviderSettings()
            val standaloneOn = vault.standaloneSummary() != null

            _state.value = _state.value.copy(
                standalone = standaloneOn,
                baseUrl = lastBase,
                standaloneModel = lastModel,
                hasApiKey = vault.hasProviderKey(),
                memoryCount = memory.count(),
            )

            val endpoint = vault.load() ?: run {
                // No desktop paired is a normal state, not an error — clear the
                // desktop half of the screen and leave the rest.
                _state.value = _state.value.copy(
                    host = null, port = null, projectName = null,
                    model = null, reachable = null, identity = null,
                )
                return@launch
            }

            _state.value = _state.value.copy(
                host = endpoint.host,
                port = endpoint.port,
                // First eight hex characters, matching what `aura serve --lan`
                // prints, so the two can be compared by eye.
                identity = endpoint.certSha256
                    ?.replace(":", "")?.take(8)?.uppercase(),
            )

            val project = http.fetchProject(endpoint)
            _state.value = _state.value.copy(
                projectName = project?.name,
                model = project?.model,
                reachable = project != null,
            )
        }
    }

    /**
     * Turn standalone on, storing the credentials.
     *
     * A blank [apiKey] keeps whatever key is already stored. That is the whole
     * point: changing the model used to demand the full key again, so every
     * adjustment meant finding a fifty-character secret and typing it into a
     * phone.
     */
    fun enableStandalone(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            if (baseUrl.isBlank() || model.isBlank()) {
                _state.value = _state.value.copy(
                    saveError = "Base URL and model are both needed.",
                )
                return@launch
            }
            if (apiKey.isBlank() && !vault.hasProviderKey()) {
                _state.value = _state.value.copy(
                    saveError = "Enter an API key — there is none stored yet.",
                )
                return@launch
            }
            vault.saveStandalone(
                TokenVault.Standalone(apiKey = apiKey, baseUrl = baseUrl, model = model),
            )
            _state.value = _state.value.copy(
                standalone = true, baseUrl = baseUrl.trim(), standaloneModel = model.trim(),
                hasApiKey = true, saveError = null,
            )
        }
    }

    fun disableStandalone() {
        viewModelScope.launch {
            // Removes the key, not just the flag: leaving a provider credential
            // on a phone that is no longer using it is the thing this mode is
            // supposed to be careful about. The endpoint and model stay, since
            // neither is secret and both make turning it back on painless.
            vault.clearStandalone()
            _state.value = _state.value.copy(standalone = false, hasApiKey = false)
        }
    }

    /**
     * Forget the pairing. Also drops that KeyStore entry, so the stored
     * ciphertext is unrecoverable rather than merely unreferenced. The provider
     * key has its own entry and is untouched.
     */
    fun unpair(onDone: () -> Unit) {
        viewModelScope.launch {
            vault.clearPairing()
            refresh()
            onDone()
        }
    }

    fun forgetEverything() {
        viewModelScope.launch {
            memory.forgetAll()
            _state.value = _state.value.copy(memoryCount = 0)
        }
    }
}
