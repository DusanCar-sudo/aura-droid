package dev.aura.auradroid.data.memory

import dev.aura.auradroid.data.local.MemoryDao
import dev.aura.auradroid.data.model.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real map rather than a mock.
 *
 * The behaviour under test is what ends up stored after a sequence of writes,
 * and verifying calls on a mock would assert the implementation rather than
 * the outcome — which is the part that has to be right.
 */
private class FakeMemoryDao : MemoryDao {
    val rows = linkedMapOf<String, Memory>()

    override fun observeAll(): Flow<List<Memory>> = flowOf(rows.values.toList())

    override suspend fun top(limit: Int): List<Memory> =
        rows.values.sortedWith(
            compareByDescending<Memory> { it.useCount }.thenByDescending { it.lastUsedAt },
        ).take(limit)

    override suspend fun search(query: String, limit: Int): List<Memory> =
        rows.values.filter {
            it.text.contains(query, ignoreCase = true) ||
                it.tag.contains(query, ignoreCase = true)
        }.take(limit)

    override suspend fun byId(id: String): Memory? = rows[id]

    override suspend fun all(): List<Memory> = rows.values.toList()

    override suspend fun upsert(memory: Memory) {
        rows[memory.id] = memory
    }

    override suspend fun markUsed(ids: List<String>, now: Long) {
        for (id in ids) {
            rows[id]?.let { rows[id] = it.copy(useCount = it.useCount + 1, lastUsedAt = now) }
        }
    }

    override suspend fun deleteById(id: String) {
        rows.remove(id)
    }

    override suspend fun deleteAll() = rows.clear()

    override suspend fun count(): Int = rows.size

    override suspend fun trimOldest(count: Int) {
        rows.values.sortedWith(compareBy<Memory> { it.useCount }.thenBy { it.lastUsedAt })
            .take(count)
            .forEach { rows.remove(it.id) }
    }
}

class AgentMemoryTest {

    @Test
    fun `a restated fact updates the existing note rather than adding another`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)

        memory.remember("Dusan prefers Kotlin over Java for Android work")
        memory.remember("Dusan prefers Kotlin over Java for android work.")

        // Models rephrase the same fact every few turns. Stored verbatim each
        // time, the prompt fills with one fact said five ways.
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `genuinely different facts are both kept`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)

        memory.remember("Dusan prefers Kotlin")
        memory.remember("The desktop runs on port 7337")

        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `a fragment is not worth remembering`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)

        assertNull(memory.remember("  "))
        assertNull(memory.remember("ok"))
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `recall counts the hit so used notes stay in the prompt`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)
        memory.remember("The project is called Aura Droid", tag = "project")

        val before = dao.rows.values.first().useCount
        val hits = memory.recall("Aura")

        assertEquals(1, hits.size)
        assertEquals(before + 1, dao.rows.values.first().useCount)
    }

    @Test
    fun `recall with no query returns the most-used notes`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)
        memory.remember("Fact one about the build system")
        memory.remember("Fact two about the deployment")

        assertEquals(2, memory.recall("").size)
    }

    @Test
    fun `the table stays under its cap`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)

        // Everything remembered is resent on every turn, so an unbounded table
        // is a permanent and growing tax on the context window.
        repeat(260) { memory.remember("Distinct note number $it about topic $it") }

        assertTrue("was ${dao.rows.size}", dao.rows.size <= 200)
    }

    @Test
    fun `the prompt block is null until there is something to say`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)

        assertNull(memory.promptBlock())

        memory.remember("Dusan works in Serbian and English", tag = "preference")
        val block = memory.promptBlock()

        assertTrue(block!!.contains("Serbian"))
        assertTrue(block.contains("[preference]"))
    }

    @Test
    fun `forgetting removes it from the prompt`() = runTest {
        val dao = FakeMemoryDao()
        val memory = AgentMemory(dao)
        val saved = memory.remember("A fact that turns out to be wrong")!!

        memory.forget(saved.id)

        assertNull(memory.promptBlock())
    }
}
