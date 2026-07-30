package dev.aura.auradroid.data.shell

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val output: String, val exitCode: Int, val cwd: File)

/**
 * A shell on the phone, inside the app's own sandbox.
 *
 * Worth being honest about the ceiling: Android gives an unrooted app its own
 * directory and the read-only system image, and nothing else. /data/data of
 * other apps, /sdcard without permission, and anything needing root are all
 * closed. What is here is toybox — ls, cat, grep, ps, df — over the app's own
 * files, plus whatever the user writes there.
 */
@Singleton
class PhoneShell @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Working directory, persisted across commands so `cd` means something. */
    var cwd: File = context.filesDir
        private set

    fun home(): File = context.filesDir

    /**
     * Commands refused outright.
     *
     * A terminal the user typed into does not get an approval prompt for every
     * line — that would make it unusable. But a phone keyboard makes a
     * mistyped recursive delete easy, and the app's own directory holds the
     * conversation database and the encrypted pairing token. These are the
     * ones with no plausible intent behind them from a phone.
     */
    private val refused = listOf(
        Regex("""\brm\s+(-[a-z]*[rf][a-z]*|--(recursive|force|no-preserve-root))\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmkfs\b"""),
        Regex("""\bdd\s+if="""),
        Regex(""":\(\)\s*\{"""),
        Regex("""\|\s*(ba)?sh\b"""),
        Regex("""\b(curl|wget)\b.*\|\s*(ba)?sh""", RegexOption.IGNORE_CASE),
        Regex("""\bpm\s+(uninstall|clear)\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * Run one command.
     *
     * [inDirectory] overrides the persisted working directory for this call
     * only, without moving it. The agent's shell tool uses it to stay inside
     * the folder it tells the model it is working in — the terminal the user
     * types into keeps its own `cd` position, and the two should not drag each
     * other around.
     */
    suspend fun run(command: String, inDirectory: File? = null): ShellResult =
        withContext(Dispatchers.IO) {
        val cmd = command.trim()
        val here = inDirectory ?: cwd
        if (cmd.isEmpty()) return@withContext ShellResult("", 0, here)

        refused.firstOrNull { it.containsMatchIn(cmd) }?.let {
            return@withContext ShellResult(
                "refused: this would destroy data and cannot be undone from here.",
                126, here,
            )
        }

        // cd is handled here, not by the shell: each command is its own
        // process, so a `cd` inside one would be forgotten immediately.
        if (cmd == "cd" || cmd.startsWith("cd ")) {
            val target = cmd.removePrefix("cd").trim()
            val dest = when {
                target.isEmpty() || target == "~" -> home()
                target.startsWith("/") -> File(target)
                else -> File(here, target)
            }
            return@withContext when {
                !dest.exists() -> ShellResult("cd: ${dest.path}: no such directory", 1, here)
                !dest.isDirectory -> ShellResult("cd: ${dest.path}: not a directory", 1, here)
                !dest.canRead() -> ShellResult("cd: ${dest.path}: permission denied", 1, here)
                // A one-off directory is for the life of the command; moving the
                // terminal's position from under it would be a surprise.
                inDirectory != null -> ShellResult("", 0, dest.canonicalFile)
                else -> { cwd = dest.canonicalFile; ShellResult("", 0, cwd) }
            }
        }

        try {
            val process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .directory(here)
                .redirectErrorStream(true)   // stderr matters as much as stdout here
                .start()
            // Bounded: a runaway `find /` would otherwise fill memory with
            // output nobody is going to read on a phone screen.
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            ShellResult(text.take(200_000), code, here)
        } catch (e: Exception) {
            ShellResult("failed: ${e.message}", 1, here)
        }
    }

    /** Shown as the prompt; the full path is long and mostly noise. */
    fun prompt(): String {
        val h = home().path
        return when {
            cwd.path == h -> "~"
            cwd.path.startsWith("$h/") -> "~/" + cwd.path.removePrefix("$h/")
            else -> cwd.path
        }
    }
}
