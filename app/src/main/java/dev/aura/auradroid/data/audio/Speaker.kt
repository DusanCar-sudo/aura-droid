package dev.aura.auradroid.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads replies aloud, using the engine already on the phone.
 *
 * Deliberately not a cloud voice: the desktop's TTS needs an API key that is
 * not set, and routing text out to a service and audio back would add a
 * round-trip, a cost, and a dependency on the desktop being reachable — for
 * something the platform does offline and instantly.
 */
@Singleton
class Speaker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private val _speakingId = MutableStateFlow<String?>(null)
    /** Id of the message being spoken, so the UI can show which one. */
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            _available.value = ready
            if (ready) {
                // The device language, not English: this user speaks Serbian,
                // and an English engine reading Serbian is unintelligible.
                val locale = Locale.getDefault()
                val supported = tts?.setLanguage(locale)
                if (supported == TextToSpeech.LANG_MISSING_DATA ||
                    supported == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speakingId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    if (_speakingId.value == utteranceId) _speakingId.value = null
                }

                @Deprecated("Required by the base class", ReplaceWith(""))
                override fun onError(utteranceId: String?) {
                    if (_speakingId.value == utteranceId) _speakingId.value = null
                }
            })
        }
    }

    /** Speak [text], replacing anything already being spoken. */
    fun speak(id: String, text: String) {
        if (!ready) return
        val spoken = strippedForSpeech(text)
        if (spoken.isBlank()) return
        tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
        _speakingId.value = id
    }

    fun stop() {
        tts?.stop()
        _speakingId.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        _available.value = false
        _speakingId.value = null
    }

    private companion object {
        /**
         * Assistant replies are markdown, and an engine reads it literally —
         * "star star note star star", every backtick, and whole code blocks
         * character by character. Strip the syntax and drop code entirely:
         * nobody wants a function read to them.
         */
        fun strippedForSpeech(raw: String): String = raw
            .replace(Regex("```[\\s\\S]*?```"), " (code omitted) ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("[*_]{1,3}([^*_]+)[*_]{1,3}"), "$1")
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*>\\s?", RegexOption.MULTILINE), "")
            .replace(Regex("\\|"), " ")
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }
}
