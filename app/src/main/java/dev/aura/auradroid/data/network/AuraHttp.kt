package dev.aura.auradroid.data.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** What `POST /api/pair` hands back once a code is accepted. */
data class PairedResult(
    val token: String,
    val deviceName: String?,
    /** The certificate seen during a Wi-Fi pairing; null over loopback. */
    val certSha256: String? = null,
)

/** What the desktop is currently working on, from `GET /api/project`. */
data class ProjectInfo(
    val name: String,
    val language: String,
    val model: String,
    val models: List<ModelChoice>,
)

data class ModelChoice(
    val id: String,
    val name: String,
    val provider: String,
    val speed: String?,
)

/**
 * The server's three REST endpoints. Everything else is the WebSocket.
 *
 * Retrofit is deliberately not used: three calls returning loosely-shaped JSON
 * do not justify an interface, a converter, and a code generator.
 */
@Singleton
class AuraHttp @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * The plain client for loopback; a pinned one for the LAN.
     *
     * Built per call rather than cached: OkHttp shares its connection pool and
     * dispatcher across clients derived with newBuilder(), so this is cheap,
     * and it keeps the pin bound to the endpoint rather than to app state.
     */
    private fun clientFor(endpoint: Endpoint): OkHttpClient = when {
        !endpoint.secure -> client
        endpoint.certSha256 != null ->
            PinnedTls.pinned(endpoint.certSha256)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        // A TLS endpoint with nothing pinned would accept any certificate on
        // the network. Refusing is the only safe reading of that state.
        else -> throw IllegalStateException("Secure endpoint has no pinned certificate.")
    }

    /**
     * Probe an endpoint during pairing. Returns the project on success, or null
     * if the host is unreachable or the token is rejected — either way the user
     * needs to fix something before connecting.
     */
    suspend fun fetchProject(endpoint: Endpoint): ProjectInfo? = withContext(Dispatchers.IO) {
        val body = get(endpoint, "/api/project") ?: return@withContext null
        val obj = try {
            JsonParser.parseString(body).asJsonObject
        } catch (_: Exception) {
            return@withContext null
        }

        val models = obj.getAsJsonArray("models")?.mapNotNull { el ->
            val m = el.asJsonObject
            val id = m.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            ModelChoice(
                id = id,
                name = m.get("name")?.takeIf { !it.isJsonNull }?.asString ?: id,
                provider = m.get("provider")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                speed = m.get("speed")?.takeIf { !it.isJsonNull }?.asString,
            )
        } ?: emptyList()

        ProjectInfo(
            name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "project",
            language = obj.get("language")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            model = obj.get("model")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            models = models,
        )
    }

    /**
     * Trade a short pairing code for this device's own long token.
     *
     * The code is what a person can realistically type off a screen; the token
     * it returns is the actual credential and is never typed by anyone. Returns
     * null if the code was wrong, expired, or already used — the server answers
     * identically for all three on purpose, so there is nothing more specific
     * to report.
     */
    suspend fun redeemPairingCode(
        host: String,
        port: Int,
        code: String,
        secure: Boolean,
    ): PairedResult? = withContext(Dispatchers.IO) {
        // Over Wi-Fi this is the one exchange with no pin yet, so record which
        // certificate answered and hand it back to be stored alongside the
        // token. Everything afterwards is checked against it.
        var seenCert: String? = null
        val http = if (secure) {
            PinnedTls.trustOnFirstUse { seenCert = it }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        } else {
            client
        }

        val scheme = if (secure) "https" else "http"
        val payload = JsonObject().apply { addProperty("code", code.trim().uppercase()) }
        val request = Request.Builder()
            .url("$scheme://$host:$port/api/pair")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val obj = JsonParser.parseString(res.body?.string() ?: return@use null)
                    .asJsonObject
                val token = obj.get("token")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@use null
                // A secure pairing that somehow produced no certificate must not
                // be stored: it would leave an unpinned TLS endpoint behind.
                if (secure && seenCert == null) return@use null
                PairedResult(
                    token = token,
                    deviceName = obj.getAsJsonObject("device")
                        ?.get("name")?.takeIf { !it.isJsonNull }?.asString,
                    certSha256 = seenCert,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * File a memo into the desktop's episodic memory.
     *
     * Sent with the phone's own id so a retry after a dropped connection does
     * not leave the same thought in Aura's recall twice. Returns false when the
     * desktop is unreachable, so the caller can leave it unsynced and try later
     * rather than losing it.
     */
    suspend fun pushMemo(
        endpoint: Endpoint,
        id: String,
        title: String,
        text: String,
        atIso: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("id", id)
            addProperty("title", title)
            addProperty("text", text)
            addProperty("at", atIso)
        }
        val request = Request.Builder()
            .url(endpoint.httpUrl + "/api/memo")
            .header("X-Aura-Token", endpoint.token)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            clientFor(endpoint).newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Server-side history. The desktop keeps it in memory, so a phone that
     * reconnects mid-task can recover what it missed.
     */
    suspend fun fetchHistory(endpoint: Endpoint): List<JsonObject> = withContext(Dispatchers.IO) {
        val body = get(endpoint, "/api/history") ?: return@withContext emptyList()
        try {
            JsonParser.parseString(body).asJsonArray.map { it.asJsonObject }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun get(endpoint: Endpoint, path: String): String? = try {
        val request = Request.Builder()
            .url(endpoint.httpUrl + path)
            // Header rather than ?token= so the secret stays out of any
            // server-side request log that records query strings.
            .header("X-Aura-Token", endpoint.token)
            .build()
        clientFor(endpoint).newCall(request).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }
    } catch (_: Exception) {
        null
    }
}
