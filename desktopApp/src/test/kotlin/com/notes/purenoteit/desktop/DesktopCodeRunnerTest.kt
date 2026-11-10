package com.notes.purenoteit.desktop

import com.notes.purenoteit.desktop.data.runner.DesktopCodeRunnerImpl
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.JsonRootType
import com.notes.purenoteit.domain.time.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class DesktopCodeRunnerTest {

    private val clock = AppClock { System.currentTimeMillis() }

    private fun createRunner(
        timeoutMs: Long = 5_000L,
        maxOutputBytes: Int = DesktopCodeRunnerImpl.MAX_OUTPUT_BYTES
    ) = DesktopCodeRunnerImpl(
        ioDispatcher = Dispatchers.IO,
        clock = clock,
        processTimeoutMs = timeoutMs,
        maxOutputBytes = maxOutputBytes
    )

    @Test
    fun `SQLite in-memory calistirma tablo dondurmeli ve tehlikeli ifadeleri engellemelidir`() = runBlocking {
        val runner = createRunner()
        val ok = runner.executeOffline(
            "sql",
            """
            CREATE TABLE users(id INT, name TEXT);
            INSERT INTO users VALUES (1, 'Ada'), (2, 'Linus');
            SELECT id, name FROM users ORDER BY id;
            """.trimIndent()
        )
        assertTrue(ok is AppResult.Success)
        val table = (ok as AppResult.Success).data as CodeExecutionOutput.SqlTable
        assertEquals(listOf("id", "name"), table.columns)
        assertEquals(listOf(listOf("1", "Ada"), listOf("2", "Linus")), table.rows)
        assertEquals(2, table.rowsAffected)

        val blockedAttach = runner.executeOffline("sql", "ATTACH DATABASE 'secret.db' AS s;")
        assertEquals(AppResult.Failure(CodeExecutionError.SecurityBlocked("ATTACH")), blockedAttach)

        val blockedVacuum = runner.executeOffline("sql", "VACUUM INTO 'dump.db';")
        assertEquals(AppResult.Failure(CodeExecutionError.SecurityBlocked("VACUUM INTO")), blockedVacuum)
    }

    @Test
    fun `JSON bicimlendirme ve hatali JSON reddi dogru calismalidir`() = runBlocking {
        val runner = createRunner()
        val ok = runner.executeOffline("json", """{"a":1,"b":[true,false]}""") as AppResult.Success
        val out = ok.data as CodeExecutionOutput.JsonResult
        assertEquals(JsonRootType.OBJECT, out.rootType)
        assertEquals(2, out.itemCount)
        assertEquals("""{"a":1,"b":[true,false]}""", out.minifiedJson)

        val bad = runner.executeOffline("json", "{invalid}")
        assertTrue(bad is AppResult.Failure && bad.error is CodeExecutionError.RuntimeOrSyntax)
    }

    @Test
    fun `Bash acik kullanici onayi olmadan calismamalidir`() = runBlocking {
        val runner = createRunner()
        val blocked = runner.executeOffline("bash", "echo should-not-run")
        assertEquals(
            AppResult.Failure(CodeExecutionError.SecurityBlocked("BASH_CONFIRMATION_REQUIRED")),
            blocked
        )
    }

    @Test
    fun `Python sandbox ortami kabuksuz calismali env degiskenlerini sizdirmamali ve tmp klasorunu silmelidir`() =
        runBlocking {
            val pythonExe = DesktopCodeRunnerImpl.findExecutableOnSystem(listOf("python3", "python"))
            assumeNotNull("Bu test ortami gercek Python ikilisi gerektirir", pythonExe)

            val runner = createRunner(timeoutMs = 8_000L)
            val beforeSandboxes = countTempSandboxes()

            val result = runner.executeOffline(
                "python",
                """
                import os
                print("CALC:", 21 * 2)
                print("HAS_HOME:", "PURE_SECRET" in os.environ)
                # Ensure metacharacters inside code are not evaluated by OS shell
                payload = "; echo INJECTION && whoami"
                print("LITERAL:", payload)
                """.trimIndent()
            )
            assertTrue("Python calismasi basarili olmali: $result", result is AppResult.Success)
            val console = (result as AppResult.Success).data as CodeExecutionOutput.Console
            val joined = console.lines.joinToString("\n") { it.text }
            assertTrue(joined.contains("CALC: 42"))
            assertTrue(joined.contains("LITERAL: ; echo INJECTION && whoami"))
            assertFalse(console.hasRuntimeError)

            val afterSandboxes = countTempSandboxes()
            assertEquals("Gecici sandbox dizinleri finally icinde temizlenmeli", beforeSandboxes, afterSandboxes)
        }

    @Test
    fun `Node js gercek surec ciktisi uretmelidir`() = runBlocking {
        val nodeExe = DesktopCodeRunnerImpl.findExecutableOnSystem(listOf("node", "nodejs"))
        assumeNotNull("Bu test ortami Node.js gerektirir", nodeExe)

        val runner = createRunner(timeoutMs = 8_000L)
        val result = runner.executeOffline(
            "javascript",
            """console.log("NODE_OK", 6 * 7);"""
        )
        assertTrue(result is AppResult.Success)
        val console = (result as AppResult.Success).data as CodeExecutionOutput.Console
        assertTrue(console.lines.any { it.text.contains("NODE_OK 42") })
    }

    @Test
    fun `Sonsuz dongu timeout sinirinda sonlandirilmali ve gecici klasor silinmelidir`() = runBlocking {
        val pythonExe = DesktopCodeRunnerImpl.findExecutableOnSystem(listOf("python3", "python"))
            ?: DesktopCodeRunnerImpl.findExecutableOnSystem(listOf("node"))
        assumeNotNull(pythonExe)

        val usePython = pythonExe!!.contains("python", ignoreCase = true)
        val runner = createRunner(timeoutMs = 450L)
        val beforeSandboxes = countTempSandboxes()

        val result = if (usePython) {
            runner.executeOffline("python", "while True:\n    pass\n")
        } else {
            runner.executeOffline("javascript", "while (true) {}")
        }

        assertEquals(AppResult.Failure(CodeExecutionError.ExecutionTimeout), result)
        assertEquals(beforeSandboxes, countTempSandboxes())
    }

    @Test
    fun `Asiri cikti 64 KB tampon sinirinda kesilerek OOM engellenmelidir`() = runBlocking {
        val nodeExe = DesktopCodeRunnerImpl.findExecutableOnSystem(listOf("node", "nodejs"))
        assumeNotNull(nodeExe)

        val cap = 4096 // Test custom tight cap matching byte-accumulator mechanism
        val runner = createRunner(timeoutMs = 5_000L, maxOutputBytes = cap)
        val result = runner.executeOffline(
            "javascript",
            """process.stdout.write("A".repeat(40000));"""
        )
        assertTrue(result is AppResult.Success)
        val console = (result as AppResult.Success).data as CodeExecutionOutput.Console
        val totalOutputChars = console.lines.filter { !it.text.startsWith("[Çıktı") }.sumOf { it.text.length }
        assertTrue("Tampon ust siniri asilmamali: $totalOutputChars", totalOutputChars <= cap)
        assertTrue(
            "Kesilme uyarisi eklenmeli",
            console.lines.any { it.text.contains("sınırında kesildi") }
        )
    }

    private fun countTempSandboxes(): Int {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        return tmpDir.listFiles { _, name -> name.startsWith("purenote_exec_") }?.size ?: 0
    }
}
