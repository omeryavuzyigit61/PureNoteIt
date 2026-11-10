package com.notes.purenoteit

import android.content.Context
import app.cash.turbine.test
import com.notes.purenoteit.data.repository.OfflineCodeExecutionRepositoryImpl
import com.notes.purenoteit.data.runner.JsScriptWrapper
import com.notes.purenoteit.data.runner.SqlStatementSplitter
import com.notes.purenoteit.data.runner.SqlValidationResult
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.CodeLanguageCapabilities
import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.model.JsonRootType
import com.notes.purenoteit.domain.model.OfflineExecutionCapability
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.domain.usecase.ExecuteOfflineCodeUseCase
import com.notes.purenoteit.domain.usecase.StaticSyntaxChecker
import com.notes.purenoteit.domain.usecase.SyntaxIssueType
import com.notes.purenoteit.ui.mvi.CodePlaygroundUiEffect
import com.notes.purenoteit.ui.mvi.CodePlaygroundUiEvent
import com.notes.purenoteit.viewmodel.CodePlaygroundViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineCodeExecutionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = AppClock { 1_700_000_000_000L }
    private lateinit var mockContext: Context
    private lateinit var repository: OfflineCodeExecutionRepositoryImpl
    private lateinit var useCase: ExecuteOfflineCodeUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk(relaxed = true)
        repository = OfflineCodeExecutionRepositoryImpl(
            context = mockContext,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            clock = fixedClock
        )
        useCase = ExecuteOfflineCodeUseCase(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dil kapasite haritasi offline calisabilen ve yalnizca sozdizimi kontrolu yapilan dilleri ayirt etmelidir`() {
        assertEquals(OfflineExecutionCapability.SQL_IN_MEMORY, CodeLanguageCapabilities.capabilityFor("sql"))
        assertEquals(OfflineExecutionCapability.JAVASCRIPT_SANDBOX, CodeLanguageCapabilities.capabilityFor("javascript"))
        assertEquals(OfflineExecutionCapability.HTML_PREVIEW, CodeLanguageCapabilities.capabilityFor("HTML"))
        assertEquals(OfflineExecutionCapability.JSON_FORMAT_VALIDATE, CodeLanguageCapabilities.capabilityFor("json"))
        assertEquals(OfflineExecutionCapability.SYNTAX_CHECK_ONLY, CodeLanguageCapabilities.capabilityFor("kotlin"))
        assertEquals(OfflineExecutionCapability.SYNTAX_CHECK_ONLY, CodeLanguageCapabilities.capabilityFor("python"))

        assertTrue(CodeLanguageCapabilities.isFullyExecutableOffline("sql"))
        assertFalse(CodeLanguageCapabilities.isFullyExecutableOffline("kotlin"))
        assertNotNull(CodeLanguageCapabilities.externalPlaygroundUrl("kotlin"))
        assertNull(CodeLanguageCapabilities.externalPlaygroundUrl("sql"))
    }

    @Test
    fun `SqlStatementSplitter tirnak ve yorum icindeki noktali virgulleri bolmemeli ve tehlikeli ifadeleri engellemelidir`() {
        val script = """
            -- Yorum; yok sayilmali
            CREATE TABLE items (id INT, note TEXT);
            INSERT INTO items VALUES (1, 'Merhaba; Dunya');
            SELECT * FROM items;
        """.trimIndent()

        val result = SqlStatementSplitter.parseAndValidate(script)
        assertTrue(result is SqlValidationResult.Valid)
        val valid = result as SqlValidationResult.Valid
        assertEquals(3, valid.statements.size)
        assertFalse(valid.statements[0].returnsRows)
        assertFalse(valid.statements[1].returnsRows)
        assertTrue(valid.statements[2].returnsRows)

        // String sabiti icindeki 'attach' engellenmemeli, gercek ATTACH komutu engellenmeli
        val safeString = SqlStatementSplitter.parseAndValidate("SELECT 'attach ok' AS msg;")
        assertTrue(safeString is SqlValidationResult.Valid)

        val blockedAttach = SqlStatementSplitter.parseAndValidate("ATTACH DATABASE '/data/local.db' AS hacked;")
        assertTrue(blockedAttach is SqlValidationResult.Blocked)
        assertEquals("ATTACH", (blockedAttach as SqlValidationResult.Blocked).keyword)

        val blockedExt = SqlStatementSplitter.parseAndValidate("SELECT load_extension('evil.so');")
        assertTrue(blockedExt is SqlValidationResult.Blocked)
    }

    @Test
    fun `StaticSyntaxChecker yorum ve metin sabitlerindeki parantezleri yoksaymali gercek hatalari yakalamalidir`() {
        val validKotlin = """
            fun greet(name: String) {
                val msg = "Merhaba (dünya { [" // Yorumda kapanmamış {
                println(msg)
            }
        """.trimIndent()
        val okReport = StaticSyntaxChecker.analyze(validKotlin, "kotlin")
        assertTrue(okReport.isValid)
        assertEquals(4, okReport.lineCount)

        val mismatched = """
            def test():
                arr = [1, 2)
        """.trimIndent()
        val badReport = StaticSyntaxChecker.analyze(mismatched, "python")
        assertFalse(badReport.isValid)
        assertEquals(SyntaxIssueType.MISMATCHED_DELIMITER, badReport.issueType)
        assertEquals(2, badReport.issueLine)
        assertEquals(']', badReport.expectedToken)
        assertEquals(')', badReport.actualToken)
    }

    @Test
    fun `JsScriptWrapper konsol ciktilarini donus degerini ve hatalari deterministik olarak ayristirmalidir`() {
        val harness = JsScriptWrapper.buildHarnessScript("console.log('Merhaba'); 6 * 7;")
        assertTrue(harness.contains("eval"))

        val chromiumRawOutput = "\"{\\\"logs\\\":[{\\\"level\\\":\\\"log\\\",\\\"text\\\":\\\"Merhaba\\\"},{\\\"level\\\":\\\"warn\\\",\\\"text\\\":\\\"Dikkat\\\"}],\\\"returnValue\\\":\\\"42\\\",\\\"hasReturnValue\\\":true,\\\"error\\\":null}\""
        val parsed = JsScriptWrapper.parseEvaluationResult(chromiumRawOutput)

        assertNull(parsed.errorMessage)
        assertEquals(3, parsed.consoleLines.size)
        assertEquals("Merhaba", parsed.consoleLines[0].text)
        assertEquals(ConsoleLevel.OUTPUT, parsed.consoleLines[0].level)
        assertEquals("Dikkat", parsed.consoleLines[1].text)
        assertEquals(ConsoleLevel.WARN, parsed.consoleLines[1].level)
        assertEquals("← 42", parsed.consoleLines[2].text)
        assertEquals(ConsoleLevel.INFO, parsed.consoleLines[2].level)
    }

    @Test
    fun `JSON calistirma gecerli JSONu bicimlendirmeli hatali JSONda Failure donmelidir`() = runTest(testDispatcher) {
        val valid = useCase("json", """{"app":"PureNoteIt","items":[1,2,3]}""")
        assertTrue(valid is AppResult.Success)
        val jsonOut = (valid as AppResult.Success).data as CodeExecutionOutput.JsonResult
        assertEquals(JsonRootType.OBJECT, jsonOut.rootType)
        assertEquals(2, jsonOut.itemCount)
        assertTrue(jsonOut.formattedJson.contains("\n"))

        val invalid = useCase("json", """{"app": [1, 2}""")
        assertTrue(invalid is AppResult.Failure)
        assertTrue((invalid as AppResult.Failure).error is CodeExecutionError.RuntimeOrSyntax)
    }

    @Test
    fun `bos kod ve HTML onizleme dogru AppResult donmelidir`() = runTest(testDispatcher) {
        val emptyRes = useCase("sql", "   ")
        assertEquals(AppResult.Failure(CodeExecutionError.EmptyCode), emptyRes)

        val htmlRes = useCase("html", "<h1>PureNoteIt</h1>")
        assertTrue(htmlRes is AppResult.Success)
        val preview = (htmlRes as AppResult.Success).data as CodeExecutionOutput.HtmlPreview
        assertEquals("<h1>PureNoteIt</h1>", preview.htmlContent)
    }

    @Test
    fun `CodePlaygroundViewModel UI durumunu ve harici Playground efektlerini reaktif yonetmelidir`() = runTest(testDispatcher) {
        val viewModel = CodePlaygroundViewModel(useCase)

        viewModel.onEvent(
            CodePlaygroundUiEvent.RunCode(
                blockId = "blk-1",
                language = "kotlin",
                code = "fun main() { println(1 + 2) }"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value.blockStates["blk-1"]
        assertNotNull(state)
        assertFalse(state!!.isRunning)
        assertTrue(state.output is CodeExecutionOutput.SyntaxCheckPassed)

        viewModel.effects.test {
            viewModel.requestExternalPlayground("kotlin")
            val effect = awaitItem()
            assertTrue(effect is CodePlaygroundUiEffect.OpenExternalPlayground)
            assertEquals("https://play.kotlinlang.org/", (effect as CodePlaygroundUiEffect.OpenExternalPlayground).url)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEvent(CodePlaygroundUiEvent.ClearOutput("blk-1"))
        assertNull(viewModel.uiState.value.blockStates["blk-1"])
    }
}
