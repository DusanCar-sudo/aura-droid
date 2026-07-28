package dev.aura.auradroid.data.repository

import dev.aura.auradroid.data.local.AuraDatabase
import dev.aura.auradroid.data.local.MessageDao
import dev.aura.auradroid.data.local.SessionDao
import dev.aura.auradroid.data.model.*
import dev.aura.auradroid.data.network.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraRepository @Inject constructor(
    private val database: AuraDatabase
) {

    private val sessionDao: SessionDao = database.sessionDao()
    private val messageDao: MessageDao = database.messageDao()

    // Session operations
    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAllSessions()

    fun getActiveSessions(): Flow<List<Session>> = sessionDao.getActiveSessions()

    suspend fun getSessionById(sessionId: String): Session? = sessionDao.getSessionById(sessionId)

    fun getSessionByIdFlow(sessionId: String): Flow<Session?> = sessionDao.getSessionByIdFlow(sessionId)

    suspend fun createSession(
        title: String,
        model: String,
        provider: String,
        mode: SessionMode = SessionMode.CODER,
        projectPath: String? = null
    ): Session {
        val sessionId = generateSessionId()
        val session = Session(
            id = sessionId,
            title = title,
            model = model,
            provider = provider,
            mode = mode,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            projectPath = projectPath
        )
        sessionDao.insertSession(session)
        return session
    }

    suspend fun updateSession(session: Session) = sessionDao.updateSession(session)

    suspend fun deleteSession(sessionId: String) = sessionDao.deleteSessionById(sessionId)

    suspend fun togglePinSession(sessionId: String) {
        val session = getSessionById(sessionId) ?: return
        sessionDao.updatePinnedStatus(sessionId, !session.isPinned)
    }

    suspend fun archiveSession(sessionId: String) = sessionDao.updateArchivedStatus(sessionId, true)

    // Message operations
    fun getMessagesForSession(sessionId: String): Flow<List<Message>> =
        messageDao.getMessagesForSession(sessionId)

    suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        isStreaming: Boolean = false,
        toolCalls: String? = null,
        errorMessage: String? = null
    ): Message {
        val message = Message(
            sessionId = sessionId,
            role = role,
            content = content,
            isStreaming = isStreaming,
            toolCalls = toolCalls,
            errorMessage = errorMessage
        )
        val messageId = messageDao.insertMessage(message)
        sessionDao.incrementMessageCount(sessionId)
        return message.copy(id = messageId)
    }

    suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)

    suspend fun updateStreamingMessage(messageId: Long, content: String, isStreaming: Boolean = false) {
        messageDao.updateMessageContent(messageId, content, isStreaming)
    }

    suspend fun getLastMessage(sessionId: String): Message? = messageDao.getLastMessage(sessionId)

    // Helper functions
    private fun generateSessionId(): String {
        return "sess_${System.currentTimeMillis()}_${(0..999).random()}"
    }
}
