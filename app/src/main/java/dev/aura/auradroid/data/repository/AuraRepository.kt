package dev.aura.auradroid.data.repository

import dev.aura.auradroid.data.local.AuraDatabase
import dev.aura.auradroid.data.local.MemoDao
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
) : MessageStore {

    private val sessionDao: SessionDao = database.sessionDao()
    private val messageDao: MessageDao = database.messageDao()
    private val memoDao: MemoDao = database.memoDao()

    // ── Memos ───────────────────────────────────────────────────────────────

    fun getMemos(): Flow<List<Memo>> = memoDao.getAllMemos()

    suspend fun getMemoById(id: String): Memo? = memoDao.getMemoById(id)

    suspend fun saveMemo(text: String, durationMs: Long): Memo {
        val memo = Memo(
            id = java.util.UUID.randomUUID().toString(),
            text = text.trim(),
            title = titleFor(text),
            durationMs = durationMs,
        )
        memoDao.insertMemo(memo)
        return memo
    }

    suspend fun updateMemoText(id: String, text: String) {
        val memo = memoDao.getMemoById(id) ?: return
        // The title tracks the text, or an edited memo keeps a title that no
        // longer describes it.
        memoDao.updateMemo(memo.copy(text = text.trim(), title = titleFor(text)))
    }

    suspend fun linkMemoToSession(id: String, sessionId: String) {
        val memo = memoDao.getMemoById(id) ?: return
        memoDao.updateMemo(memo.copy(startedSessionId = sessionId))
    }

    suspend fun deleteMemo(id: String) = memoDao.deleteMemoById(id)

    suspend fun markMemoSynced(id: String) {
        val memo = memoDao.getMemoById(id) ?: return
        memoDao.updateMemo(memo.copy(synced = true))
    }

    /** First sentence or so, for the list. */
    private fun titleFor(text: String): String {
        val firstLine = text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val cut = firstLine.take(60).trim()
        return when {
            cut.isEmpty() -> "Untitled memo"
            firstLine.length > 60 -> "$cut…"
            else -> cut
        }
    }

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

    /**
     * Rename a conversation. Blank titles are ignored rather than stored — an
     * untitled row is indistinguishable from a broken one in the list.
     */
    suspend fun renameSession(sessionId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun togglePinSession(sessionId: String) {
        val session = getSessionById(sessionId) ?: return
        sessionDao.updatePinnedStatus(sessionId, !session.isPinned)
    }

    suspend fun archiveSession(sessionId: String) = sessionDao.updateArchivedStatus(sessionId, true)

    // Message operations
    fun getMessagesForSession(sessionId: String): Flow<List<Message>> =
        messageDao.getMessagesForSession(sessionId)

    override suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        isStreaming: Boolean,
        toolCalls: String?,
        metadata: String?,
        errorMessage: String?
    ): Message {
        val message = Message(
            sessionId = sessionId,
            role = role,
            content = content,
            isStreaming = isStreaming,
            toolCalls = toolCalls,
            metadata = metadata,
            errorMessage = errorMessage
        )
        val messageId = messageDao.insertMessage(message)
        sessionDao.incrementMessageCount(sessionId)
        return message.copy(id = messageId)
    }

    override suspend fun getMessage(messageId: Long): Message? = messageDao.getMessageById(messageId)

    override suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)

    override suspend fun updateStreamingMessage(messageId: Long, content: String, isStreaming: Boolean) {
        messageDao.updateMessageContent(messageId, content, isStreaming)
    }

    suspend fun getLastMessage(sessionId: String): Message? = messageDao.getLastMessage(sessionId)

    // Helper functions
    private fun generateSessionId(): String {
        return "sess_${System.currentTimeMillis()}_${(0..999).random()}"
    }
}
