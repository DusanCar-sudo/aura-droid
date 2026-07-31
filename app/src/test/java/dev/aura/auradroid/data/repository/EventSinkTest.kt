package dev.aura.auradroid.data.repository

import com.google.gson.Gson
import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.MessageRole
import dev.aura.auradroid.data.network.Plan
import dev.aura.auradroid.data.network.PlanStep
import dev.aura.auradroid.data.network.ServerEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * EventSink folds the server stream into Room. The parts worth pinning are the
 * ones that are easy to get subtly wrong and hard to notice: streaming writes
 * being throttled, tool calls pairing with the right result, and the `done`
 * summary not duplicating text the user already watched stream in.
 */
class EventSinkTest {

    private lateinit var repo: FakeRepository
    private lateinit var sink: EventSink
    private val gson = Gson()
    private val session = "s1"

    @Before
    fun setUp() {
        repo = FakeRepository()
        sink = EventSink(repo, gson)
    }

    @Test
    fun `streaming text writes once on the first chunk, not once per token`() = runTest {
        // 50 rapid chunks, as a real response would arrive.
        repeat(50) { sink.handle(session, ServerEvent.Text("tok$it ")) }

        assertEquals("one row for the whole response", 1, repo.inserted.size)
        // The throttle is time-based, so the exact count depends on timing —
        // what must hold is that it is nowhere near one write per chunk.
        assertTrue(
            "expected far fewer than 50 updates, got ${repo.updates}",
            repo.updates < 10,
        )
    }

    @Test
    fun `text_end flushes the complete buffer and clears the streaming flag`() = runTest {
        sink.handle(session, ServerEvent.Text("Hello "))
        sink.handle(session, ServerEvent.Text("world"))
        sink.handle(session, ServerEvent.TextEnd)

        val row = repo.store.values.single()
        assertEquals("Hello world", row.content)
        assertFalse("must not stay marked streaming", row.isStreaming)
    }

    @Test
    fun `done finalises the stream without duplicating the text`() = runTest {
        sink.handle(session, ServerEvent.Text("Streamed answer"))
        sink.handle(session, ServerEvent.Done("Streamed answer", true, 1, 0))

        assertEquals("summary must not become a second message", 1, repo.store.size)
        assertEquals("Streamed answer", repo.store.values.single().content)
    }

    @Test
    fun `text_end before done does not duplicate the reply`() = runTest {
        // The ordering a real `aura serve` actually sends. Checking for a live
        // stream at `done` time reports none — text_end already finalised it —
        // and the summary lands as a visible second copy of the same reply.
        sink.handle(session, ServerEvent.Text("Created approved.txt."))
        sink.handle(session, ServerEvent.TextEnd)
        sink.handle(session, ServerEvent.Done("Created approved.txt.", true, 1, 1))

        assertEquals("summary must not become a second message", 1, repo.store.size)
        assertEquals("Created approved.txt.", repo.store.values.single().content)
    }

    @Test
    fun `a later turn still gets its summary when it streams nothing`() = runTest {
        sink.handle(session, ServerEvent.Text("First turn"))
        sink.handle(session, ServerEvent.TextEnd)
        sink.handle(session, ServerEvent.Done("First turn", true, 1, 0))

        // Second turn produces only a summary; suppressing it would lose the
        // reply entirely, so the guard has to be per-turn, not sticky.
        sink.handle(session, ServerEvent.Done("Second turn", true, 1, 0))

        assertEquals(2, repo.store.size)
        assertEquals(
            listOf("First turn", "Second turn"),
            repo.store.values.map { it.content },
        )
    }

    @Test
    fun `done writes the summary when nothing streamed`() = runTest {
        // Some paths finish without emitting text events at all.
        sink.handle(session, ServerEvent.Done("Only summary", true, 1, 0))

        assertEquals(1, repo.store.size)
        assertEquals("Only summary", repo.store.values.single().content)
    }

    @Test
    fun `tool result updates the call that is pending`() = runTest {
        sink.handle(session, ServerEvent.ToolCall("read_file", null))
        sink.handle(session, ServerEvent.ToolResult("read_file", "contents", 42))

        val row = repo.store.values.single { it.role == MessageRole.TOOL }
        val payload = gson.fromJson(row.toolCalls, ToolPayload::class.java)
        assertEquals("COMPLETED", payload.status)
        assertEquals("contents", payload.result)
        assertEquals(42L, payload.ms)
    }

    @Test
    fun `blocked tool records the reason`() = runTest {
        sink.handle(session, ServerEvent.ToolCall("run_shell", null))
        sink.handle(session, ServerEvent.ToolBlocked("run_shell", "denied by user"))

        val payload = gson.fromJson(
            repo.store.values.single { it.role == MessageRole.TOOL }.toolCalls,
            ToolPayload::class.java,
        )
        assertEquals("BLOCKED", payload.status)
        assertEquals("denied by user", payload.result)
    }

    @Test
    fun `sequential tools each get their own row and result`() = runTest {
        sink.handle(session, ServerEvent.ToolCall("read_file", null))
        sink.handle(session, ServerEvent.ToolResult("read_file", "first", 1))
        sink.handle(session, ServerEvent.ToolCall("list_dir", null))
        sink.handle(session, ServerEvent.ToolResult("list_dir", "second", 2))

        val tools = repo.store.values.filter { it.role == MessageRole.TOOL }
            .sortedBy { it.id }
        assertEquals(2, tools.size)
        assertEquals("first", gson.fromJson(tools[0].toolCalls, ToolPayload::class.java).result)
        assertEquals("second", gson.fromJson(tools[1].toolCalls, ToolPayload::class.java).result)
    }

    @Test
    fun `a result with no preceding call is ignored rather than crashing`() = runTest {
        sink.handle(session, ServerEvent.ToolResult("orphan", "x", 1))
        assertTrue(repo.store.isEmpty())
    }

