package dev.aura.auradroid.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Assistant replies are markdown. A TTS engine reads it literally — "star star
 * important star star", every backtick, and a whole function body character by
 * character — so what reaches the engine has to be prose.
 */
class SpeechTextTest {

    private val strip: Method = Class.forName("dev.aura.auradroid.data.audio.Speaker\$Companion")
        .getDeclaredMethod("strippedForSpeech", String::class.java)
        .apply { isAccessible = true }

    private val companion = Speaker::class.java
        .getDeclaredField("Companion")
        .apply { isAccessible = true }
        .get(null)

    private fun spoken(raw: String): String = strip.invoke(companion, raw) as String

    @Test
    fun `drops code blocks rather than reading them out`() {
        val out = spoken("Here is the fix:\n\n```kotlin\nval x = 1\n```\n\nThat's all.")
        assertFalse("code must not be spoken", out.contains("val x"))
        assertTrue(out.contains("code omitted"))
        assertTrue(out.contains("That's all"))
    }

    @Test
    fun `removes emphasis markers but keeps the words`() {
        val out = spoken("This is **important** and *urgent*.")
        assertFalse(out.contains("*"))
        assertTrue(out.contains("important"))
        assertTrue(out.contains("urgent"))
    }

    @Test
    fun `strips inline code fences`() {
        val out = spoken("Run `npm test` now.")
        assertFalse(out.contains("`"))
        assertTrue(out.contains("Run"))
        assertTrue(out.contains("now"))
    }

    @Test
    fun `reads link text, not the URL`() {
        val out = spoken("See [the docs](https://example.com/very/long/path).")
        assertTrue(out.contains("the docs"))
        assertFalse("a spoken URL is unusable", out.contains("example.com"))
    }

    @Test
    fun `drops heading and list markers`() {
        val out = spoken("## Summary\n\n- first\n- second")
        assertFalse(out.contains("#"))
        assertFalse(out.trimStart().startsWith("-"))
        assertTrue(out.contains("first"))
        assertTrue(out.contains("second"))
    }

    @Test
    fun `collapses blank lines into sentence breaks`() {
        // Otherwise the engine runs two paragraphs together with no pause.
        val out = spoken("One thing.\n\nAnother thing.")
        assertTrue(out.contains("One thing. Another thing") || out.contains("One thing.. Another"))
    }

    @Test
    fun `plain prose is left alone`() {
        assertEquals("Just a normal sentence.", spoken("Just a normal sentence."))
    }

    @Test
    fun `a reply that is only code produces nothing worth speaking`() {
        val out = spoken("```\nrm -rf /\n```")
        assertFalse(out.contains("rm -rf"))
    }
}
