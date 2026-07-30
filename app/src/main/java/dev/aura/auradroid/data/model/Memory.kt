package dev.aura.auradroid.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Something the agent chose to keep.
 *
 * Distinct from [Memo], which is the *user* talking into the phone. This is the
 * agent's own note to itself, written by the `remember` tool and read back into
 * the system prompt on every turn — the difference between an assistant that
 * asks which provider you use each morning and one that already knows.
 *
 * Kept small on purpose. Everything here is resent to the model each turn, so
 * an unbounded pile would quietly eat the context window; [MemoryDao] caps what
 * is loaded and prefers what is actually used.
 */
@Entity(tableName = "agent_memory")
data class Memory(
    @PrimaryKey
    val id: String,

    /** The fact itself, in the agent's words. One thing per row. */
    val text: String,

    /**
     * A loose bucket — "preference", "project", "person". Free text rather than
     * an enum because the model invents the useful ones, and a fixed list would
     * force everything into "other" within a week.
     */
    val tag: String = "note",

    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Last time this was read back or rewritten.
     *
     * Recency beats age for deciding what still matters: a fact from months ago
     * that keeps coming up is worth more of the context window than one written
     * yesterday and never touched since.
     */
    val lastUsedAt: Long = System.currentTimeMillis(),

    /** How often it has been recalled. Breaks ties when trimming. */
    val useCount: Int = 0,

    /** The conversation it was learned in, for tracing a fact back. */
    val sessionId: String? = null,
)
