package dev.aura.auradroid.data.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the microphone is currently doing. */
sealed interface ListenState {
    data object Idle : ListenState
    data object Listening : ListenState
    data class Error(val message: String) : ListenState
}

/**
 * Speech to text, on the device.
 *
 * The awkward part is that SpeechRecognizer is built for single commands: it
 * decides you have finished after a second or two of silence and stops. Left
 * alone that truncates a memo at the first pause for thought, which is exactly
 * when someone is deciding what to say next.
 *
 * So in [continuous] mode a finished segment is appended to the transcript and
 * the recogniser is started again immediately, until the caller says stop.
 * Segments accumulate rather than replacing each other, which is the whole
 * difference between dictating a sentence and recording a memo.
 */
@Singleton
class Listener @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var stopping = false
    /** Cleared once the service says it has no model for this locale. */
    private var useDeviceLanguage = true

    /** Text confirmed so far this session. */
    private val committed = StringBuilder()

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    /** Committed text plus whatever is being recognised right now. */
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    /** Loudness 0..1, for a level meter that shows the mic is live. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) || preferredRecognizer() != null

    /**
     * Start listening. [continuousMode] keeps going across pauses, for memos;
     * without it the recogniser stops at the first natural break, which is
     * what you want when dictating a single message.
     */
    fun start(continuousMode: Boolean, initialText: String = "") {
        if (!isAvailable()) {
            _state.value = ListenState.Error(
                "This phone has no speech recogniser. Google app may be disabled.",
            )
            return
        }
        continuous = continuousMode
        stopping = false
        committed.setLength(0)
        if (initialText.isNotBlank()) committed.append(initialText.trim()).append(' ')
        _transcript.value = committed.toString()
        beginListening()
    }

    fun stop() {
        stopping = true
        continuous = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        _level.value = 0f
        _state.value = ListenState.Idle
    }

    /** Drop everything and reset, e.g. when the user cancels a memo. */
    fun reset() {
        stop()
        committed.setLength(0)
        _transcript.value = ""
    }

    /**
     * One recogniser for the whole session, restarted rather than rebuilt.
     *
     * The first version destroyed and recreated it per segment, from inside
     * its own result callback — which tears down the service binding while it
     * is delivering, and the next startListening lands on a dead connection
     * ("not connected to the recognition service"). Reusing the instance and
     * posting the restart to the main looper avoids both.
     */
    private fun ensureRecognizer() {
        if (recognizer != null) return
        val component = preferredRecognizer()
        recognizer = (
            if (component != null) {
                SpeechRecognizer.createSpeechRecognizer(context, component)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            ).apply { setRecognitionListener(Callbacks()) }
    }

    /**
     * Pick a recognition service explicitly rather than taking the system
     * default.
     *
     * The default is whatever is set as the device's voice assistant, and not
     * every such service accepts third-party callers — a phone with a
     * non-Google assistant installed reports recognition as "available", then
     * refuses to bind, and the only symptom is "not connected to the
     * recognition service" with nothing to say why. Found on a device whose
     * default had been changed to another assistant app.
     *
     * Google's is preferred because it is the one with broad language
     * coverage; failing that, any installed service; failing that, null, and
     * the platform default is used.
     */
    private fun preferredRecognizer(): android.content.ComponentName? {
        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            0,
        )
        if (services.isEmpty()) return null
        val chosen = services.firstOrNull {
            it.serviceInfo.packageName == "com.google.android.googlequicksearchbox"
        } ?: services.firstOrNull {
            // Anything shipped with the system is a safer bet than a
            // third-party assistant that may gate its service.
            it.serviceInfo.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        } ?: services.first()
        return android.content.ComponentName(
            chosen.serviceInfo.packageName,
            chosen.serviceInfo.name,
        )
    }

    private fun beginListening() {
        ensureRecognizer()
        recognizer?.startListening(intent())
        _state.value = ListenState.Listening
    }

    /** Restart on the main looper — never synchronously inside a callback. */
    private fun restartListening() {
        if (stopping || !continuous) return
        main.post {
            if (stopping || !continuous) return@post
            recognizer?.cancel()
            recognizer?.startListening(intent())
        }
    }

    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // The device language, so Serbian is transcribed as Serbian — unless
            // no model exists for it, in which case [useDeviceLanguage] is
            // cleared and the service falls back to whatever it does have.
            if (useDeviceLanguage) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Give the speaker room to think mid-sentence before the recogniser
            // decides they have finished.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

    private fun commit(segment: String) {
        val clean = segment.trim()
        if (clean.isEmpty()) return
        if (committed.isNotEmpty() && !committed.endsWith(" ")) committed.append(' ')
        committed.append(clean)
        _transcript.value = committed.toString()
    }

    private inner class Callbacks : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = ListenState.Listening
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Roughly -2..10 dB in practice; normalised for a meter.
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            // Shown but not committed: the recogniser revises partials freely,
            // so appending them would produce stuttered, duplicated text.
            _transcript.value = (committed.toString() + " " + partial).trim()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            commit(text)
            if (continuous && !stopping) restartListening() else stop()
        }

        override fun onError(error: Int) {
            // No model for the device locale: drop the request and let the
            // service choose, rather than failing outright on a phone set to a
            // language Google does not transcribe.
            if (useDeviceLanguage && (error == 12 || error == 13)) {
                useDeviceLanguage = false
                restartListening()
                return
            }
            // Silence is not a failure in continuous mode — it is a pause. Keep
            // going, or a memo would end the moment the speaker drew breath.
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (continuous && !stopping && recoverable) {
                restartListening()
                return
            }
            if (stopping) return
            _state.value = ListenState.Error(describe(error))
            stop()
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onEndOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition stopped — is another app using the microphone?"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection."
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing recognised."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy — try again."
        SpeechRecognizer.ERROR_SERVER -> "Speech service error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard."
        else -> "Speech recognition failed ($error)."
    }
}
