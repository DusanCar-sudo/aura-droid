package dev.aura.auradroid.data.memory

import dev.aura.auradroid.data.local.MemoryDao
import dev.aura.auradroid.data.model.Memory
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agent's own long-term memory.
 *
 * Three jobs, none of which belong in a DAO: refuse near-duplicates, keep the
 * table under a cap, and render the whole thing into the handful of lines that
 * go into the system prompt.
 *
 * The cap is the important one. Everything remembered is resent on every turn,
 * so memory is not free storage — it is a permanent tax on the context window,
 * and a phone talking to a small model has little of it to spend.
 */
@Singleton
class AgentMemory @Inject constructor(
    private val dao: MemoryDao,
) {

    fun observeAll(): Flow<List<Memory>> = dao.observeAll()

    suspend fun count(): Int = dao.count()

    /**
     * Store a fact, or refresh the one that already says it.
     *
     * Models restate the same thing in slightly different words across turns —
     * "prefers Kotlin", "the user likes Kotlin" — and stored verbatim each time
     * the prompt fills with the same fact five ways. A near-duplicate updates
     * the existing row instead, which also refreshes its recency.
     */
    suspend fun remember(text: String, tag: String = "note", sessionId: String? = null): Memory? {
        val clean = text.trim().replace(WHITESPACE, " ")
        if (clean.length < MIN_LENGTH) return null

        val trimmed = clean.take(MAX_TEXT)
        val existing = dao.all().firstOrNull { similar(it.text, trimmed) }

        val row = existing?.copy(
            text = trimmed,
            tag = tag.trim().ifBlank { existing.tag },
            lastUsedAt = System.currentTimeMillis(),
            useCount = existing.useCount + 1,
        ) ?: Memory(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            tag = tag.trim().ifBlank { "note" }.take(24),
            sessionId = sessionId,
        )

        dao.upsert(row)

        // Trim after inserting rather than before: the fact just learned is the
        // one most likely to matter, and should not lose a race against rows
        // that are on their way out anyway.
        val over = dao.count() - MAX_ROWS
        if (over > 0) dao.trimOldest(over)

        return row
    }

    /**
     * Look something up, and count the hit.
     *
     * The counting is the point: rows that keep answering questions rise to the
     * top of what gets injected, and rows that never do sink until they are
     * trimmed. It makes the cap self-sorting rather than arbitrary.
     */
    suspend fun recall(query: String, limit: Int = SEARCH_LIMIT): List<Memory> {
        val clean = query.trim()
        val hits = if (clean.isEmpty()) dao.top(limit) else dao.search(clean, limit)
        if (hits.isNotEmpty()) dao.markUsed(hits.map { it.id })
        return hits
    }

    suspend fun forget(id: String) = dao.deleteById(id)

    suspend fun forgetAll() = dao.deleteAll()

    /**
     * The block that goes into the system prompt, or null when there is nothing
     * worth spending the tokens on.
     */
    suspend fun promptBlock(): String? {
        val rows = dao.top(PROMPT_LIMIT)
        if (rows.isEmpty()) return null
        return buildString {
            append("What you remember about this person and their work:\n")
            for (row in rows) append("- [${row.tag}] ${row.text}\n")
            append(
                "Treat these as things you already know — do not re-ask. " +
                    "Use `remember` when you learn something durable, " +
                    "`forget` when one of them turns out to be wrong.",
            )
        }
    }

    /**
     * Whether two notes say the same thing.
     *
     * Word overlap rather than string distance: the restatements that matter
     * here differ by filler words ("the user", "prefers to") while sharing the
     * nouns that carry the fact, and Jaccard over the word sets catches exactly
     * that while leaving genuinely different facts alone.
     */
    private fun similar(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val wordsA = words(a)
        val wordsB = words(b)
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val shared = wordsA.intersect(wordsB).size.toDouble()
        return shared / minOf(wordsA.size, wordsB.size) >= DUPLICATE_OVERLAP
    }

    private fun words(text: String): Set<String> =
        text.lowercase().split(NON_WORD).filter { it.length > 2 }.toSet()

    private companion object {
        /** Below this it is not a fact, it is a fragment. */
        const val MIN_LENGTH = 3
        const val MAX_TEXT = 280
        const val MAX_ROWS = 200
        const val PROMPT_LIMIT = 24
        const val SEARCH_LIMIT = 10
        const val DUPLICATE_OVERLAP = 0.7

        val WHITESPACE = Regex("""\s+""")
        val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    }
}
