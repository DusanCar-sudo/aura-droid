package dev.aura.auradroid.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.repository.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: dev.aura.auradroid.data.repository.AuraRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<dev.aura.auradroid.data.model.Session>>(emptyList())
    val sessions: StateFlow<List<dev.aura.auradroid.data.model.Session>> = _sessions.asStateFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            repository.getActiveSessions().collect { sessionList ->
                _sessions.value = sessionList.sortedByDescending { it.updatedAt }
            }
        }
    }

    /**
     * Create a conversation and hand its id back so the caller can open it.
     *
     * Model and provider are left empty rather than guessed: the phone holds
     * no credentials and the desktop's configured model is the only correct
     * answer, which the chat screen fills in from /api/project. This used to
     * hardcode a DeepSeek model, which was simply wrong on any other setup.
     */
    fun createNewSession(onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val session = repository.createSession(
                title = "New Chat",
                model = "",
                provider = "",
            )
            onCreated(session.id)
        }
    }

    /**
     * Export a conversation and hand it to the share sheet.
     *
     * Markdown by default because it is what every other coding agent accepts
     * as context — the point is that a conversation started here does not have
     * to end here.
     */
    fun exportSession(
        context: android.content.Context,
        sessionId: String,
        format: dev.aura.auradroid.data.export.ChatExporter.Format,
    ) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId) ?: return@launch
            val messages = repository.getMessagesForSession(sessionId).first()
            val body = dev.aura.auradroid.data.export.ChatExporter.export(session, messages, format)
            dev.aura.auradroid.data.export.Sharing.shareText(
                context = context,
                fileName = dev.aura.auradroid.data.export.ChatExporter.fileName(session, format),
                content = body,
                subject = session.title,
            )
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch { repository.renameSession(sessionId, title) }
    }

    fun togglePin(sessionId: String) {
        viewModelScope.launch {
            repository.togglePinSession(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}
