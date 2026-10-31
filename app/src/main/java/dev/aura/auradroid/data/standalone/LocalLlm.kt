package dev.aura.auradroid.data.standalone

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a model on the phone itself. Nothing leaves the device.
 *
 * The model is any MediaPipe-format file (.task / .litertlm / .bin) placed in
 * the app's `models/` folder. Plain chat only: these small models cannot drive
 * the tool loop reliably, so no tools are offered to them.
 */
@Singleton
class LocalLlm @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object { const val BASE = "local" }

    val dir: File get() = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }

    fun models(): List<File> =
        dir.listFiles { f -> f.extension in setOf("task", "litertlm", "bin") }?.sortedBy { it.name }.orEmpty()

    private var engine: LlmInference? = null
    private var loaded: String? = null

    private fun engineFor(file: File): LlmInference {
        if (loaded == file.absolutePath) engine?.let { return it }
        engine?.close()
        val opts = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(1024)
            .build()
        return LlmInference.createFromOptions(context, opts).also { engine = it; loaded = file.absolutePath }
    }

    fun stream(model: String, system: String, history: List<Turn>): Flow<Chunk> = callbackFlow {
        val file = models().firstOrNull { it.name == model } ?: models().firstOrNull()
        if (file == null) {
            trySend(Chunk.Failed("No local model found. Put a .task or .litertlm file in ${dir.path}"))
            close(); return@callbackFlow
        }
        try {
            val llm = engineFor(file)
            val prompt = buildString {
                if (system.isNotBlank()) append(system).append("\n\n")
                history.forEach { t ->
                    val c = t.content ?: return@forEach
                    append(if (t.role == "assistant") "Aura: " else "User: ").append(c).append("\n")
                }
                append("Aura:")
            }
            llm.generateResponseAsync(prompt) { part, done ->
                if (part.isNotEmpty()) trySend(Chunk.Text(part))
                if (done) { trySend(Chunk.Done); close() }
            }
        } catch (e: Throwable) {
            trySend(Chunk.Failed("Local model failed: ${e.message}"))
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.Default)
}
