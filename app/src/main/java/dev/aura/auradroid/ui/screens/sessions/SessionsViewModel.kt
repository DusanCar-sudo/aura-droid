package dev.aura.auradroid.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aura.auradroid.data.repository.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun createNewSession() {
        viewModelScope.launch {
            repository.createSession(
                title = "New Chat",
                model = "deepseek/deepseek-chat",
                provider = "DeepSeek"
            )
        }
    }

    fun selectSession(sessionId: String) {
        // This will be handled by navigation back to chat screen
        viewModelScope.launch {
            // Update current session in shared preferences or via navigation
        }
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
