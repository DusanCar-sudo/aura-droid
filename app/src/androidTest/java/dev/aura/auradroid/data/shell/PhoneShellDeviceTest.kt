package dev.aura.auradroid.data.shell

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneShellDeviceTest {

    private lateinit var shell: PhoneShell

    @Before
    fun setUp() {
        shell = PhoneShell(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testNonElevatedShell() = runBlocking {
        val res = shell.run("whoami", elevated = false)
        assertEquals(0, res.exitCode)
        assertTrue(res.output.isNotBlank())
        assertFalse(res.output.contains("root"))
    }

    @Test
    fun testElevatedShellId() = runBlocking {
        val res = shell.run("id", elevated = true)
        assertEquals("Failed to run id. Output: [${res.output}]", 0, res.exitCode)
        assertTrue("Expected uid=2000 in output but got: ${res.output}", res.output.contains("uid=2000"))
    }

    @Test
    fun testElevatedShellPmList() = runBlocking {
        val res = shell.run("pm list packages -3", elevated = true)
        assertEquals("Failed pm list. Output: [${res.output}]", 0, res.exitCode)
        assertTrue(res.output.contains("package:"))
    }

    @Test
    fun testElevatedShellSettings() = runBlocking {
        val res = shell.run("settings get global airplane_mode_on", elevated = true)
        assertEquals("Failed settings. Output: [${res.output}]", 0, res.exitCode)
        assertTrue(res.output.trim() in listOf("0", "1"))
    }

    @Test
    fun testElevatedShellDumpsys() = runBlocking {
        val res = shell.run("dumpsys battery", elevated = true)
        assertEquals("Failed dumpsys. Output: [${res.output}]", 0, res.exitCode)
        assertTrue(res.output.contains("level:") || res.output.contains("Current Battery Service state"))
    }

    @Test
    fun testElevatedShellSdcardAccess() = runBlocking {
        val res = shell.run("ls /sdcard/", elevated = true)
        assertEquals("Failed ls /sdcard/. Output: [${res.output}]", 0, res.exitCode)
        assertTrue(res.output.isNotBlank())
    }

    @Test
    fun testRefusedCommands() = runBlocking {
        val res1 = shell.run("cat aura_database", elevated = true)
        assertEquals(126, res1.exitCode)
        assertTrue(res1.output.contains("refused"))

        val res2 = shell.run("cat aura_pairing", elevated = true)
        assertEquals(126, res2.exitCode)
        assertTrue(res2.output.contains("refused"))

        val res3 = shell.run("rm -rf /sdcard/test", elevated = true)
        assertEquals(126, res3.exitCode)
        assertTrue(res3.output.contains("refused"))
    }
}
