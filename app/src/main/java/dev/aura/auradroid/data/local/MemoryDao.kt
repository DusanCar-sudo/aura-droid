package dev.aura.auradroid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.aura.auradroid.data.model.Memory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    /** Newest first, for the screen that shows what the agent knows. */
    @Query("SELECT * FROM agent_memory ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<Memory>>

    /**
     * What gets injected into the system prompt.
     *
     * Ordered by use then recency, and capped: this is spent context on every
     * single turn, so the budget is small and the most-earned rows win.
     */
    @Query("SELECT * FROM agent_memory ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit")
    suspend fun top(limit: Int): List<Memory>

    /**
     * Substring search over the text and the tag.
     *
     * Deliberately not FTS: the table is tens of rows, LIKE is instant at that
     * size, and an FTS4 virtual table would mean another migration and another
     * thing to keep in sync for no measurable gain.
     */
    @Query(
        """
        SELECT * FROM agent_memory
        WHERE text LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%'
        ORDER BY useCount DESC, lastUsedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<Memory>

    @Query("SELECT * FROM agent_memory WHERE id = :id")
    suspend fun byId(id: String): Memory?

    /** Used to spot a near-duplicate before writing another copy of a fact. */
    @Query("SELECT * FROM agent_memory")
    suspend fun all(): List<Memory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: Memory)

    @Query("UPDATE agent_memory SET useCount = useCount + 1, lastUsedAt = :now WHERE id IN (:ids)")
    suspend fun markUsed(ids: List<String>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM agent_memory WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM agent_memory")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM agent_memory")
    suspend fun count(): Int

    /**
     * Drop the least valuable rows once the table outgrows its cap.
     *
     * Without this the agent accumulates every passing detail forever and the
     * prompt slowly fills with things that stopped being true.
     */
    @Query(
        """
        DELETE FROM agent_memory WHERE id IN (
            SELECT id FROM agent_memory ORDER BY useCount ASC, lastUsedAt ASC LIMIT :count
        )
        """,
    )
    suspend fun trimOldest(count: Int)
}
