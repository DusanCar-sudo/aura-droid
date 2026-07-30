package dev.aura.auradroid.data.repository

import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.MessageRole

/**
 * The four message operations [EventSink] needs.
 *
 * Narrower than [AuraRepository] on purpose: the sink folds a server stream
 * into rows and has no business with sessions, pinning, or archiving. It also
 * means the folding logic is testable on the JVM without standing up Room.
 */
interface MessageStore {

    suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        isStreaming: Boolean = false,
        toolCalls: String? = null,
        metadata: String? = null,
        errorMessage: String? = null,
    ): Message

    suspend fun getMessage(messageId: Long): Message?

    suspend fun updateMessage(message: Message)

    suspend fun updateStreamingMessage(
        messageId: Long,
        content: String,
        isStreaming: Boolean = false,
    )
}
