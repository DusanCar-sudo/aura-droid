package dev.aura.auradroid.data.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge to the desktop's self-hosted GitHub App (`aura-git`).
 *
 * The phone never holds the GitHub private key. It asks the paired Aura server
 * over `/api/github`, which acts as the GitHub App on its behalf. This ships in
 * the app out of the box and is usable the moment the desktop is configured
 * with GITHUB_APP_ID / GITHUB_APP_PRIVATE_KEY — no per-phone setup required.
 */
@Singleton
class GitHubBridge @Inject constructor(private val http: AuraHttp) {

    /**
     * Raw call to the server's `/api/github` dispatcher. Returns the parsed JSON
     * body on success, or null when the server is unreachable or rejects the
     * request. Never throws across a network boundary.
     */
    suspend fun invoke(
        endpoint: Endpoint,
        action: String,
        params: Map<String, String> = emptyMap(),
    ): JsonObject? {
        val payload = JsonObject().apply {
            addProperty("action", action)
            params.forEach { (k, v) -> addProperty(k, v) }
        }
        val body = http.postJson(endpoint, "/api/github", payload) ?: return null
        return try {
            JsonParser.parseString(body).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    /** Repositories visible to the installation, newest-friendly order from the API. */
    suspend fun listRepos(endpoint: Endpoint): List<GitHubRepoSummary> {
        val obj = invoke(endpoint, "list_repos") ?: return emptyList()
        val arr = obj.get("repos")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.asJsonObject
            GitHubRepoSummary(
                fullName = o.get("full_name")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                private = o.get("private")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                language = o.get("language")?.takeIf { !it.isJsonNull }?.asString,
                description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
            )
        }
    }

    /** Whether the paired desktop can reach GitHub as the configured App. */
    suspend fun testConnection(endpoint: Endpoint): GitHubStatus {
        val obj = invoke(endpoint, "test_connection")
            ?: return GitHubStatus(ok = false, error = "No response from server")
        val ok = obj.get("ok")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val error = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
        val reposAccessible = obj.get("reposAccessible")?.takeIf { !it.isJsonNull }?.asInt
        return GitHubStatus(ok = ok, reposAccessible = reposAccessible, error = error)
    }
}

/** Lightweight repo summary returned by `/api/github` list_repos. */
data class GitHubRepoSummary(
    val fullName: String,
    val private: Boolean,
    val language: String?,
    val description: String?,
)

/** Result of the `test_connection` action. */
data class GitHubStatus(
    val ok: Boolean,
    val reposAccessible: Int? = null,
    val error: String? = null,
)
