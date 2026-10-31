package dev.aura.auradroid.data.standalone

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolMarkupFilterTest {
    private fun run(vararg chunks: String): String {
        val f = ToolMarkupFilter()
        return chunks.joinToString("") { f.feed(it) } + f.flush()
    }

    @Test fun plainTextPassesThrough() =
        assertEquals("a < b and x<y", run("a < b ", "and x<y"))

    @Test fun dropsMarkupFromMarkerOn() =
        assertEquals("Sure.\n", run("Sure.\n<｜DSML｜function_calls><｜DSML｜invoke name=\"x\">"))

    @Test fun markerSplitAcrossChunks() =
        assertEquals("ok ", run("ok <", "｜DS", "ML｜function_calls>rest", "more"))

    @Test fun spacedVariant() =
        assertEquals("hi", run("hi< | DSML | calls>"))

    @Test fun trailingLoneAngleFlushed() = assertEquals("x <", run("x <"))
}
