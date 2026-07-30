package dev.aura.auradroid.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A spoken note, kept as text.
 *
 * The audio itself is deliberately not stored. Recognition happens live on the
 * device, so once the words exist the recording adds nothing a user can search,
 * edit or send — while costing megabytes per minute and creating a pile of
 * voice recordings of someone's unfinished thoughts sitting on disk.
 */
@Entity(tableName = "memos")
data class Memo(
    @PrimaryKey
    val id: String,

    /** What was said. Editable, because recognition is not perfect. */
    val text: String,

    /** First line, or the opening words — shown in the list. */
    val title: String,

    val createdAt: Long = System.currentTimeMillis(),

    /** How long the recording ran, for the list. */
    val durationMs: Long = 0,

    /**
     * The session started from this memo, if any. Keeps the note attached to
     * what came of it rather than leaving a stack of orphaned ideas.
     */
    val startedSessionId: String? = null,

    /**
     * Whether the desktop has this in its episodic memory.
     *
     * Memos are recorded wherever the user happens to be, often with no
     * desktop in reach, so syncing is a later step rather than a condition of
     * saving one.
     */
    val synced: Boolean = false,
)
