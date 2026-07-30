package dev.aura.auradroid.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNamerTest {

    @Test
    fun `the subject of the sentence becomes the title`() {
        assertEquals("Gradle Build", SessionNamer.titleFor("the gradle build keeps failing"))
    }

    @Test
    fun `the opening verb is not the title`() {
        // Every request to an assistant starts with one of these, so a title
        // made of them tells the user nothing about which chat is which.
        val title = SessionNamer.titleFor("fix the login screen for me please")
        assertTrue("got `$title`", "fix" !in title.lowercase())
        assertTrue("got `$title`", "login" in title.lowercase())
    }

    @Test
    fun `an identifier keeps the shape it was written in`() {
        // Not title-cased and not lowercased: `Aurasocket` names nothing.
        assertTrue(SessionNamer.titleFor("AuraSocket drops the connection").startsWith("AuraSocket"))
        assertTrue("build.gradle" in SessionNamer.titleFor("update build.gradle to sdk 35"))
    }

    @Test
    fun `the title reads as a phrase, not two loose words`() {
        // "Gradle Keeps" was what independent scoring produced here: two
        // high-scoring words that never belonged next to each other.
        assertEquals("Camera Preview", SessionNamer.titleFor("the camera preview freezes"))
        assertEquals("Login Screen", SessionNamer.titleFor("fix the login screen for me"))
    }

    @Test
    fun `a repeated word wins over one said once`() {
        val title = SessionNamer.titleFor(
            "camera is broken. the camera preview freezes when the shutter runs",
        )
        assertTrue("got `$title`", "camera" in title.lowercase())
    }

    @Test
    fun `serbian filler words are dropped`() {
        // The phone is dictated to in Serbian, and untrimmed this titled itself
        // from whichever pronoun came first.
        val title = SessionNamer.titleFor("hocu da napravim aplikaciju za recepte")
        assertTrue("got `$title`", "hocu" !in title.lowercase())
        assertTrue("got `$title`", "aplikaciju" in title.lowercase() || "recepte" in title.lowercase())
    }

    @Test
    fun `a pasted stack trace does not decide the title`() {
        // The message is about the sentence around the paste, not about
        // whichever symbol repeats most inside it.
        val title = SessionNamer.titleFor(
            """
            the checkout screen crashes

            ```
            java.lang.NullPointerException at Checkout.kt:42
            at Checkout.render(Checkout.kt:42)
            at Checkout.render(Checkout.kt:42)
            at Checkout.render(Checkout.kt:42)
            ```
            """.trimIndent(),
        )
        assertTrue("got `$title`", "NullPointerException" !in title)
        assertTrue("got `$title`", "checkout" in title.lowercase())
    }

    @Test
    fun `never more than the requested number of words`() {
        val title = SessionNamer.titleFor(
            "I want to design a distributed scheduling service with retries and backoff",
        )
        assertTrue("got `$title`", title.split(" ").size <= 2)
    }

    @Test
    fun `one word is allowed when there is only one`() {
        assertEquals("Kotlin", SessionNamer.titleFor("kotlin"))
    }

    @Test
    fun `nothing to go on stays untitled`() {
        assertEquals(SessionNamer.UNTITLED, SessionNamer.titleFor(""))
        assertEquals(SessionNamer.UNTITLED, SessionNamer.titleFor("   "))
        // All stop words: there is no honest title in here.
        assertEquals(SessionNamer.UNTITLED, SessionNamer.titleFor("can you do it for me"))
    }

    @Test
    fun `a url is not a title`() {
        val title = SessionNamer.titleFor("summarise https://example.com/very/long/path for me")
        assertTrue("got `$title`", "https" !in title.lowercase())
        assertTrue("got `$title`", "example" !in title.lowercase())
    }

    @Test
    fun `an initialism survives the minimum length rule`() {
        val title = SessionNamer.titleFor("the API returns 500 on upload")
        assertTrue("got `$title`", "API" in title)
    }

    @Test
    fun `words keep the order they were said in`() {
        // "Recipe App", not "App Recipe" — a title that reorders the sentence
        // reads as a bag of words rather than as a name.
        val title = SessionNamer.titleFor("recipe app with offline storage, recipe first")
        assertEquals("Recipe App", title)
    }
}
