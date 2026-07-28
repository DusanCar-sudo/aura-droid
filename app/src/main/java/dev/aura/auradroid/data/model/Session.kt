package dev.aura.auradroid.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey
    val id: String,

    val title: String,

    val model: String,

    val provider: String,

    val mode: SessionMode = SessionMode.CODER,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    val messageCount: Int = 0,

    val projectPath: String? = null,

    val isPinned: Boolean = false,

    val isArchived: Boolean = false
)

enum class SessionMode {
    CODER,
    GAZELLE,
    ARCHITECT
}
