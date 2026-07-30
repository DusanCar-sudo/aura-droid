package dev.aura.auradroid.data.export

import dev.aura.auradroid.data.model.Memo
import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.model.Session
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a conversation into something another tool can read.
 *
 * The point is portability: a conversation that only this app can open is a
 * conversation you have to abandon when you move. Markdown is the format
 * every coding agent already accepts — paste it into a prompt and the context
 * comes with it — so that is the default, with JSON for anything doing the
 * reading programmatically.
 */
object ChatExporter {

    enum class Format { MARKDOWN, JSON }

    fun fileName(session: Session, format: Format): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(session.updatedAt))
        val slug = session.title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "chat" }
        return "aura-$slug-$stamp." + if (format == Format.MARKDOWN) "md" else "json"
    }

    fun export(session: Session, messages: List<Message>, format: Format): String =
        when (format) {
            Format.MARKDOWN -> markdown(session, messages)
            Format.JSON -> json(session, messages)
        }

    private fun markdown(session: Session, messages: List<Message>): String = buildString {
        appendLine("# ${session.title}")
        appendLine()
        appendLine("- Mode: ${session.mode.name.lowercase()}")
        if (session.model.isNotBlank()) appendLine("- Model: ${session.model}")
        appendLine("- Exported: ${stamp(System.currentTimeMillis())}")
        appendLine("- Messages: ${messages.size}")
        appendLine()
        appendLine("---")
        appendLine()

        for (m in messages) {
            when (m.role) {
                MessageRole.USER -> appendLine("## You")
                MessageRole.ASSISTANT -> appendLine("## Aura")
                MessageRole.TOOL -> appendLine("## Tool")
                MessageRole.SYSTEM -> appendLine("## System")
            }
            appendLine()
            if (m.role == MessageRole.TOOL) {
                // Tool output is not prose; fencing it keeps it from being read
                // as instructions when this is pasted into another agent.
                appendLine("```")
                appendLine(m.content.trim())
                m.toolCalls?.takeIf { it.isNotBlank() }?.let { appendLine(it.trim()) }
                appendLine("```")
            } else {
                appendLine(m.content.trim())
            }
            m.errorMessage?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("> Error: $it")
            }
            appendLine()
        }
    }.trimEnd() + "\n"

    private fun json(session: Session, messages: List<Message>): String {
        val root = JSONObject()
        root.put("title", session.title)
        root.put("mode", session.mode.name.lowercase())
        root.put("model", session.model)
        root.put("createdAt", stamp(session.createdAt))
        root.put("exportedAt", stamp(System.currentTimeMillis()))

        val arr = JSONArray()
        for (m in messages) {
            val o = JSONObject()
            o.put("role", m.role.name.lowercase())
            o.put("content", m.content)
            o.put("at", stamp(m.timestamp))
            m.toolCalls?.takeIf { it.isNotBlank() }?.let { o.put("toolCalls", it) }
            m.errorMessage?.takeIf { it.isNotBlank() }?.let { o.put("error", it) }
            arr.put(o)
        }
        root.put("messages", arr)
        return root.toString(2)
    }

    /** Memos, for moving a whole thinking-out-loud archive somewhere else. */
    fun exportMemos(memos: List<Memo>, format: Format): String = when (format) {
        Format.MARKDOWN -> buildString {
            appendLine("# Memos")
            appendLine()
            appendLine("- Exported: ${stamp(System.currentTimeMillis())}")
            appendLine("- Count: ${memos.size}")
            appendLine()
            for (memo in memos) {
                appendLine("---")
                appendLine()
                appendLine("## ${memo.title}")
                appendLine()
                appendLine("*${stamp(memo.createdAt)}*")
                appendLine()
                appendLine(memo.text.trim())
                appendLine()
            }
        }.trimEnd() + "\n"

        Format.JSON -> JSONArray().apply {
            for (memo in memos) {
                put(
                    JSONObject()
                        .put("title", memo.title)
                        .put("text", memo.text)
                        .put("at", stamp(memo.createdAt))
                        .put("durationMs", memo.durationMs),
                )
            }
        }.toString(2)
    }

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
