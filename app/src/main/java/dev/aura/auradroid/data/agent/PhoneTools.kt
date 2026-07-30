package dev.aura.auradroid.data.agent

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.export.CodeBlocks
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.shell.PhoneShell
import dev.aura.auradroid.data.standalone.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Tools whose effects the user should see coming. */
    fun needsApproval(name: String): Boolean = name == RUN_SHELL

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
            description = "Run one shell command in your workspace on this phone. " +
                "Standard toybox tools only (ls, cat, grep, find, wc, head, tail). " +
                "No root, and nothing outside the app's own storage.",
            parameters = schema(
                required = listOf("command"),
                "command" to string("The command line to run."),
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
    )

    /** A one-line description of a pending call, for the approval sheet. */
    fun describe(name: String, arguments: String): String {
        val args = parse(arguments)
        return when (name) {
            RUN_SHELL -> args.stringOr("command", "")
            WRITE_FILE -> "write ${args.stringOr("path", "?")}"
            SAVE_DOWNLOAD -> "save ${args.stringOr("filename", "?")} to Downloads"
            FETCH_URL -> "fetch ${args.stringOr("url", "?")}"
            else -> "$name ${arguments.take(120)}"
        }
    }

    suspend fun run(name: String, arguments: String, sessionId: String?): ToolOutcome {
        val args = parse(arguments)
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
        if (command.isBlank()) return ToolOutcome("run_shell needs a command.", failed = true)
        // Pinned to the workspace. The shell's own working directory is the
        // app's files root, which also holds the conversation database and the
        // encrypted credentials — not what the tool description promises, and
        // not somewhere the model has any business poking around.
        val result = shell.run(command, inDirectory = workspace())
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
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
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
     */
    private fun resolve(path: String): File? {
        val root = workspace().canonicalFile
        val target = if (path.isBlank()) root else File(root, path.trimStart('/')).canonicalFile
        return target.takeIf { it == root || it.path.startsWith(root.path + File.separator) }
    }

    private fun relative(file: File): String =
        file.path.removePrefix(workspace().canonicalFile.path).trimStart('/')

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

        private const val WORKSPACE = "workspace"
        private const val MAX_READ = 24_000
    }
}
