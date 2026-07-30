package dev.aura.auradroid.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlocksTest {

    @Test
    fun `an html page becomes index html`() {
        val reply = """
            Here is the page:

            ```html
            <!DOCTYPE html>
            <html>
            <body><h1>Hi</h1></body>
            </html>
            ```
        """.trimIndent()

        val blocks = CodeBlocks.extract(reply)
        assertEquals(1, blocks.size)
        // A browser expects index.html, and that is nearly always what a
        // single HTML reply is.
        assertEquals("index.html", blocks[0].fileName)
        assertTrue(blocks[0].code.contains("<h1>Hi</h1>"))
    }

    @Test
    fun `an unlabelled fence is still recognised as html`() {
        val reply = "```\n<!doctype html>\n<html>\n<body>x</body>\n</html>\n```"
        assertEquals("index.html", CodeBlocks.extract(reply)[0].fileName)
    }

    @Test
    fun `a one-line fence is not offered as a file`() {
        // Inline commands are shown constantly; a Save button on each would be
        // noise around the thing the user actually wants.
        assertEquals(0, CodeBlocks.extract("run ```npm test``` now").size)
        assertEquals(0, CodeBlocks.extract("```sh\nnpm test\n```").size)
    }

    @Test
    fun `several blocks get distinct names`() {
        val reply = "```css\na{}\nb{}\nc{}\n```\ntext\n```js\nlet a\nlet b\nlet c\n```"
        val blocks = CodeBlocks.extract(reply)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].fileName.endsWith(".css"))
        assertTrue(blocks[1].fileName.endsWith(".js"))
        assertTrue(blocks[0].fileName != blocks[1].fileName)
    }

    @Test
    fun `language maps to the extension that opens it`() {
        fun ext(lang: String): String =
            CodeBlocks.extract("```$lang\nl1\nl2\nl3\n```")[0].fileName.substringAfterLast('.')
        assertEquals("py", ext("python"))
        assertEquals("kt", ext("kotlin"))
        assertEquals("sh", ext("bash"))
        assertEquals("ts", ext("typescript"))
        assertEquals("json", ext("json"))
    }

    @Test
    fun `an unknown language falls back to txt rather than being dropped`() {
        val blocks = CodeBlocks.extract("```brainfuck\n+++\n---\n>>>\n```")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].fileName.endsWith(".txt"))
    }

    @Test
    fun `prose with no fences yields nothing`() {
        assertEquals(0, CodeBlocks.extract("Just an ordinary reply about `npm`.").size)
    }

    @Test
    fun `mime type follows the extension`() {
        assertEquals("text/html", CodeBlocks.mimeFor("index.html"))
        assertEquals("application/json", CodeBlocks.mimeFor("a.json"))
        assertEquals("text/plain", CodeBlocks.mimeFor("notes"))
    }
}
