package dev.aura.auradroid.data.standalone

/**
 * Some models (DeepSeek among them) sometimes write their tool calls into the
 * message text as `<｜DSML｜function_calls>…` instead of using the tool_calls
 * field. That is never something to show: everything from the marker onward is
 * dropped for the rest of the turn. The marker can arrive split across stream
 * chunks, so a tail that might still turn into one is held back until it can't.
 */
class ToolMarkupFilter {
    private val buffer = StringBuilder()
    private var suppressed = false

    fun feed(text: String): String {
        if (suppressed) return ""
        buffer.append(text)
        val s = buffer.toString()
        MARKER.find(s)?.let {
            suppressed = true
            buffer.clear()
            return s.substring(0, it.range.first)
        }
        val lt = s.lastIndexOf('<')
        val keepFrom = if (lt >= 0 && PARTIAL.matches(s.substring(lt))) lt else s.length
        buffer.setLength(0)
        buffer.append(s, keepFrom, s.length)
        return s.substring(0, keepFrom)
    }

    /** End of turn: whatever was held back turned out to be ordinary text. */
    fun flush(): String {
        if (suppressed) return ""
        val rest = buffer.toString()
        buffer.clear()
        return rest
    }

    private companion object {
        val MARKER = Regex("""<\s*[|｜]\s*DSML""")
        val PARTIAL = Regex("""<\s*[|｜]?\s*D?S?M?""")
    }
}
