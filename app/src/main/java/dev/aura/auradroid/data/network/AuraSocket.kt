package dev.aura.auradroid.data.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/** Where a paired desktop lives, and the token that authenticates to it. */
data class Endpoint(
    val host: String,
    val port: Int,
    val token: String,
    /** false for loopback (adb reverse / Termux), true for the LAN path. */
    val secure: Boolean = false,
    /**
     * SHA-256 of the desktop's certificate, colon-hex, pinned at pairing.
     *
     * The desktop issues its own certificate — no public CA will sign for a
     * private address — so "is this signed by someone trusted" is unanswerable
     * and the only meaningful question is "is this the same machine I paired
     * with". Required whenever [secure] is set; a LAN connection without a pin
     * would trust any certificate on the network, which is no protection at all.
     */
    val certSha256: String? = null,
) {
    val httpUrl: String get() = "${if (secure) "https" else "http"}://$host:$port"
    val wsUrl: String get() = "${if (secure) "wss" else "ws"}://$host:$port/?token=$token"

    /** Never log or display the raw token. */
    override fun toString(): String = "Endpoint($host:$port, secure=$secure, token=***)"
}

sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    /** [retryInSeconds] is null when we have given up. */
    data class Reconnecting(val attempt: Int, val retryInSeconds: Int) : ConnState
    data class Failed(val reason: String) : ConnState
}

/**
 * WebSocket client for `aura serve`.
 *
 * The desktop's HTTP surface is small (`/api/history`, `/api/project`,
 * `/api/reset`) and everything interesting — streaming text, tool calls, plan
 * progress, approval prompts — arrives over one socket. So this is the primary
 * transport, not a supplement to a REST API.
 *
 * Note OkHttp sends no `Origin` header on a WebSocket handshake, which is what
 * lets a native client through the server's Origin allowlist while still
 * rejecting cross-site browser connections. Do not add one.
 */
@Singleton
class AuraSocket @Inject constructor(
    private val gson: Gson,
) {
    private fun clientFor(endpoint: Endpoint): OkHttpClient {
        val builder = if (endpoint.secure) {
            // wss:// to a self-signed desktop: the pin is the whole trust
            // decision, and its absence means there is none to make.
            PinnedTls.pinned(
                endpoint.certSha256
                    ?: error("Secure endpoint has no pinned certificate."),
            )
        } else {
            OkHttpClient.Builder()
        }
        return builder
            // Android silently drops idle sockets; without pings a long agent
            // turn dies mid-task with no close frame.
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            // No read timeout: a socket waiting on a slow model is not stalled.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _events = MutableSharedFlow<ServerEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private var endpoint: Endpoint? = null
    private var reconnectJob: Job? = null
    private var attempt = 0

    /** Set when the user disconnects, so we do not fight them by reconnecting. */
    private var intentionallyClosed = false

    private lateinit var scope: CoroutineScope

    fun connect(endpoint: Endpoint, scope: CoroutineScope) {
        this.scope = scope
        this.endpoint = endpoint
        intentionallyClosed = false
        attempt = 0
        openSocket()
    }

    fun disconnect() {
        intentionallyClosed = true
        reconnectJob?.cancel()
        socket?.close(NORMAL_CLOSURE, "client disconnect")
        socket = null
        _state.value = ConnState.Disconnected
    }

    private fun openSocket() {
        val ep = endpoint ?: return
        _state.value = if (attempt == 0) ConnState.Connecting else _state.value

        val request = Request.Builder()
            .url(ep.wsUrl)
            .build()

        socket = clientFor(ep).newWebSocket(request, Listener())
    }

    // ── Outbound ────────────────────────────────────────────────────────────

    /** Ask the agent to do something. [model] null means the server's default. */
    fun sendTask(task: String, model: String? = null): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "task")
            addProperty("task", task)
            model?.let { addProperty("model", it) }
        }
        return send(payload)
    }

    /** Answer a [ServerEvent.ConfirmRequest]. The agent is blocked until this lands. */
    fun sendConfirm(id: String, approved: Boolean): Boolean {
        val payload = JsonObject().apply {
            addProperty("type", "confirm_response")
            addProperty("id", id)
            addProperty("approved", approved)
        }
        return send(payload)
    }

    /** Clear the server's in-memory history. */
    fun sendReset(): Boolean =
        send(JsonObject().apply { addProperty("type", "reset") })

    private fun send(payload: JsonObject): Boolean {
        val ws = socket ?: return false
        return ws.send(gson.toJson(payload))
    }

    // ── Inbound ─────────────────────────────────────────────────────────────

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            _state.value = ConnState.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = EventParser.parse(text) ?: return
            // tryEmit rather than emit: this callback is not a coroutine, and
            // the 256-slot buffer absorbs burst traffic from streaming text.
            _events.tryEmit(event)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // A 401 means the token is wrong or the server restarted and issued
            // a new one. Retrying cannot fix that, so surface it instead of
            // looping — the user needs to re-pair.
            if (response?.code == 401) {
                _state.value = ConnState.Failed(
                    "Rejected by the desktop (401). The server was probably " +
                        "restarted and issued a new token — pair again."
                )
                return
            }
            scheduleReconnect(t.message ?: "connection failed")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!intentionallyClosed) scheduleReconnect(reason.ifBlank { "closed" })
        }
    }

    // ── Reconnect ───────────────────────────────────────────────────────────

    private fun scheduleReconnect(reason: String) {
        if (intentionallyClosed) return
        if (attempt >= MAX_ATTEMPTS) {
            _state.value = ConnState.Failed("$reason (gave up after $MAX_ATTEMPTS attempts)")
            return
        }

        attempt++
        // 1s, 2s, 4s … capped at 30s. Phones move between networks constantly,
        // so retry rather than surfacing every transient drop to the user.
        val delaySeconds = min(BASE_DELAY_S * 2.0.pow(attempt - 1).toInt(), MAX_DELAY_S)
        _state.value = ConnState.Reconnecting(attempt, delaySeconds)

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delaySeconds * 1000L)
            if (!intentionallyClosed) openSocket()
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val MAX_ATTEMPTS = 8
        const val BASE_DELAY_S = 1
        const val MAX_DELAY_S = 30
    }
}