    @Test
    fun `plan progress updates one row in place`() = runTest {
        sink.handle(session, ServerEvent.PlanCreating)
        sink.handle(
            session,
            ServerEvent.PlanCreated(
                Plan(
                    "p1", "refactor auth",
                    listOf(
                        PlanStep("s1", "researcher", "read"),
                        PlanStep("s2", "coder", "edit"),
                    ),
                ),
            ),
        )
        sink.handle(session, ServerEvent.StepStarted(PlanStep("s1", "researcher", "read")))
        sink.handle(session, ServerEvent.StepCompleted(PlanStep("s1", "researcher", "read"), "ok"))

        val planRows = repo.store.values.filter { it.role == MessageRole.SYSTEM }
        assertEquals("plan must not append a row per step", 1, planRows.size)

        val payload = gson.fromJson(planRows.single().metadata, PlanPayload::class.java)
        assertEquals("refactor auth", payload.goal)
        assertEquals(2, payload.steps.size)
        assertEquals("DONE", payload.steps[0].status)
        assertEquals("ok", payload.steps[0].result)
        assertEquals("PENDING", payload.steps[1].status)
    }

    @Test
    fun `confirm request surfaces then clears on timeout`() = runTest {
        sink.handle(session, ServerEvent.ConfirmRequest("c1", "Allow: write_file x?"))
        assertNotNull(sink.pendingConfirm.value)
        assertEquals("c1", sink.pendingConfirm.value?.id)

        sink.handle(session, ServerEvent.ConfirmTimeout("c1"))
        assertNull("stale prompt must not linger", sink.pendingConfirm.value)
    }

    @Test
    fun `a timeout for a different prompt does not clear the live one`() = runTest {
        sink.handle(session, ServerEvent.ConfirmRequest("c1", "first"))
        sink.handle(session, ServerEvent.ConfirmTimeout("c2"))
        assertEquals("c1", sink.pendingConfirm.value?.id)
    }

    @Test
    fun `thinking clears once real output arrives`() = runTest {
        sink.handle(session, ServerEvent.Thinking)
        assertTrue(sink.thinking.value)

        sink.handle(session, ServerEvent.Text("hi"))
        assertFalse(sink.thinking.value)
    }

    @Test
    fun `error ends the stream and records the message`() = runTest {
        sink.handle(session, ServerEvent.Text("partial"))
        sink.handle(session, ServerEvent.Error("provider exploded"))

        val streamed = repo.store.values.first { it.role == MessageRole.ASSISTANT }
        assertFalse("partial text must not stay streaming", streamed.isStreaming)

        val err = repo.store.values.first { it.errorMessage != null }
        assertEquals("provider exploded", err.errorMessage)
    }

    @Test
    fun `disconnect finalises a half-written message`() = runTest {
        sink.handle(session, ServerEvent.Text("half"))
        sink.onDisconnected()

        val row = repo.store.values.single()
        assertEquals("half", row.content)
        assertFalse(row.isStreaming)
        assertNull(sink.pendingConfirm.value)
    }

    @Test
    fun `context health is exposed without touching the database`() = runTest {
        sink.handle(
            session,
            ServerEvent.ContextHealth(1200, 200_000, 0.6, 0.0021, 4, dashboard = false),
        )
        assertEquals(1200, sink.contextHealth.value?.estimatedTokens)
        assertTrue("health must not be persisted", repo.store.isEmpty())
    }

    @Test
    fun `unknown events are ignored`() = runTest {
        sink.handle(session, ServerEvent.Unknown("some_future_event"))
        assertTrue(repo.store.isEmpty())
    }

    @Test
    fun `artifact event creates a system message with metadata`() = runTest {
        sink.handle(
            session,
            ServerEvent.Artifact(
                id = "a1",
                name = "index.html",
                content = "<h1>Hello</h1>",
                contentType = "text/html",
            ),
        )

        assertEquals(1, repo.store.size)
        val msg = repo.store.values.single()
        assertEquals(MessageRole.SYSTEM, msg.role)
        assertEquals("index.html", msg.content)
        assertNotNull(msg.metadata)
        val payload = Gson().fromJson(msg.metadata, dev.aura.auradroid.data.repository.ArtifactPayload::class.java)
        assertEquals("artifact", payload.type)
        assertEquals("a1", payload.id)
        assertEquals("index.html", payload.name)
        assertEquals("<h1>Hello</h1>", payload.content)
        assertEquals("text/html", payload.contentType)
    }
}

/**
 * In-memory stand-in for AuraRepository. Room is not available in a JVM unit
 * test, and what these tests assert is the folding logic, not persistence.
 */
private class FakeRepository : MessageStore {
    val store = linkedMapOf<Long, Message>()
    val inserted = mutableListOf<Message>()
    var updates = 0
    private var nextId = 1L

    override suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        isStreaming: Boolean,
        toolCalls: String?,
        metadata: String?,
        errorMessage: String?,
    ): Message {
        val m = Message(
            id = nextId++,
            sessionId = sessionId,
            role = role,
            content = content,
            isStreaming = isStreaming,
            toolCalls = toolCalls,
            metadata = metadata,
            errorMessage = errorMessage,
        )
        store[m.id] = m
        inserted += m
        return m
    }

    override suspend fun getMessage(messageId: Long): Message? = store[messageId]

    override suspend fun updateMessage(message: Message) {
        store[message.id] = message
        updates++
    }

    override suspend fun updateStreamingMessage(
        messageId: Long,
        content: String,
        isStreaming: Boolean,
    ) {
        store[messageId]?.let { store[messageId] = it.copy(content = content, isStreaming = isStreaming) }
        updates++
    }
}
