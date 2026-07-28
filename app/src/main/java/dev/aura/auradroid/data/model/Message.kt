package dev.aura.auradroid.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,

    val role: MessageRole,

    val content: String,

    val timestamp: Long = System.currentTimeMillis(),

    val isStreaming: Boolean = false,

    val metadata: String? = null, // JSON for tool calls, files, etc.

    val toolCalls: String? = null, // JSON array of tool calls

    val errorMessage: String? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any>,
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

enum class ToolCallStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
