package dev.aura.auradroid.data.local

import androidx.room.*
import dev.aura.auradroid.data.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getActiveSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): Session?

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionByIdFlow(sessionId: String): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedSessions(): Flow<List<Session>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Query("UPDATE sessions SET messageCount = messageCount + 1, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET isPinned = :isPinned WHERE id = :sessionId")
    suspend fun updatePinnedStatus(sessionId: String, isPinned: Boolean)

    @Query("UPDATE sessions SET isArchived = :isArchived WHERE id = :sessionId")
    suspend fun updateArchivedStatus(sessionId: String, isArchived: Boolean)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)
}
