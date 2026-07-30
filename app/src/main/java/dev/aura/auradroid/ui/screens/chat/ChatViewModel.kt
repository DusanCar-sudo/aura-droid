package dev.aura.auradroid.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aura.auradroid.data.agent.AgentEvent
import dev.aura.auradroid.data.agent.AgentLoop
import dev.aura.auradroid.data.agent.Persona
import dev.aura.auradroid.data.attach.Attachment
import dev.aura.auradroid.data.attach.AttachmentKind
import dev.aura.auradroid.data.attach.Attachments
import dev.aura.auradroid.data.audio.ListenState
import dev.aura.auradroid.data.audio.Listener
import dev.aura.auradroid.data.audio.Speaker
import dev.aura.auradroid.data.memory.AgentMemory
import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.model.Session
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.network.AuraHttp
import dev.aura.auradroid.data.network.AuraSocket
import dev.aura.auradroid.data.network.ConnState
import dev.aura.auradroid.data.network.ModelChoice
import dev.aura.auradroid.data.repository.AuraRepository
import dev.aura.auradroid.data.repository.ContextSnapshot
import dev.aura.auradroid.data.repository.EventSink
import dev.aura.auradroid.data.repository.PendingConfirm
import dev.aura.auradroid.data.repository.ToolPayload
import dev.aura.auradroid.data.security.TokenVault
import dev.aura.auradroid.data.session.SessionNamer
import dev.aura.auradroid.data.standalone.Turn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AuraRepository,
    private val socket: AuraSocket,
    private val http: AuraHttp,
    private val vault: TokenVault,
    private val gson: Gson,
    val listener: Listener,
    val speaker: Speaker,
    private val agent: AgentLoop,
    private val memory: AgentMemory,
) : ViewModel() {

    private val sink = EventSink(repository, gson)

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /** Files and photos staged for the next message. */
    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    /** Set when the phone talks to a model itself, with no desktop. */
    private val _thinkingLocal = MutableStateFlow(false)

    private val _standalone = MutableStateFlow<TokenVault.Standalone?>(null)
    val standalone: StateFlow<TokenVault.Standalone?> = _standalone.asStateFlow()

    private val _dictating = MutableStateFlow(false)
    val dictating: StateFlow<Boolean> = _dictating.asStateFlow()

    /** Loudness while the mic is live, so holding the button shows something. */
    val micLevel: StateFlow<Float> get() = listener.level

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val speakingId: StateFlow<String?> get() = speaker.speakingId

    private val _currentMode = MutableStateFlow(SessionMode.CODER)
    val currentMode: StateFlow<SessionMode> = _currentMode.asStateFlow()

    private val _projectName = MutableStateFlow<String?>(null)
    val projectName: StateFlow<String?> = _projectName.asStateFlow()

    private val _models = MutableStateFlow<List<ModelChoice>>(emptyList())
    val models: StateFlow<List<ModelChoice>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    /** Null until we know; true means the pairing screen should take over. */
    private val _needsPairing = MutableStateFlow<Boolean?>(null)
    val needsPairing: StateFlow<Boolean?> = _needsPairing.asStateFlow()

    /**
     * Approval for a tool the phone is about to run itself.
     *
     * The desktop path has its own, arriving over the socket; this is the local
     * equivalent, and both feed the one sheet so the user sees a single kind of
     * prompt whichever mode they are in.
     */
    private val _localConfirm = MutableStateFlow<PendingConfirm?>(null)
    private var awaitingApproval: CompletableDeferred<Boolean>? = null

    // Straight through from the socket and the sink.
    val connection: StateFlow<ConnState> = socket.state
    val thinking: StateFlow<Boolean> =
        combine(sink.thinking, _thinkingLocal) { a, b -> a || b }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val pendingConfirm: StateFlow<PendingConfirm?> =
        combine(sink.pendingConfirm, _localConfirm) { remote, local -> local ?: remote }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val contextHealth: StateFlow<ContextSnapshot?> = sink.contextHealth

    private var messageCollectJob: Job? = null
    private var replyJob: Job? = null
    private var configured = false

    /** True between press and release of the microphone button. */
    private var holdingToTalk = false
    private var observingDictation = false

    init {
        configure()
        // Photos are read into the message and the file has no further use.
        Attachments.sweepCameraCache(appContext)
    }

    /**
     * Re-read which mode applies and set the screen up for it.
     *
     * Called again when the chat screen resumes, because the mode is changed in
     * Settings while this ViewModel is alive on the back stack — doing it only
     * in init meant the switch appeared to do nothing until the app was
     * restarted, which is exactly what it looked like.
     */
    fun configure() {
        viewModelScope.launch {
            val endpoint = vault.load()
            val standalone = vault.loadStandalone()

            // Nothing changed since the last check — leave the live socket and
            // the loaded conversation alone rather than tearing them down.
            if (configured && standalone?.model == _standalone.value?.model &&
                (standalone == null) == (_standalone.value == null)
            ) return@launch

            configured = true
            _standalone.value = standalone
            if (standalone != null) socket.disconnect()

            if (endpoint == null && standalone == null) {
                _needsPairing.value = true
                return@launch
            }
            _needsPairing.value = false

            observeDictation()
            speaker.init()

            // Standalone wins when it is on, whether or not a desktop is also
            // paired. Enabling it is a deliberate act, and having sending go
            // one way while the status bar reports the other is worse than
            // either choice.
            if (standalone != null) {
                val local = repository.getActiveSessions().first()
                if (local.isEmpty()) createNewSession() else loadSession(local.first().id)
                _projectName.value = "on this phone"
                _selectedModel.value = standalone.model
                return@launch
            }

            // Past the standalone branch, a desktop must exist.
            val paired = endpoint ?: return@launch

            // Sessions on the phone are local views; the desktop keeps exactly
            // one. See loadSession for what that means for switching.
            val sessions = repository.getActiveSessions().first()
            if (sessions.isEmpty()) createNewSession() else loadSession(sessions.first().id)

            socket.connect(paired, viewModelScope)
            observeEvents()
            observeConnection()

            http.fetchProject(paired)?.let { project ->
                _projectName.value = project.name
                _models.value = project.models
                // The desktop's configured model is the right default — the
                // phone holds no provider credentials and cannot know better.
                _selectedModel.value = project.model.takeIf { it.isNotBlank() }
            }
        }
    }

    /** While dictating, what is heard becomes the message being composed. */
    private fun observeDictation() {
        // configure() runs again whenever the screen resumes or the mode
        // changes, and each pass used to start another pair of collectors on
        // the same two flows.
        if (observingDictation) return
        observingDictation = true

        viewModelScope.launch {
            listener.transcript.collect { text ->
                if (_dictating.value) _inputText.value = text
            }
        }
        viewModelScope.launch {
            listener.state.collect { st ->
                // Hold-to-talk owns the button while the finger is down. Without
                // this guard the recogniser's own end-of-utterance would clear
                // the flag mid-sentence and the meter would vanish while the
                // user was still speaking.
                if (st !is ListenState.Listening && !holdingToTalk) _dictating.value = false
                if (st is ListenState.Error) {
                    _notice.value = st.message
                    holdingToTalk = false
                    _dictating.value = false
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            socket.events.collect { event ->
                val sessionId = _currentSession.value?.id ?: return@collect
                sink.handle(sessionId, event)
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            socket.state.collect { state ->
                // A drop mid-stream would otherwise leave a message stuck
                // rendering as if it were still arriving.
                if (state is ConnState.Reconnecting || state is ConnState.Failed) {
                    sink.onDisconnected()
                }
            }
        }
    }

    fun loadSession(sessionId: String) {
        messageCollectJob?.cancel()
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId) ?: return@launch
            _currentSession.value = session
            _currentMode.value = session.mode
        }
        messageCollectJob = viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collect { list ->
                _messages.value = list.map { it.toMessageItem() }
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = repository.createSession(
                title = SessionNamer.UNTITLED,
                model = _selectedModel.value ?: "",
                provider = "",
                mode = _currentMode.value,
            )
            loadSession(session.id)
            // The desktop holds one in-memory session, so starting a fresh
            // local conversation has to clear its history too — otherwise the
            // agent answers with context the user can no longer see.
            socket.sendReset()
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ── Attachments ─────────────────────────────────────────────────────────

    /**
     * Stage a picked file or a photo just taken.
     *
     * Read here rather than at send time so the user finds out immediately that
     * a 40 MB video cannot be attached, instead of after typing a message.
     */
    fun attach(uri: Uri) {
        viewModelScope.launch {
            val attachment = Attachments.read(appContext, uri)
            if (attachment == null) {
                _notice.value = "That file could not be read."
                return@launch
            }
            if (attachment.kind == AttachmentKind.BINARY) {
                _notice.value =
                    "${attachment.name} is not text or an image, so the model cannot read it."
                return@launch
            }
            _attachments.value = (_attachments.value + attachment).takeLast(MAX_ATTACHMENTS)
        }
    }

    fun removeAttachment(id: String) {
        _attachments.value = _attachments.value.filterNot { it.id == id }
    }

    // ── Dictation ───────────────────────────────────────────────────────────

    /**
     * Press and hold to talk, release to stop.
     *
     * Continuous mode while held, because the recogniser otherwise decides the
     * utterance ended after a second of silence — which on a phone is someone
     * pausing to think, not someone finishing.
     */
    fun startHoldDictation() {
        if (holdingToTalk) return
        _notice.value = null
        holdingToTalk = true
        _dictating.value = true
        listener.start(continuousMode = true, initialText = _inputText.value)
    }

    fun stopHoldDictation() {
        if (!holdingToTalk) return
        holdingToTalk = false
        listener.stop()
        _dictating.value = false
        // The last partial has not been committed when the finger comes up, so
        // take the transcript as it stands rather than losing the final words.
        listener.transcript.value.takeIf { it.isNotBlank() }?.let { _inputText.value = it.trim() }
    }

    fun speak(messageId: String, text: String) {
        if (speaker.speakingId.value == messageId) speaker.stop() else speaker.speak(messageId, text)
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    /**
     * Send text the user did not type — currently a memo turned into a brief.
     *
     * Waits for the socket rather than dropping it: the memo screen navigates
     * straight here, so the connection is often still coming up, and silently
     * losing what someone just recorded is the worst possible outcome.
     */
    fun sendTaskText(task: String) {
        val sessionId = _currentSession.value?.id ?: return

        val local = _standalone.value
        if (local != null) {
            // No socket to wait on in standalone; it is one HTTP call.
            _inputText.value = task
            sendMessage()
            return
        }

        viewModelScope.launch {
            repository.addMessage(sessionId, MessageRole.USER, task)
            titleSessionFrom(task)
            var waited = 0L
            while (connection.value !is ConnState.Connected && waited < 15_000) {
                kotlinx.coroutines.delay(250)
                waited += 250
            }
            if (connection.value !is ConnState.Connected) {
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "Not connected — the memo was saved but not sent.",
                    errorMessage = "send failed",
                )
                return@launch
            }
            socket.sendTask(task, _selectedModel.value)
        }
    }

    fun sendMessage() {
        if (holdingToTalk) stopHoldDictation()
        if (_dictating.value) {
            listener.stop()
            _dictating.value = false
        }

        val text = _inputText.value.trim()
        val staged = _attachments.value
        // An attachment on its own is a message: "what is this?" is implied by
        // photographing something and pressing send.
        if (text.isEmpty() && staged.isEmpty()) return

        val local = _standalone.value
        if (local != null) {
            sendStandalone(text, staged, local)
            return
        }

        if (connection.value !is ConnState.Connected) return
        val sessionId = _currentSession.value?.id ?: return

        viewModelScope.launch {
            // The desktop protocol carries a task string, so an attachment goes
            // as its text. Images cannot cross that path at all, and saying so
            // beats sending the words and dropping the picture in silence.
            val body = withAttachments(text, staged, imagesAsText = true)
            repository.addMessage(sessionId, MessageRole.USER, describeForTranscript(text, staged))
            titleSessionFrom(text.ifBlank { staged.firstOrNull()?.name.orEmpty() })
            _inputText.value = ""
            _attachments.value = emptyList()

            val sent = socket.sendTask(body, _selectedModel.value)
            if (!sent) {
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "Not connected — message not sent.",
                    errorMessage = "send failed",
                )
            }
        }
    }

    /** Stop a reply in progress. */
    fun stopReply() {
        replyJob?.cancel()
        replyJob = null
        _thinkingLocal.value = false
    }

    /** Answer the agent's approval prompt. It is blocked until this lands. */
    fun respondToConfirm(id: String, approved: Boolean) {
        if (id == LOCAL_CONFIRM_ID) {
            _localConfirm.value = null
            awaitingApproval?.complete(approved)
            awaitingApproval = null
            return
        }
        socket.sendConfirm(id, approved)
        sink.clearPendingConfirm()
    }

    fun selectModel(modelId: String) {
        _selectedModel.value = modelId
    }

    /**
     * Ask the model directly, with no desktop in the path.
     *
     * The whole conversation is resent each turn because the provider is
     * stateless — there is no session on the other end to remember it, unlike
     * the desktop which holds one.
     */
    private fun sendStandalone(
        text: String,
        staged: List<Attachment>,
        creds: TokenVault.Standalone,
    ) {
        val sessionId = _currentSession.value?.id ?: return

        // Built from what is on screen *now*, before the new row is inserted.
        // Reading the flow back after inserting raced the database: the collector
        // had often not emitted yet, and the model was answering the previous
        // question with the new one missing entirely.
        val history = _messages.value
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filter { it.content.isNotBlank() }
            .map {
                Turn(
                    role = if (it.role == MessageRole.USER) "user" else "assistant",
                    content = it.content,
                )
            }

        val prompt = withAttachments(text, staged, imagesAsText = false)
        val transcript = describeForTranscript(text, staged)
        val images = staged.mapNotNull { it.base64 }

        _inputText.value = ""
        _attachments.value = emptyList()

        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            repository.addMessage(sessionId, MessageRole.USER, transcript)
            titleSessionFrom(text.ifBlank { staged.firstOrNull()?.name.orEmpty() })
            _thinkingLocal.value = true

            // Images ride only on the turn they were attached to. Keeping the
            // base64 in the database and resending it every turn would grow the
            // request without bound for a picture the model already described.
            val turns = history + Turn(role = "user", content = prompt, images = images)

            runAgent(sessionId, creds, turns)
        }
    }

    /**
     * Drive the agent loop and write what it does into the conversation.
     *
     * Split out from [sendStandalone] because the interesting part is the
     * cleanup: whatever happens — finished, failed, or stopped by the user
     * mid-sentence — the transcript has to be left in a state that is true.
     */
    private suspend fun runAgent(
        sessionId: String,
        creds: TokenVault.Standalone,
        turns: List<Turn>,
    ) {
        var streamId: Long? = null
        val buffer = StringBuilder()
        var lastFlush = 0L
        val toolRows = mutableMapOf<String, Long>()

        suspend fun finishText() {
            streamId?.let { repository.updateStreamingMessage(it, buffer.toString(), false) }
            streamId = null
            buffer.setLength(0)
        }

        try {
            val system = Persona.systemPrompt(
                mode = _currentMode.value,
                memoryBlock = memory.promptBlock(),
                toolsEnabled = true,
            )

            agent.run(
                apiKey = creds.apiKey,
                baseUrl = creds.baseUrl,
                model = creds.model,
                system = system,
                history = turns,
                sessionId = sessionId,
                approve = ::askApproval,
            ).collect { event ->
                when (event) {
                    is AgentEvent.Text -> {
                        _thinkingLocal.value = false
                        buffer.append(event.text)
                        val id = streamId
                        if (id == null) {
                            streamId = repository.addMessage(
                                sessionId, MessageRole.ASSISTANT, buffer.toString(),
                                isStreaming = true,
                            ).id
                            lastFlush = System.currentTimeMillis()
                        } else {
                            // Same ~10/sec budget as the desktop path: a write
                            // per token is hundreds of round-trips per reply.
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= FLUSH_INTERVAL_MS) {
                                lastFlush = now
                                repository.updateStreamingMessage(id, buffer.toString(), true)
                            }
                        }
                    }

                    is AgentEvent.ToolStarted -> {
                        // Close off whatever was being said first, so the tool
                        // row lands between two bubbles rather than inside one.
                        finishText()
                        _thinkingLocal.value = true
                        toolRows[event.id] = repository.addMessage(
                            sessionId = sessionId,
                            role = MessageRole.TOOL,
                            content = event.name,
                            toolCalls = gson.toJson(
                                ToolPayload(
                                    name = event.name,
                                    input = event.label,
                                    status = "RUNNING",
                                ),
                            ),
                        ).id
                    }

                    is AgentEvent.ToolFinished -> {
                        val rowId = toolRows.remove(event.id) ?: return@collect
                        repository.getMessage(rowId)?.let { row ->
                            repository.updateMessage(
                                row.copy(
                                    toolCalls = gson.toJson(
                                        ToolPayload(
                                            name = event.name,
                                            input = null,
                                            status = if (event.outcome.failed) {
                                                "BLOCKED"
                                            } else {
                                                "COMPLETED"
                                            },
                                            result = event.outcome.summary,
                                            ms = event.ms,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }

                    is AgentEvent.Failed -> {
                        _thinkingLocal.value = false
                        finishText()
                        repository.addMessage(
                            sessionId = sessionId,
                            role = MessageRole.SYSTEM,
                            content = event.message,
                            errorMessage = event.message,
                        )
                    }

                    AgentEvent.Done -> {
                        _thinkingLocal.value = false
                        finishText()
                    }
                }
            }
        } finally {
            // NonCancellable, or these writes are dropped on the way out —
            // which is exactly the case being cleaned up after. Pressing stop
            // used to leave the half-written reply flagged as still streaming:
            // a cursor that blinked forever, and a message that went back to
            // the model next turn as though it were complete.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                finishText()
                // A tool row left saying RUNNING is a lie once nothing is.
                for (rowId in toolRows.values) {
                    repository.getMessage(rowId)?.let { row ->
                        repository.updateMessage(
                            row.copy(
                                toolCalls = gson.toJson(
                                    ToolPayload(
                                        name = row.content,
                                        input = null,
                                        status = "BLOCKED",
                                        result = "stopped",
                                    ),
                                ),
                            ),
                        )
                    }
                }
                // An approval still on screen would block the next reply
                // waiting on an answer nobody is coming back to give.
                awaitingApproval?.complete(false)
                awaitingApproval = null
                _localConfirm.value = null
            }
            _thinkingLocal.value = false
        }
    }

    /**
     * Put the pending tool in front of the user and wait.
     *
     * Suspends the agent loop until they answer, which is the point: a shell
     * command should not have run by the time its approval appears.
     */
    private suspend fun askApproval(name: String, description: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        awaitingApproval = deferred
        _localConfirm.value = PendingConfirm(
            id = LOCAL_CONFIRM_ID,
            message = "$name\n\n$description",
        )
        return try {
            deferred.await()
        } finally {
            _localConfirm.value = null
            awaitingApproval = null
        }
    }

    // ── Message shaping ─────────────────────────────────────────────────────

    /**
     * The message as the model receives it, with text attachments inlined.
     *
     * Inlined rather than referenced because there is nowhere to reference: the
     * model has no access to the phone's storage, so a file that is not in the
     * message does not exist as far as it is concerned.
     */
    private fun withAttachments(
        text: String,
        staged: List<Attachment>,
        imagesAsText: Boolean,
    ): String {
        if (staged.isEmpty()) return text
        return buildString {
            append(text)
            for (attachment in staged) {
                when (attachment.kind) {
                    AttachmentKind.TEXT -> {
                        append("\n\n--- ").append(attachment.name).append(" ---\n")
                        append(attachment.text.orEmpty())
                        if (attachment.truncated) append("\n…(truncated)")
                    }
                    AttachmentKind.IMAGE ->
                        if (imagesAsText) {
                            append("\n\n[A photo named ")
                            append(attachment.name)
                            append(" was attached, but this connection cannot carry images.]")
                        }
                    AttachmentKind.BINARY -> Unit
                }
            }
        }.trim()
    }

    /**
     * The message as the transcript shows it.
     *
     * Deliberately not the inlined form: a bubble containing a whole pasted
     * file buries the sentence the user actually wrote, and they already know
     * what they attached.
     */
    private fun describeForTranscript(text: String, staged: List<Attachment>): String {
        if (staged.isEmpty()) return text
        val names = staged.joinToString(", ") {
            if (it.kind == AttachmentKind.IMAGE) "📷 ${it.name}" else "📎 ${it.name}"
        }
        return if (text.isBlank()) names else "$text\n\n$names"
    }

    /**
     * Give a new conversation a name from its first message.
     *
     * Only ever replaces the placeholder, so a conversation the user renamed by
     * hand keeps that name however long it runs.
     */
    private suspend fun titleSessionFrom(text: String) {
        val session = _currentSession.value ?: return
        if (session.title != SessionNamer.UNTITLED && session.title.isNotBlank()) return
        val title = SessionNamer.titleFor(text)
        if (title == SessionNamer.UNTITLED) return
        repository.renameSession(session.id, title)
        _currentSession.value = session.copy(title = title)
    }

    fun setMode(mode: SessionMode) {
        _currentMode.value = mode
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val updated = session.copy(mode = mode)
            repository.updateSession(updated)
            _currentSession.value = updated
        }
    }

    override fun onCleared() {
        super.onCleared()
        socket.disconnect()
        listener.stop()
        speaker.shutdown()
    }

    private companion object {
        /** Id for approvals raised by the phone rather than by the desktop. */
        const val LOCAL_CONFIRM_ID = "local"
        const val MAX_ATTACHMENTS = 6
        const val FLUSH_INTERVAL_MS = 100L
    }
}

data class MessageItem(
    val id: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean,
    val toolCalls: String?,
    val metadata: String?,
    val errorMessage: String?,
)

fun Message.toMessageItem() = MessageItem(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp,
    isStreaming = isStreaming,
    toolCalls = toolCalls,
    metadata = metadata,
    errorMessage = errorMessage,
)
