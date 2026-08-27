package dev.aura.auradroid.data.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PathGuard decides whether a file-tool path leaves the workspace. The
 * sandbox-out gate turns on approval for exactly these paths, so the ones
 * that must be true here are the ones a model would try: absolute paths and
 * `..` traversal, on both slash styles.
 */
class PathGuardTest {

    @Test
    fun `blank path stays inside`() = assertFalse(PathGuard.escapes(""))

    @Test
    fun `simple relative path stays inside`() = assertFalse(PathGuard.escapes("notes.md"))

    @Test
    fun `nested relative path stays inside`() = assertFalse(PathGuard.escapes("src/main.kt"))

    @Test
    fun `absolute path escapes`() = assertTrue(PathGuard.escapes("/storage/emulated/0/Download"))

    @Test
    fun `parent traversal escapes`() = assertTrue(PathGuard.escapes("../databases"))

    @Test
    fun `nested parent traversal escapes`() = assertTrue(PathGuard.escapes("src/../../secret"))

    @Test
    fun `windows style parent traversal escapes`() = assertTrue(PathGuard.escapes("..\\secret"))

    @Test
    fun `three dots is a filename, not traversal`() = assertFalse(PathGuard.escapes(".../weird"))
}
