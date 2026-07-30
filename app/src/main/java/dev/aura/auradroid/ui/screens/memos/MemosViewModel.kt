package dev.aura.auradroid.ui.screens.memos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.audio.ListenState
import dev.aura.auradroid.data.audio.Listener
import dev.aura.auradroid.data.model.Memo
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.repository.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemosViewModel @Inject constructor(
    private val repository: AuraRepository,
    private val http: dev.aura.auradroid.data.network.AuraHttp,
    private val vault: dev.aura.auradroid.data.security.TokenVault,
    val listener: Listener,
) : ViewModel() {

    init {
        syncPending()
    }

    /**
     * Push memos the desktop has not seen into its episodic memory.
     *
     * Best effort and silent: a memo is worth keeping whether or not a desktop
     * was in reach when it was spoken, so a failure here leaves it queued
     * rather than surfacing an error about something the user cannot fix from
     * a train.
     */
    fun syncPending() {
        viewModelScope.launch {
            val endpoint = vault.load() ?: return@launch
            val pending = repository.getMemos().first().filter { !it.synced }
            for (memo in pending) {
                val ok = http.pushMemo(
                    endpoint = endpoint,
                    id = memo.id,
                    title = memo.title,
                    text = memo.text,
                    atIso = java.time.Instant.ofEpochMilli(memo.createdAt).toString(),
                )
                if (ok) repository.markMemoSynced(memo.id)
            }
        }
    }

    val memos: StateFlow<List<Memo>> = repository.getMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transcript: StateFlow<String> = listener.transcript
    val listenState: StateFlow<ListenState> = listener.state
    val level: StateFlow<Float> = listener.level

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private var startedAt = 0L

    fun startRecording() {
        startedAt = System.currentTimeMillis()
        _recording.value = true
        // Continuous: a memo is someone thinking aloud, and the recogniser
        // would otherwise stop at the first pause.
        listener.start(continuousMode = true)
    }

    /** Stop and keep it. Returns silently if nothing was heard. */
    fun stopAndSave() {
        listener.stop()
        _recording.value = false
        val text = transcript.value.trim()
        val duration = System.currentTimeMillis() - startedAt
        if (text.isEmpty()) {
            listener.reset()
            return
        }
        viewModelScope.launch {
            repository.saveMemo(text, duration)
            listener.reset()
            // Straight into Aura's memory if the desktop is reachable, so a
            // thought is searchable the moment it is finished.
            syncPending()
        }
    }

    fun cancelRecording() {
        listener.reset()
        _recording.value = false
    }

    fun exportMemos(
        context: android.content.Context,
        format: dev.aura.auradroid.data.export.ChatExporter.Format,
    ) {
        viewModelScope.launch {
            val all = repository.getMemos().first()
            if (all.isEmpty()) return@launch
            dev.aura.auradroid.data.export.Sharing.shareText(
                context = context,
                fileName = "aura-memos." + if (format == dev.aura.auradroid.data.export.ChatExporter.Format.MARKDOWN) "md" else "json",
                content = dev.aura.auradroid.data.export.ChatExporter.exportMemos(all, format),
                subject = "Aura memos",
            )
        }
    }

    fun updateMemo(id: String, text: String) {
        viewModelScope.launch { repository.updateMemoText(id, text) }
    }

    fun deleteMemo(id: String) {
        viewModelScope.launch { repository.deleteMemo(id) }
    }

    /**
     * Turn a memo into a conversation.
     *
     * The memo is spoken, so it rambles and lacks the shape of an instruction.
     * Wrapping it tells the agent to treat it as a brief to be worked out
     * rather than a command to execute literally — and asking for the plan
     * first means a vague memo produces questions, not a wrong guess acted on.
     */
    fun startProject(memo: Memo, onStarted: (sessionId: String, task: String) -> Unit) {
        viewModelScope.launch {
            val session = repository.createSession(
                title = memo.title,
                model = "",
                provider = "",
                mode = SessionMode.ARCHITECT,
            )
            repository.linkMemoToSession(memo.id, session.id)
            val task = buildString {
                append("This is a spoken note I recorded. Work out what it is asking for, ")
                append("then tell me the plan before you change anything. ")
                append("If it is ambiguous, ask rather than guess.\n\n---\n\n")
                append(memo.text)
            }
            onStarted(session.id, task)
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener.stop()
    }
}
