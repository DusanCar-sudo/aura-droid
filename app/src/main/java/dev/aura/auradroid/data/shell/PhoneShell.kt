package dev.aura.auradroid.data.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method
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
 *
 * If elevated, uses Shizuku (if installed and authorized) to run as the adb user
 * (uid 2000), unlocking pm, settings, dumpsys, and full /sdcard access.
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
        // The app's own private state. Never legitimate for the agent or the
        // user to touch from a shell: the conversation database and the
        // encrypted pairing token are the two things this app protects.
        Regex("""aura_database""", RegexOption.IGNORE_CASE),
        Regex("""aura_pairing""", RegexOption.IGNORE_CASE),
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
    suspend fun run(command: String, inDirectory: File? = null, elevated: Boolean = false): ShellResult =
        withContext(Dispatchers.IO) {
        val cmd = command.trim()
        val here = inDirectory ?: cwd
        if (cmd.isEmpty()) return@withContext ShellResult("", 0, here)

        refused.firstOrNull { it.containsMatchIn(cmd) }?.let {
            return@withContext when {
                it.pattern.contains("aura_database") || it.pattern.contains("aura_pairing") ->
                    ShellResult(
                        "refused: this touches the app's private data and is never allowed.",
                        126, here,
                    )
                else -> ShellResult(
                    "refused: this would destroy data and cannot be undone from here.",
                    126, here,
                )
            }
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
                !elevated && !dest.exists() -> ShellResult("cd: ${dest.path}: no such directory", 1, here)
                !elevated && !dest.isDirectory -> ShellResult("cd: ${dest.path}: not a directory", 1, here)
                !elevated && !dest.canRead() -> ShellResult("cd: ${dest.path}: permission denied", 1, here)
                // A one-off directory is for the life of the command; moving the
                // terminal's position from under it would be a surprise.
                inDirectory != null -> ShellResult("", 0, dest.canonicalFile)
                else -> { cwd = dest.canonicalFile; ShellResult("", 0, cwd) }
            }
        }

        try {
            val process: java.lang.Process = if (elevated) {
                if (!Shizuku.pingBinder()) {
                    return@withContext ShellResult("Shizuku is not running on this device. Start it via adb or wireless debugging.", 1, here)
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    return@withContext ShellResult("Shizuku permission not granted to this app. Please grant it in Shizuku or Settings.", 1, here)
                }
                val runDir = when {
                    // The adb user (uid 2000) cannot access the app's private sandbox directory (/data/user/0/<pkg>/...).
                    // Fall back to /sdcard or /data/local/tmp for elevated commands.
                    here.absolutePath.startsWith(context.applicationInfo.dataDir) -> {
                        File("/sdcard").takeIf { it.exists() && it.canRead() } ?: File("/data/local/tmp")
                    }
                    else -> here
                }
                try {
                    spawnShizukuProcess(cmd, runDir)
                } catch (e: Exception) {
                    Log.e("PhoneShell", "spawnShizukuProcess failed", e)
                    return@withContext ShellResult("Shizuku execution failed: ${e.message}", 1, here)
                }
            } else {
                ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .directory(here)
                    .redirectErrorStream(true)
                    .start()
            }
            // Bounded: a runaway `find /` would otherwise fill memory with
            // output nobody is going to read on a phone screen.
            // Stop cancels the coroutine but a blocking read ignores that; kill
            // the process so the read hits EOF and nothing keeps running.
            val job = currentCoroutineContext().job
            val handle = job.invokeOnCompletion { if (it != null) process.destroyForcibly() }
            try {
                val outDeferred = async { readBoundedStream(process.inputStream, 200_000) }
                val errDeferred = async { readBoundedStream(process.errorStream, 50_000) }
                val stdout = outDeferred.await()
                val stderr = errDeferred.await()
                val code = process.waitFor()
                val output = when {
                    stderr.isBlank() -> stdout
                    stdout.isBlank() -> stderr
                    else -> "${stdout.trimEnd()}\n${stderr.trimEnd()}"
                }
                ShellResult(output, code, here)
            } finally {
                handle.dispose()
                if (process.isAlive) process.destroyForcibly()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PhoneShell", "Execution failed", e)
            ShellResult("failed: ${e.message}", 1, here)
        }
    }

    private fun readBoundedStream(stream: java.io.InputStream, maxChars: Int): String {
        val sb = StringBuilder()
        stream.bufferedReader().use { r ->
            val buf = CharArray(8192)
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                if (sb.length < maxChars) sb.append(buf, 0, minOf(n, maxChars - sb.length))
            }
        }
        return sb.toString()
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

    private fun spawnShizukuProcess(command: String, here: File): java.lang.Process {
        val shellCmd = arrayOf("/system/bin/sh", "-c", "exec 2>&1; $command")
        val dirPath = here.absolutePath
        Log.d("PhoneShell", "spawnShizukuProcess: cmd=$command dir=$dirPath")
        // 1. Try cached Shizuku.newProcess(cmd, env, dir)
        shizukuNewProcessMethod?.let { m ->
            try {
                return m.invoke(null, shellCmd, null, dirPath) as java.lang.Process
            } catch (t: Throwable) {
                Log.w("PhoneShell", "shizukuNewProcessMethod reflection failed, trying service direct", t)
            }
        }

        // 2. Fall back to calling IShizukuService directly and wrapping in ShizukuRemoteProcess
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder is not available")
        val service = IShizukuService.Stub.asInterface(binder)
            ?: throw IllegalStateException("Cannot obtain IShizukuService interface")
        val remoteProcess = service.newProcess(shellCmd, null, dirPath)
            ?: throw IllegalStateException("IShizukuService.newProcess returned null")

        val ctor = shizukuRemoteProcessConstructor
            ?: throw IllegalStateException("ShizukuRemoteProcess constructor is not available")
        return ctor.newInstance(remoteProcess) as java.lang.Process
    }

    companion object {
        private val shizukuNewProcessMethod: Method? by lazy {
            try {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                ).apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
        }

        private val shizukuRemoteProcessConstructor: Constructor<*>? by lazy {
            try {
                ShizukuRemoteProcess::class.java.getDeclaredConstructor(
                    IRemoteProcess::class.java,
                ).apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
