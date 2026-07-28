package dev.aura.auradroid.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.model.Session
import dev.aura.auradroid.data.model.SessionMode
import dev.aura.auradroid.data.repository.AuraRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AuraRepository
) : ViewModel() {

    private val _currentSession = mutableStateOf<Session?>(null)
    val currentSession: State<Session?> = _currentSession

    private val _messages = mutableStateListOf<MessageItem>()
    val messages: List<MessageItem> = _messages

    private val _inputText = mutableStateOf("")
    val inputText: State<String> = _inputText

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _isStreaming = mutableStateOf(false)
    val isStreaming: State<Boolean> = _isStreaming

    private val _currentMode = mutableStateOf(SessionMode.CODER)
    val currentMode: State<SessionMode> = _currentMode

    init {
        // Create a default session if none exists
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
        viewModelScope.launch {
            _currentSession.value = repository.getSessionById(sessionId)
            repository.getMessagesForSession(sessionId).collect { messageList ->
                _messages.clear()
                _messages.addAll(messageList.map { it.toMessageItem() })
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
            _currentSession.value = session
            _messages.clear()
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isLoading.value) return

        viewModelScope.launch {
            val sessionId = _currentSession.value?.id ?: return@launch

            // Add user message
            val userMessage = repository.addMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = text
            )
            _messages.add(userMessage.toMessageItem())
            _inputText.value = ""
            _isLoading.value = true

            // Simulate AI response (replace with actual API call)
            simulateAIResponse(sessionId, text)
        }
    }

    private suspend fun simulateAIResponse(sessionId: String, userMessage: String) {
        // This is a placeholder - replace with actual API call
        kotlinx.coroutines.delay(1000)

        val response = when {
            userMessage.contains("hello", ignoreCase = true) -> "Hello! How can I help you today?"
            userMessage.contains("code", ignoreCase = true) -> "I can help you with coding tasks. What would you like me to work on?"
            userMessage.contains("help", ignoreCase = true) -> "I'm Aura, your AI coding assistant. I can help you with:\n• Writing and refactoring code\n• Debugging issues\n• Explaining code\n• Architectural decisions\n\nWhat would you like to work on?"
            else -> "I understand you're asking about: \"$userMessage\". Let me help you with that..."
        }

        val assistantMessage = repository.addMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = response
        )
        _messages.add(assistantMessage.toMessageItem())
        _isLoading.value = false
    }

    fun setMode(mode: SessionMode) {
        _currentMode.value = mode
    }

    fun clearMessages() {
        _messages.clear()
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
