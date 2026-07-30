package dev.aura.auradroid.ui.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.network.AuraHttp
import dev.aura.auradroid.data.network.Endpoint
import dev.aura.auradroid.data.security.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Matches CODE_LENGTH in the desktop's server/devices.ts. */
const val PAIRING_CODE_LENGTH = 6

/**
 * Loopback is the USB/Termux path: traffic never leaves the phone, so cleartext
 * is both safe and the only thing the network security config permits. Anything
 * else is the Wi-Fi path and must be TLS with a pinned certificate.
 */
internal fun isLoopback(host: String): Boolean =
    host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"

data class PairingUiState(
    val host: String = "127.0.0.1",
    val port: String = "7337",
    val token: String = "",
    val testing: Boolean = false,
    val error: String? = null,
    /** Set once the endpoint answered and the pairing was stored. */
    val pairedProject: String? = null,
    // ── Standalone, prefilled from whatever was used last ────────────────────
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    /**
     * True when a provider key is already stored.
     *
     * The whole reason this is here: without it the setup form cannot tell
     * "no key yet" from "key already saved", so it demands one every time and
     * the user retypes a fifty-character secret to change a model name.
     */
    val hasApiKey: Boolean = false,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val http: AuraHttp,
    private val vault: TokenVault,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (baseUrl, model) = vault.lastProviderSettings()
            update {
                it.copy(
                    baseUrl = baseUrl.ifBlank { it.baseUrl },
                    model = model.ifBlank { it.model },
                    hasApiKey = vault.hasProviderKey(),
                )
            }
        }
    }

    fun onHostChange(v: String) = update { it.copy(host = v.trim(), error = null) }
    fun onPortChange(v: String) = update { it.copy(port = v.filter(Char::isDigit), error = null) }
    fun onTokenChange(v: String) = update { it.copy(token = v.trim(), error = null) }

    /**
     * Verify the endpoint before storing it.
     *
     * Pairing against an unreachable host or a stale token would "succeed" and
     * then fail later at the chat screen, where the cause is much less obvious.
     * GET /api/project exercises the same host, port, and token the socket will
     * use, so a pass here means the socket will connect.
     */
    fun pair() {
        val s = _state.value
        val port = s.port.toIntOrNull()

        if (s.host.isBlank()) return update { it.copy(error = "Enter the desktop's address.") }
        if (port == null || port !in 1..65535) return update { it.copy(error = "Port must be 1–65535.") }
        if (s.token.isBlank()) {
            return update { it.copy(error = "Enter the code from `aura devices add`.") }
        }

        viewModelScope.launch {
            update { it.copy(testing = true, error = null) }

            // A six-character entry is a pairing code, which the desktop swaps
            // for this device's own long token. Anything longer is taken as a
            // token already — that is what `aura serve` prints in its URL, and
            // it stays usable so a single-user setup needs no extra step.
            // Loopback means the traffic never leaves the phone (adb reverse or
            // Termux), so plain HTTP is fine and the platform permits it. Any
            // other address crosses the network, where the desktop serves TLS
            // and the certificate gets pinned.
            val overWifi = !isLoopback(s.host)

            val typed = s.token.trim()
            val endpoint = if (typed.length == PAIRING_CODE_LENGTH) {
                val paired = http.redeemPairingCode(s.host, port, typed, secure = overWifi)
                if (paired == null) {
                    update {
                        it.copy(
                            testing = false,
                            error = if (overWifi) {
                                "That code was not accepted. Codes last about ten " +
                                    "minutes and work once — run `aura devices add` on " +
                                    "the desktop for a fresh one. Also check the desktop " +
                                    "is running `aura serve --lan` and both are on the " +
                                    "same Wi-Fi."
                            } else {
                                "That code was not accepted. Codes last about ten " +
                                    "minutes and work once — run `aura devices add` on " +
                                    "the desktop for a fresh one. Also check `adb " +
                                    "reverse tcp:$port tcp:$port` is set up."
                            },
                        )
                    }
                    return@launch
                }
                Endpoint(
                    host = s.host,
                    port = port,
                    token = paired.token,
                    secure = overWifi,
                    certSha256 = paired.certSha256,
                )
            } else {
                // A raw token predates the pairing flow and only ever came from
                // `aura serve` on loopback, so there is no certificate to pin.
                Endpoint(host = s.host, port = port, token = typed, secure = false)
            }

            // Verify before storing: pairing that "succeeds" and then fails at
            // the chat screen hides the cause where it is hardest to see.
            val project = http.fetchProject(endpoint)
            if (project == null) {
                update {
                    it.copy(
                        testing = false,
                        error = "Could not reach ${endpoint.host}:${endpoint.port}. " +
                            if (overWifi) {
                                "Check the desktop is running `aura serve --lan` and " +
                                    "that both are on the same Wi-Fi."
                            } else {
                                "Check that `aura serve` is running, and — over USB — " +
                                    "that `adb reverse tcp:${endpoint.port} " +
                                    "tcp:${endpoint.port}` is set up."
                            },
                    )
                }
                return@launch
            }
            vault.save(endpoint)
            update { it.copy(testing = false, pairedProject = project.name) }
        }
    }

    /**
     * Skip the desktop entirely and run against a provider from this phone.
     *
     * Offered here because this screen is the first thing an unpaired install
     * shows, and without it someone with no desktop is stuck: the standalone
     * switch lives in Settings, and Settings is behind the chat screen, which
     * is behind pairing.
     */
    fun useStandalone(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            if (baseUrl.isBlank() || model.isBlank()) {
                update { it.copy(error = "Base URL and model are both needed.") }
                return@launch
            }
            // A blank key is only an error the first time. After that it means
            // "keep the one you have", which is what lets someone change model
            // without digging their API key out again.
            if (apiKey.isBlank() && !vault.hasProviderKey()) {
                update { it.copy(error = "Enter an API key — there is none stored yet.") }
                return@launch
            }
            vault.saveStandalone(
                TokenVault.Standalone(apiKey = apiKey, baseUrl = baseUrl, model = model),
            )
            // Same signal as a successful pairing, so navigation is identical.
            update {
                it.copy(
                    testing = false, error = null, hasApiKey = true,
                    pairedProject = "this phone",
                )
            }
        }
    }

    private inline fun update(block: (PairingUiState) -> PairingUiState) {
        _state.value = block(_state.value)
    }
}
