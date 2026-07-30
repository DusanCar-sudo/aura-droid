package dev.aura.auradroid.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.model.Session
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.repository.AuraRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AuraRepository
) : ViewModel() {

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentMode = MutableStateFlow(SessionMode.CODER)
    val currentMode: StateFlow<SessionMode> = _currentMode.asStateFlow()

    /** Collector for the active session's messages; cancelled when switching sessions. */
    private var messageCollectJob: Job? = null

    init {
        viewModelScope.launch {
            val sessions = repository.getActiveSessions().first()
            if (sessions.isEmpty()) {
                createNewSession()
            } else {
                loadSession(sessions.first().id)
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
            repository.getMessagesForSession(sessionId).collect { messageList ->
                _messages.value = messageList.map { it.toMessageItem() }
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = repository.createSession(
                title = "New Chat",
                model = "deepseek/deepseek-chat",
                provider = "DeepSeek",
                mode = _currentMode.value
            )
            loadSession(session.id)
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isLoading.value) return

        val sessionId = _currentSession.value?.id ?: return

        viewModelScope.launch {
            repository.addMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = text
            )
            _inputText.value = ""
            _isLoading.value = true

            try {
                respond(sessionId, text)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Placeholder response. Wired to the Aura HTTP API in a follow-up —
     * see AuraApiService.sendMessageStream.
     */
    private suspend fun respond(sessionId: String, userMessage: String) {
        kotlinx.coroutines.delay(600)

        val response = when {
            userMessage.contains("hello", ignoreCase = true) ->
                "Hello. What are we building?"
            userMessage.contains("help", ignoreCase = true) ->
                "I can help with coding, debugging, architecture, and explanation. " +
                    "Point me at a task and I'll read, plan, execute, and verify."
            else ->
                "Not yet connected to the Aura backend. Your message was: \"$userMessage\""
        }

        repository.addMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = response
        )
    }

    fun setMode(mode: SessionMode) {
        _currentMode.value = mode
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            repository.updateSession(session.copy(mode = mode))
            _currentSession.value = session.copy(mode = mode)
        }
    }
}

data class MessageItem(
    val id: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean
)

fun Message.toMessageItem() = MessageItem(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp,
    isStreaming = isStreaming
)
