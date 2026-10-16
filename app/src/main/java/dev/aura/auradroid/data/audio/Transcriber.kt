package dev.aura.auradroid.data.audio

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.security.TokenVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a memo to a file and has the provider transcribe it.
 *
 * Online transcription (Whisper-style `/audio/transcriptions`) reads whole
 * sentences in context and handles long, rambling speech far better than the
 * on-device recogniser, which is why memos use it when a provider key is saved.
 * The audio leaves the phone only for that one request.
 */
@Singleton
class Transcriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: TokenVault,
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    /** The last recording, kept so a failed upload can be retried. */
    var lastAudio: File? = null
        private set

    private val client = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /** True when a provider key is saved, i.e. online transcription can run. */
    suspend fun available(): Boolean = vault.loadStandalone()?.apiKey?.isNotBlank() == true

    /** 0..1 loudness for the level meter. */
    fun level(): Float = try {
        ((recorder?.maxAmplitude ?: 0) / 12000f).coerceIn(0f, 1f)
    } catch (_: Exception) { 0f }

    fun start() {
        stopQuietly()
        val f = File(context.cacheDir, "memo-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16000)
        r.setAudioEncodingBitRate(32000)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
    }

    /** Stop recording and return the audio file, or null if nothing usable. */
    fun stop(): File? {
        val f = file
        try { recorder?.stop() } catch (_: Exception) { file?.delete(); file = null }
        stopQuietly()
        lastAudio = file ?: f?.takeIf { it.exists() }
        file = null
        return lastAudio?.takeIf { it.exists() && it.length() > 1500 }
    }

    fun discard() {
        stopQuietly()
        file?.delete(); file = null
    }

    private fun stopQuietly() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    /** Result is the text, or a failure with a message fit to show the user. */
    suspend fun transcribe(audio: File): Result<String> = withContext(Dispatchers.IO) {
        val creds = vault.loadStandalone()
            ?: return@withContext Result.failure(IllegalStateException("No provider key saved"))
        val base = creds.baseUrl.trim().trimEnd('/')
        val model = if (base.contains("groq")) "whisper-large-v3-turbo" else "whisper-1"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("$base/audio/transcriptions")
            .header("Authorization", "Bearer ${creds.apiKey}")
            .post(body)
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Transcription failed (${resp.code}). This provider may not offer speech-to-text."),
                    )
                }
                val out = com.google.gson.JsonParser.parseString(text).asJsonObject.get("text")?.asString.orEmpty().trim()
                if (out.isEmpty()) Result.failure(IllegalStateException("Nothing was heard"))
                else Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not reach the provider: ${e.message}"))
        }
    }
}
