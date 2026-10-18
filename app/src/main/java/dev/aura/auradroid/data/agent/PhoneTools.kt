package dev.aura.auradroid.data.agent

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.export.CodeBlocks
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.security.TokenVault
import dev.aura.auradroid.data.settings.ApprovalRepository
import dev.aura.auradroid.data.shell.PhoneShell
import dev.aura.auradroid.data.standalone.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a tool did, as the model will read it back. */
data class ToolOutcome(
    val output: String,
    val failed: Boolean = false,
    /** Short form for the collapsed row in the transcript. */
    val summary: String = output.lineSequence().firstOrNull().orEmpty().take(80),
)

/**
 * The tools the agent can reach from a phone.
 *
 * Chosen for what an unrooted Android app can honestly do, which is narrower
 * than a desktop: its own directory, a toybox shell over that directory, the
 * network, the user's Downloads folder, and its own memory. Offering a tool
 * that then fails on every call is worse than not offering it — the model
 * spends turns retrying instead of saying it cannot.
 *
 * Nine of them, and not more. Every tool is described in full on every request,
 * so the list is a fixed cost per turn, and small models get measurably worse
 * at choosing once it runs long.
 */
@Singleton
class PhoneTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: PhoneShell,
    private val memory: AgentMemory,
    private val vault: TokenVault,
    private val desktop: DesktopBridge,
    private val approvalRepo: ApprovalRepository,
) {

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Whether this call must be approved first.
     *
     * Shell and desktop handoff always need it. File tools normally live
     * inside the workspace and are safe; with "Sandbox out" on they can reach
     * outside it, and a call whose path leaves the workspace is treated like
     * the shell — the user sees it coming.
     */
    suspend fun needsApproval(name: String, arguments: String): Boolean = when (name) {
        RUN_SHELL, ASK_DESKTOP -> true
        // A page the model just read can tell it to send what it found here in
        // a query string. Once anything local or fetched is in play, a URL that
        // can carry data out is shown to the person first.
        FETCH_URL -> tainted && carriesData(parse(arguments).stringOr("url", ""))
        READ_FILE, WRITE_FILE, LIST_FILES -> sandboxAllowed() && escapes(arguments)
        else -> false
    }

    /** Set once this turn has read local data or fetched content; cleared by [newTurn]. */
    @Volatile private var tainted = false

    fun newTurn() { tainted = false }

    private fun carriesData(url: String): Boolean {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return true
        return u.rawQuery != null || u.rawFragment != null || u.rawUserInfo != null ||
            (u.rawPath?.length ?: 0) > 80
    }

    private fun escapes(arguments: String): Boolean =
        PathGuard.escapes(parse(arguments).stringOr("path", ""))

    fun specs(): List<ToolSpec> = listOf(
        ToolSpec(
            name = REMEMBER,
            description = "Save something durable about this person or their work so " +
                "you still know it in future conversations. Use it for preferences, " +
                "decisions, names and ongoing projects — not for passing details.",
            parameters = schema(
                required = listOf("text"),
                "text" to string("The fact, in one sentence, written so it stands alone."),
                "tag" to string("A loose category: preference, project, person, fact."),
            ),
        ),
        ToolSpec(
            name = RECALL,
            description = "Search what you have remembered. Use it when the answer " +
                "depends on something the person told you before.",
            parameters = schema(
                required = listOf("query"),
                "query" to string("Words to look for. Empty returns the most-used notes."),
            ),
        ),
        ToolSpec(
            name = FORGET,
            description = "Delete a remembered note by id once it turns out to be wrong " +
                "or out of date. Ids come from recall.",
            parameters = schema(
                required = listOf("id"),
                "id" to string("The id of the note to delete."),
            ),
        ),
        ToolSpec(
            name = LIST_FILES,
            description = "List files in your workspace on this phone.",
            parameters = schema(
                required = emptyList(),
                "path" to string("Relative path inside the workspace. Defaults to the root."),
            ),
        ),
        ToolSpec(
            name = READ_FILE,
            description = "Read a text file from your workspace on this phone.",
            parameters = schema(
                required = listOf("path"),
                "path" to string("Relative path inside the workspace."),
            ),
        ),
        ToolSpec(
            name = WRITE_FILE,
            description = "Write a text file into your workspace on this phone. " +
                "Creates parent folders. Overwrites an existing file.",
            parameters = schema(
                required = listOf("path", "content"),
                "path" to string("Relative path inside the workspace."),
                "content" to string("The full contents to write."),
            ),
        ),
        ToolSpec(
            name = RUN_SHELL,
            description = "Run one shell command on this phone. " +
                "By default, standard toybox tools only (ls, cat, grep, find, wc, head, tail). " +
                "If 'elevated' is true, runs via Shizuku (uid 2000) allowing pm install, settings put, " +
                "dumpsys, and full /sdcard access (requires Shizuku to be active and permitted). " +
                "With 'Sandbox out' the command may leave the app workspace.",
            parameters = schema(
                required = listOf("command"),
                "command" to string("The command line to run."),
                "elevated" to JsonObject().apply {
                    addProperty("type", "boolean")
                    addProperty("description", "Set to true to run via Shizuku for system-wide access.")
                }
            ),
        ),
        ToolSpec(
            name = SAVE_DOWNLOAD,
            description = "Save a file to the phone's Downloads folder, where the person " +
                "can open it in other apps. Use this when they asked for a file rather " +
                "than for text on screen.",
            parameters = schema(
                required = listOf("filename", "content"),
                "filename" to string("File name with extension, e.g. plan.md."),
                "content" to string("The full contents of the file."),
            ),
        ),
        ToolSpec(
            name = FETCH_URL,
            description = "Fetch a web page or API response and read it as text. " +
                "HTML is stripped to its readable text.",
            parameters = schema(
                required = listOf("url"),
                "url" to string("The full https URL to fetch."),
            ),
        ),
        ToolSpec(
            name = ASK_DESKTOP,
            description = "Ask the paired desktop Aura agent to do work on the computer. " +
                "Use this when the task needs the real project files, desktop shell, git, " +
                "or tools that the Android app cannot access. Requires the person's approval.",
            parameters = schema(
                required = listOf("task"),
                "task" to string(
                    "A specific desktop task. Include the goal, files or commands to inspect, " +
                        "and what result should come back.",
                ),
            ),
        ),
    )

    /** A one-line description of a pending call, for the approval sheet. */
    fun describe(name: String, arguments: String): String {
        val args = parse(arguments)
        return when (name) {
            RUN_SHELL -> {
                val cmd = args.stringOr("command", "")
                val elev = if (args.get("elevated")?.asBoolean == true) "[elevated] " else ""
                "$elev$cmd"
            }
            WRITE_FILE -> "write ${args.stringOr("path", "?")}"
            SAVE_DOWNLOAD -> "save ${args.stringOr("filename", "?")} to Downloads"
            FETCH_URL -> "fetch ${args.stringOr("url", "?")}"
            ASK_DESKTOP -> "send to desktop: ${args.stringOr("task", "").take(160)}"
            else -> "$name ${arguments.take(120)}"
        }
    }

    suspend fun run(name: String, arguments: String, sessionId: String?): ToolOutcome {
        val args = parse(arguments)
        if (name in setOf(READ_FILE, LIST_FILES, RUN_SHELL, RECALL, FETCH_URL)) tainted = true
        return when (name) {
            REMEMBER -> doRemember(args, sessionId)
            RECALL -> doRecall(args)
            FORGET -> doForget(args)
            LIST_FILES -> doList(args)
            READ_FILE -> doRead(args)
            WRITE_FILE -> doWrite(args)
            RUN_SHELL -> doShell(args)
            SAVE_DOWNLOAD -> doSaveDownload(args)
            FETCH_URL -> doFetch(args)
            ASK_DESKTOP -> doAskDesktop(args)
            else -> ToolOutcome("No tool called $name.", failed = true)
        }
    }

    // ── Memory ──────────────────────────────────────────────────────────────

    private suspend fun doRemember(args: JsonObject, sessionId: String?): ToolOutcome {
        val text = args.stringOr("text", "")
        if (text.isBlank()) return ToolOutcome("remember needs text.", failed = true)
        val saved = memory.remember(text, args.stringOr("tag", "note"), sessionId)
            ?: return ToolOutcome("Too short to be worth remembering.", failed = true)
        return ToolOutcome("Remembered: ${saved.text}", summary = "remembered")
    }

    private suspend fun doRecall(args: JsonObject): ToolOutcome {
        val hits = memory.recall(args.stringOr("query", ""))
        if (hits.isEmpty()) return ToolOutcome("Nothing remembered about that.", summary = "no matches")
        val body = hits.joinToString("\n") { "${it.id} [${it.tag}] ${it.text}" }
        return ToolOutcome(body, summary = "${hits.size} note(s)")
    }

    private suspend fun doForget(args: JsonObject): ToolOutcome {
        val id = args.stringOr("id", "")
        if (id.isBlank()) return ToolOutcome("forget needs an id.", failed = true)
        memory.forget(id)
        return ToolOutcome("Forgotten.", summary = "forgotten")
    }

    // ── Workspace ───────────────────────────────────────────────────────────

    private suspend fun doList(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val dir = resolve(args.stringOr("path", ""))
            ?: return@withContext outsideWorkspace()
        if (!dir.exists()) return@withContext ToolOutcome("No such folder.", failed = true)
        if (!dir.isDirectory) return@withContext ToolOutcome("Not a folder.", failed = true)

        val entries = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        if (entries.isEmpty()) return@withContext ToolOutcome("(empty)", summary = "empty")
        val body = entries.joinToString("\n") {
            if (it.isDirectory) "${it.name}/" else "${it.name}  ${it.length()}b"
        }
        ToolOutcome(body, summary = "${entries.size} entries")
    }

    private suspend fun doRead(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val file = resolve(args.stringOr("path", "")) ?: return@withContext outsideWorkspace()
        if (!file.isFile) return@withContext ToolOutcome("No such file.", failed = true)
        // Bounded: the model pays for every character of this, and a log file
        // would blow the context window on its way to being useless.
        val text = file.readText().take(MAX_READ)
        val suffix = if (file.length() > MAX_READ) "\n…(truncated)" else ""
        ToolOutcome(text + suffix, summary = "read ${file.name}")
    }

    private suspend fun doWrite(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val file = resolve(args.stringOr("path", "")) ?: return@withContext outsideWorkspace()
        val content = args.stringOr("content", "")
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }.onFailure {
            return@withContext ToolOutcome("Could not write: ${it.message}", failed = true)
        }
        ToolOutcome(
            "Wrote ${content.length} characters to ${relative(file)}.",
            summary = "wrote ${file.name}",
        )
    }

    private suspend fun doShell(args: JsonObject): ToolOutcome {
        val command = args.stringOr("command", "")
        val elevated = args.get("elevated")?.let { if (it.isJsonPrimitive) it.asBoolean else false } ?: false
        if (command.isBlank()) return ToolOutcome("run_shell needs a command.", failed = true)
        
        // Pinned to the workspace unless "Sandbox out" is on.
        val here = if (sandboxAllowed() || elevated) {
            // Elevated implies sandbox out is essentially granted for this process (since it runs as adb).
            // but we'll try to stick to shell.cwd if it's not home.
            if (shell.cwd == shell.home()) workspace() else shell.cwd
        } else {
            workspace()
        }
        val result = shell.run(command, inDirectory = here, elevated = elevated)
        val body = result.output.take(MAX_READ).ifBlank { "(no output)" }
        return ToolOutcome(
            output = "exit ${result.exitCode}\n$body",
            failed = result.exitCode != 0,
            summary = command.take(60),
        )
    }

    private suspend fun doSaveDownload(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val name = args.stringOr("filename", "").ifBlank { "aura.txt" }
        // A path separator here would write outside Downloads on the pre-Q
        // branch, which takes a plain File. Names only.
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
        val where = CodeBlocks.saveToDownloads(context, safe, args.stringOr("content", ""))
            ?: return@withContext ToolOutcome("Could not save to Downloads.", failed = true)
        ToolOutcome("Saved to $where.", summary = "saved $safe")
    }

    // ── Network ─────────────────────────────────────────────────────────────

    private suspend fun doFetch(args: JsonObject): ToolOutcome = withContext(Dispatchers.IO) {
        val url = args.stringOr("url", "").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ToolOutcome("fetch_url needs an http(s) URL.", failed = true)
        }
        runCatching {
            // Redirects are followed by hand and stop at a new host: a redirect is
            // as good as a query string for carrying data somewhere else.
            val origin = url.toHttpUrlOrNull()?.host
            var current = url
            var hops = 0
            var call = http.newCall(Request.Builder().url(current).build()).execute()
            while (call.isRedirect && hops++ < 5) {
                val next = call.header("Location")?.let { call.request.url.resolve(it) }
                call.close()
                if (next == null || next.host != origin) {
                    return@withContext ToolOutcome("Redirected to another host; not followed.", failed = true)
                }
                current = next.toString()
                call = http.newCall(Request.Builder().url(current).build()).execute()
            }
            call.use { res ->
                if (!res.isSuccessful) {
                    return@withContext ToolOutcome("HTTP ${res.code} from $url.", failed = true)
                }
                val type = res.header("Content-Type").orEmpty()
                val raw = res.body?.string().orEmpty()
                val text = if (type.contains("html", ignoreCase = true)) strip(raw) else raw
                ToolOutcome(text.take(MAX_READ), summary = url.take(60))
            }
        }.getOrElse {
            ToolOutcome("Could not fetch $url: ${it.message}", failed = true)
        }
    }

    // ── Desktop handoff ────────────────────────────────────────────────────

    private suspend fun doAskDesktop(args: JsonObject): ToolOutcome {
        val endpoint = vault.load()
            ?: return ToolOutcome(
                "No paired desktop is configured. Pair one from Settings first.",
                failed = true,
            )
        return desktop.ask(endpoint, args.stringOr("task", ""))
    }

    /**
     * HTML down to the words on the page.
     *
     * Not a parser, and does not need to be: scripts and styles out, tags out,
     * entities in, whitespace collapsed. What is left is what the model would
     * have read anyway, at a fraction of the tokens of raw markup.
     */
    private fun strip(html: String): String = html
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        .replace(Regex("(?is)<!--.*?-->"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    // ── Paths ───────────────────────────────────────────────────────────────

    /** The agent's own folder, kept apart from the database and the token. */
    private fun workspace(): File =
        File(context.filesDir, WORKSPACE).apply { if (!exists()) mkdirs() }

    /**
     * A path inside the workspace, or null if it escapes.
     *
     * Canonicalised before the check, so `../../databases/aura_database` is
     * caught rather than merely looking wrong. The model has no business
     * reading the conversation database or the encrypted pairing token, and it
     * will happily try if asked to "find your config".
     *
     * With "Sandbox out" on, paths that escape the workspace are allowed —
     * that is the whole point of the setting — but the app's own private
     * data (conversation database, pairing token, provider key) stays closed
     * no matter what, from an agent that can now read anything else.
     */
    private suspend fun resolve(path: String): File? {
        val root = workspace().canonicalFile
        val target = if (path.isBlank()) root else File(root, path.trimStart('/')).canonicalFile
        val inside = target == root || target.path.startsWith(root.path + File.separator)
        if (inside) return target

        if (!sandboxAllowed()) return null

        // The whole app data dir, not just filesDir: the conversation database
        // (databases/) and shared_prefs sit beside it.
        val private = listOfNotNull(context.dataDir, context.getExternalFilesDir(null)?.parentFile)
            .map { it.canonicalFile }
        val isPrivate = private.any { target == it || target.path.startsWith(it.path + File.separator) }
        return target.takeIf { !isPrivate }
    }

    private suspend fun sandboxAllowed(): Boolean =
        approvalRepo.sandboxOut.first()

    private fun relative(file: File): String {
        val root = workspace().canonicalFile.path
        val p = file.path
        return if (p.startsWith(root + File.separator)) p.removePrefix(root).trimStart('/')
        else p
    }

    private fun outsideWorkspace() =
        ToolOutcome("That path is outside your workspace.", failed = true)

    // ── JSON helpers ────────────────────────────────────────────────────────

    /**
     * Arguments as an object, however mangled they arrived.
     *
     * Small models return `"{}"`, an empty string, or occasionally a JSON
     * string containing JSON. All of those should mean "no arguments" rather
     * than an exception that kills the turn.
     */
    private fun parse(arguments: String): JsonObject = runCatching {
        when (val parsed = JsonParser.parseString(arguments.ifBlank { "{}" })) {
            is com.google.gson.JsonObject -> parsed
            else -> JsonParser.parseString(parsed.asString).asJsonObject
        }
    }.getOrDefault(JsonObject())

    private fun JsonObject.stringOr(key: String, fallback: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.let {
            // Numbers and booleans arrive unquoted often enough to be worth
            // taking as text rather than refusing.
            if (it.isJsonPrimitive) it.asString else it.toString()
        } ?: fallback

    private fun string(description: String): JsonObject = JsonObject().apply {
        addProperty("type", "string")
        addProperty("description", description)
    }

    private fun schema(required: List<String>, vararg properties: Pair<String, JsonObject>) =
        JsonObject().apply {
            addProperty("type", "object")
            add(
                "properties",
                JsonObject().apply { properties.forEach { (name, spec) -> add(name, spec) } },
            )
            add(
                "required",
                com.google.gson.JsonArray().apply { required.forEach { add(it) } },
            )
        }

    companion object {
        const val REMEMBER = "remember"
        const val RECALL = "recall"
        const val FORGET = "forget"
        const val LIST_FILES = "list_files"
        const val READ_FILE = "read_file"
        const val WRITE_FILE = "write_file"
        const val RUN_SHELL = "run_shell"
        const val SAVE_DOWNLOAD = "save_to_downloads"
        const val FETCH_URL = "fetch_url"
        const val ASK_DESKTOP = "ask_desktop"

        private const val WORKSPACE = "workspace"
        private const val MAX_READ = 24_000
    }
}
