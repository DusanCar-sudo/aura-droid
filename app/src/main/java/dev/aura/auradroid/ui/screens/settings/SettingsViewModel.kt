package dev.aura.auradroid.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.network.AuraHttp
import dev.aura.auradroid.data.security.TokenVault
import dev.aura.auradroid.data.settings.Appearance
import dev.aura.auradroid.data.settings.AppearanceRepository
import dev.aura.auradroid.data.settings.ApprovalRepository
import dev.aura.auradroid.data.settings.ThemeMode
import dev.aura.auradroid.data.settings.ThemePalette
import dev.aura.auradroid.data.standalone.DirectProvider
import dev.aura.auradroid.data.standalone.ModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_NEEDED,
    READY,
}

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
    val appearance: Appearance = Appearance(),
    /** Whether every tool runs without asking, in any conversation. */
    val autoApproveAll: Boolean = false,
    /** Whether the agent may reach beyond the app's sandbox when asked. */
    val sandboxOut: Boolean = false,
    /** Shizuku status for elevated commands */
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    /** Active provider ID (e.g. openrouter, kilocode, gemini) */
    val activeProviderId: String? = null,
    /** Set of provider IDs that have saved keys/credentials */
    val savedProviders: Set<String> = emptySet(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: TokenVault,
    private val http: AuraHttp,
    private val memory: AgentMemory,
    private val appearanceRepo: AppearanceRepository,
    private val approvalRepo: ApprovalRepository,
    private val provider: DirectProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            updateShizukuStatus()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuStatus()
    }

    init {
        // Appearance is read reactively: a change here updates the theme of the
        // whole app (handled in MainActivity) as well as this screen.
        viewModelScope.launch {
            appearanceRepo.appearance.collect { ap ->
                _state.value = _state.value.copy(appearance = ap)
            }
        }
        viewModelScope.launch {
            approvalRepo.autoApproveAll.collect { on ->
                _state.value = _state.value.copy(autoApproveAll = on)
            }
        }
        viewModelScope.launch {
            approvalRepo.sandboxOut.collect { on ->
                _state.value = _state.value.copy(sandboxOut = on)
            }
        }
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {}
        updateShizukuStatus()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            vault.checkAndImportSeedCredentials()
            memory.checkAndSeedDurableMemories()
            val (lastBase, lastModel) = vault.lastProviderSettings()
            val standaloneOn = vault.standaloneSummary() != null
            val savedProv = vault.getSavedProviderIds()
            val activeId = vault.getActiveProviderId() ?: ModelFactory.detectProvider(lastBase)?.id

            _state.value = _state.value.copy(
                standalone = standaloneOn,
                baseUrl = lastBase,
                standaloneModel = lastModel,
                hasApiKey = vault.hasProviderKey(),
                memoryCount = memory.count(),
                savedProviders = savedProv,
                activeProviderId = activeId,
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

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            val factory = ModelFactory.findById(providerId)
            val saved = vault.loadProvider(providerId)
            val targetBase = saved?.baseUrl?.ifBlank { factory?.defaultBaseUrl.orEmpty() } ?: factory?.defaultBaseUrl.orEmpty()
            val targetModel = saved?.model?.ifBlank { factory?.defaultModel.orEmpty() } ?: factory?.defaultModel.orEmpty()
            val hasKey = vault.hasProviderKey(providerId)

            _state.value = _state.value.copy(
                activeProviderId = providerId,
                baseUrl = targetBase,
                standaloneModel = targetModel,
                hasApiKey = hasKey,
                saveError = null,
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
    fun enableStandalone(apiKey: String, baseUrl: String, model: String, providerId: String? = null) {
        viewModelScope.launch {
            if (baseUrl.isBlank() || model.isBlank()) {
                _state.value = _state.value.copy(
                    saveError = "Base URL and model are both needed.",
                )
                return@launch
            }
            val targetProvId = providerId ?: ModelFactory.detectProvider(baseUrl)?.id ?: "custom"
            val hasSavedKey = vault.hasProviderKey(targetProvId)
            if (apiKey.isBlank() && !hasSavedKey && !vault.hasProviderKey() && !baseUrl.trim().equals("local", true)) {
                _state.value = _state.value.copy(
                    saveError = "Enter an API key — there is none stored yet.",
                )
                return@launch
            }
            vault.saveProvider(
                providerId = targetProvId,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                activate = true,
            )
            val savedProv = vault.getSavedProviderIds()
            _state.value = _state.value.copy(
                standalone = true,
                baseUrl = baseUrl.trim(),
                standaloneModel = model.trim(),
                activeProviderId = targetProvId,
                savedProviders = savedProv,
                hasApiKey = true,
                saveError = null,
            )
        }
    }

    fun testStandalone(
        apiKey: String,
        baseUrl: String,
        model: String,
        providerId: String? = null,
        onResult: (Result<String>) -> Unit,
    ) {
        viewModelScope.launch {
            val key = if (apiKey.isNotBlank()) {
                apiKey
            } else {
                val targetProvId = providerId ?: ModelFactory.detectProvider(baseUrl)?.id
                if (targetProvId != null) {
                    vault.loadProvider(targetProvId)?.apiKey.orEmpty()
                } else {
                    vault.loadStandalone()?.apiKey.orEmpty()
                }
            }
            val result = provider.testConnection(key, baseUrl, model)
            onResult(result)
        }
    }

    fun disableStandalone() {
        viewModelScope.launch {
            // Keeps URL, model and key (still encrypted in the KeyStore): the
            // user asked for them to be remembered, and retyping a key on a
            // phone keyboard is the friction this avoids.
            vault.disableStandalone()
            _state.value = _state.value.copy(standalone = false)
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appearanceRepo.setThemeMode(mode) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { appearanceRepo.setThemePalette(palette) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { appearanceRepo.setDynamicColor(enabled) }
    }

    fun injectDurableMemories() {
        viewModelScope.launch {
            memory.injectDurableMemories()
            _state.value = _state.value.copy(memoryCount = memory.count())
        }
    }

    fun setAutoApproveAll(enabled: Boolean) {
        viewModelScope.launch { approvalRepo.setAutoApproveAll(enabled) }
    }

    fun setSandboxOut(enabled: Boolean) {
        viewModelScope.launch { approvalRepo.setSandboxOut(enabled) }
    }

    fun updateShizukuStatus() {
        val status = try {
            val installed = try {
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (!installed) {
                ShizukuStatus.NOT_INSTALLED
            } else if (!Shizuku.pingBinder()) {
                ShizukuStatus.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.PERMISSION_NEEDED
            } else {
                ShizukuStatus.READY
            }
        } catch (_: Throwable) {
            ShizukuStatus.NOT_RUNNING
        }
        _state.value = _state.value.copy(shizukuStatus = status)
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (_: Throwable) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {}
    }

    private companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }
}
