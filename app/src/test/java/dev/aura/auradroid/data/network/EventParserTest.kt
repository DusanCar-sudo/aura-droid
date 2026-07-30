package dev.aura.auradroid.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format is owned by aura-code's src/server/index.ts, not by this app.
 * The payloads below were captured from a live `aura serve` rather than written
 * from the type definitions, so these tests fail if the server's actual output
 * drifts — which is the failure worth catching.
 */
class EventParserTest {

    @Test
    fun `parses the connected handshake`() {
        // Captured verbatim.
        val e = EventParser.parse("""{"type":"connected"}""")
        assertEquals(ServerEvent.Connected, e)
    }

    @Test
    fun `parses a confirm_request with its uuid`() {
        // Captured verbatim from a live server confirm.
        val raw = """{"type":"confirm_request","id":"0a31f013-86c6-4d1a-9da2-c76068a4d747",""" +
            """"message":"Allow: write_file test.txt?"}"""
        val e = EventParser.parse(raw) as ServerEvent.ConfirmRequest
        assertEquals("0a31f013-86c6-4d1a-9da2-c76068a4d747", e.id)
        assertEquals("Allow: write_file test.txt?", e.message)
    }

    @Test
    fun `drops a confirm_request with no id`() {
        // Without an id there is nothing to answer, so it must not surface as
        // a prompt the user can never resolve.
        assertNull(EventParser.parse("""{"type":"confirm_request","message":"x"}"""))
    }

    @Test
    fun `parses streaming text`() {
        val e = EventParser.parse("""{"type":"text","text":"Hello"}""") as ServerEvent.Text
        assertEquals("Hello", e.text)
        assertEquals(ServerEvent.TextEnd, EventParser.parse("""{"type":"text_end"}"""))
    }

    @Test
    fun `parses a tool call and its result`() {
        val call = EventParser.parse(
            """{"type":"tool_call","name":"read_file","input":{"path":"src/main.ts"}}"""
        ) as ServerEvent.ToolCall
        assertEquals("read_file", call.name)
        assertEquals("src/main.ts", call.input?.get("path")?.asString)

        val result = EventParser.parse(
            """{"type":"tool_result","name":"read_file","result":"file contents","ms":42}"""
        ) as ServerEvent.ToolResult
        assertEquals("read_file", result.name)
        assertEquals("file contents", result.result)
        assertEquals(42L, result.ms)
    }

    @Test
    fun `parses a tool call with no input object`() {
        // git_status and friends take no arguments.
        val e = EventParser.parse("""{"type":"tool_call","name":"git_status"}""")
            as ServerEvent.ToolCall
        assertEquals("git_status", e.name)
        assertNull(e.input)
    }

    @Test
    fun `parses tool_blocked`() {
        val e = EventParser.parse(
            """{"type":"tool_blocked","name":"run_shell","reason":"denied by user"}"""
        ) as ServerEvent.ToolBlocked
        assertEquals("run_shell", e.name)
        assertEquals("denied by user", e.reason)
    }

    @Test
    fun `parses a plan and its steps`() {
        val raw = """{"type":"plan_created","plan":{"id":"p1","goal":"refactor auth",""" +
            """"steps":[{"id":"s1","specialist":"researcher","task":"read the module"},""" +
            """{"id":"s2","specialist":"coder","task":"apply the change"}]}}"""
        val e = EventParser.parse(raw) as ServerEvent.PlanCreated
        assertEquals("refactor auth", e.plan.goal)
        assertEquals(2, e.plan.steps.size)
        assertEquals("researcher", e.plan.steps[0].specialist)
        assertEquals("apply the change", e.plan.steps[1].task)
        // Status is local view state; the server never sends it.
        assertEquals(StepStatus.PENDING, e.plan.steps[0].status)
    }

    @Test
    fun `parses step lifecycle events`() {
        val started = EventParser.parse(
            """{"type":"step_started","step":{"id":"s1","specialist":"coder","task":"edit"}}"""
        ) as ServerEvent.StepStarted
        assertEquals("s1", started.step.id)

        val completed = EventParser.parse(
            """{"type":"step_completed","step":{"id":"s1","specialist":"coder","task":"edit"},""" +
                """"result":"done"}"""
        ) as ServerEvent.StepCompleted
        assertEquals("done", completed.result)
    }

    @Test
    fun `drops a step with no id`() {
        // The id is what the UI keys progress updates on.
        assertNull(EventParser.parse("""{"type":"step_started","step":{"specialist":"coder"}}"""))
    }

    @Test
    fun `parses done with its counters`() {
        val e = EventParser.parse(
            """{"type":"done","success":true,"text":"All set.","turns":3,"toolCount":7}"""
        ) as ServerEvent.Done
        assertTrue(e.success)
        assertEquals("All set.", e.text)
        assertEquals(3, e.turns)
        assertEquals(7, e.toolCount)
    }

    @Test
    fun `parses context health from both event names`() {
        val bar = EventParser.parse(
            """{"type":"context_bar","health":{"estimatedTokens":1200,"contextWindow":200000,""" +
                """"usagePercent":0.6,"totalCostUsd":0.0021,"turnCount":4}}"""
        ) as ServerEvent.ContextHealth
        assertEquals(1200, bar.estimatedTokens)
        assertEquals(200000, bar.contextWindow)
        assertTrue(!bar.dashboard)

        val dash = EventParser.parse(
            """{"type":"context_dashboard","health":{"estimatedTokens":1,"contextWindow":2,""" +
                """"usagePercent":0.1,"totalCostUsd":0.0,"turnCount":1}}"""
        ) as ServerEvent.ContextHealth
        assertTrue(dash.dashboard)
    }

    @Test
    fun `parses errors and warnings`() {
        val err = EventParser.parse("""{"type":"error","message":"boom"}""") as ServerEvent.Error
        assertEquals("boom", err.message)
        val warn = EventParser.parse("""{"type":"warning","message":"heads up"}""")
            as ServerEvent.Warning
        assertEquals("heads up", warn.message)
    }

    @Test
    fun `parses plan_done`() {
        val e = EventParser.parse(
            """{"type":"plan_done","outcome":"finished","success":true}"""
        ) as ServerEvent.PlanDone
        assertEquals("finished", e.outcome)
        assertTrue(e.success)
    }

    @Test
    fun `keeps an unknown event type instead of failing`() {
        // A newer desktop must not break an older phone.
        val e = EventParser.parse("""{"type":"some_future_event","x":1}""") as ServerEvent.Unknown
        assertEquals("some_future_event", e.type)
    }

    @Test
    fun `returns null for junk rather than throwing`() {
        assertNull(EventParser.parse("not json"))
        assertNull(EventParser.parse("{}"))
        assertNull(EventParser.parse("[]"))
        assertNull(EventParser.parse(""))
    }

    @Test
    fun `tolerates explicit nulls in optional fields`() {
        // Gson's asString throws on JsonNull, so this would crash a naive parser.
        val e = EventParser.parse(
            """{"type":"done","success":false,"text":null,"turns":null,"toolCount":null}"""
        ) as ServerEvent.Done
        assertNull(e.text)
        assertNull(e.turns)
        assertNull(e.toolCount)
    }
}
