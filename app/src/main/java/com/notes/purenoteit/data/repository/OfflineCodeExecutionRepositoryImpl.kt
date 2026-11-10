package com.notes.purenoteit.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.notes.purenoteit.data.runner.JsScriptWrapper
import com.notes.purenoteit.data.runner.SqlStatementSplitter
import com.notes.purenoteit.data.runner.SqlValidationResult
import com.notes.purenoteit.di.DefaultDispatcher
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.di.MainDispatcher
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.CodeLanguageCapabilities
import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.model.ConsoleLine
import com.notes.purenoteit.domain.model.JsonRootType
import com.notes.purenoteit.domain.model.OfflineExecutionCapability
import com.notes.purenoteit.domain.repository.CodeExecutionRepository
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.domain.usecase.StaticSyntaxChecker
import com.notes.purenoteit.domain.usecase.SyntaxIssueType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import javax.inject.Inject
import kotlin.coroutines.resume

internal class OfflineCodeExecutionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val clock: AppClock
) : CodeExecutionRepository {

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val compactJson = Json {
        prettyPrint = false
    }

    override suspend fun executeOffline(
        language: String,
        code: String
    ): AppResult<CodeExecutionOutput, CodeExecutionError> {
        if (code.isBlank()) {
            return AppResult.Failure(CodeExecutionError.EmptyCode)
        }
        return when (CodeLanguageCapabilities.capabilityFor(language)) {
            OfflineExecutionCapability.SQL_IN_MEMORY -> executeSql(code)
            OfflineExecutionCapability.JSON_FORMAT_VALIDATE -> validateAndFormatJson(code)
            OfflineExecutionCapability.HTML_PREVIEW -> prepareHtmlPreview(code)
            OfflineExecutionCapability.JAVASCRIPT_SANDBOX -> executeJavascript(code)
            OfflineExecutionCapability.SYNTAX_CHECK_ONLY -> checkStaticSyntax(language, code)
        }
    }

    private suspend fun executeSql(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> =
        withContext(ioDispatcher) {
            val startedAt = clock.nowMillis()
            val validation = SqlStatementSplitter.parseAndValidate(code)
            if (validation is SqlValidationResult.Blocked) {
                return@withContext AppResult.Failure(
                    CodeExecutionError.SecurityBlocked(validation.keyword)
                )
            }
            val statements = (validation as SqlValidationResult.Valid).statements
            if (statements.isEmpty()) {
                return@withContext AppResult.Failure(CodeExecutionError.EmptyCode)
            }

            val cancellationSignal = CancellationSignal()
            var db: SQLiteDatabase? = null
            try {
                withTimeout(SQL_TIMEOUT_MS) {
                    val memDb = SQLiteDatabase.create(null)
                    db = memDb
                    var totalAffected = 0
                    var lastColumns = emptyList<String>()
                    var lastRows = emptyList<List<String>>()
                    var truncated = false
                    var lastReturnedRows = false

                    for (stmt in statements) {
                        if (stmt.returnsRows) {
                            memDb.rawQuery(stmt.sql, null, cancellationSignal).use { cursor ->
                                val cols = cursor.columnNames.toList()
                                val rows = mutableListOf<List<String>>()
                                while (cursor.moveToNext()) {
                                    if (rows.size >= MAX_SQL_ROWS) {
                                        truncated = true
                                        break
                                    }
                                    rows += readCursorRow(cursor, cols.size)
                                }
                                lastColumns = cols
                                lastRows = rows
                                lastReturnedRows = true
                            }
                        } else {
                            memDb.execSQL(stmt.sql)
                            val changes = queryChangesCount(memDb, cancellationSignal)
                            if (changes > 0) totalAffected += changes
                            lastReturnedRows = false
                        }
                    }

                    val elapsed = (clock.nowMillis() - startedAt).coerceAtLeast(0L)
                    AppResult.Success(
                        CodeExecutionOutput.SqlTable(
                            columns = if (lastReturnedRows) lastColumns else emptyList(),
                            rows = if (lastReturnedRows) lastRows else emptyList(),
                            rowsAffected = totalAffected,
                            statementsExecuted = statements.size,
                            isTruncated = if (lastReturnedRows) truncated else false,
                            durationMs = elapsed
                        )
                    )
                }
            } catch (_: TimeoutCancellationException) {
                cancellationSignal.cancel()
                AppResult.Failure(CodeExecutionError.ExecutionTimeout)
            } catch (e: CancellationException) {
                cancellationSignal.cancel()
                throw e
            } catch (e: SQLiteException) {
                AppResult.Failure(
                    CodeExecutionError.RuntimeOrSyntax(sanitizeSqliteError(e.message))
                )
            } catch (_: Exception) {
                AppResult.Failure(
                    CodeExecutionError.RuntimeOrSyntax("SQL error")
                )
            } finally {
                runCatchingCleanly { db?.close() }
            }
        }

    private suspend fun validateAndFormatJson(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> =
        withContext(defaultDispatcher) {
            val startedAt = clock.nowMillis()
            try {
                val element = compactJson.parseToJsonElement(code)
                val formatted = prettyJson.encodeToString(element)
                val minified = compactJson.encodeToString(element)
                val (rootType, itemCount) = when (element) {
                    is JsonObject -> JsonRootType.OBJECT to element.size
                    is JsonArray -> JsonRootType.ARRAY to element.size
                    is JsonPrimitive -> JsonRootType.PRIMITIVE to 1
                }
                val elapsed = (clock.nowMillis() - startedAt).coerceAtLeast(0L)
                AppResult.Success(
                    CodeExecutionOutput.JsonResult(
                        formattedJson = formatted,
                        minifiedJson = minified,
                        rootType = rootType,
                        itemCount = itemCount,
                        durationMs = elapsed
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                AppResult.Failure(
                    CodeExecutionError.RuntimeOrSyntax(
                        message = sanitizeJsonError(e.message)
                    )
                )
            } catch (_: Exception) {
                AppResult.Failure(CodeExecutionError.RuntimeOrSyntax("Invalid JSON syntax"))
            }
        }

    private suspend fun prepareHtmlPreview(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> =
        withContext(defaultDispatcher) {
            val startedAt = clock.nowMillis()
            val trimmed = code.trim()
            if (trimmed.isEmpty()) {
                return@withContext AppResult.Failure(CodeExecutionError.EmptyCode)
            }
            val elapsed = (clock.nowMillis() - startedAt).coerceAtLeast(0L)
            AppResult.Success(
                CodeExecutionOutput.HtmlPreview(
                    htmlContent = trimmed,
                    durationMs = elapsed
                )
            )
        }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun executeJavascript(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> =
        withContext(mainDispatcher) {
            val startedAt = clock.nowMillis()
            var webView: WebView? = null
            try {
                withTimeout(JS_TIMEOUT_MS) {
                    val created = WebView(context)
                    webView = created
                    configureSandboxedWebView(created)

                    val rawResult = suspendCancellableCoroutine { continuation ->
                        var evaluated = false
                        val script = JsScriptWrapper.buildHarnessScript(code)

                        created.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (evaluated || !continuation.isActive) return
                                evaluated = true
                                created.evaluateJavascript(script) { value ->
                                    if (continuation.isActive) {
                                        continuation.resume(value)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = true

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString().orEmpty()
                                return if (reqUrl == "about:blank") {
                                    null
                                } else {
                                    WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                            }
                        }

                        continuation.invokeOnCancellation {
                            created.stopLoading()
                        }

                        created.loadDataWithBaseURL(
                            "about:blank",
                            "<!doctype html><html><head><meta charset='utf-8'></head><body></body></html>",
                            "text/html",
                            "utf-8",
                            null
                        )
                    }

                    val parsed = JsScriptWrapper.parseEvaluationResult(rawResult)
                    val elapsed = (clock.nowMillis() - startedAt).coerceAtLeast(0L)
                    if (parsed.errorMessage != null) {
                        val allLines = parsed.consoleLines + ConsoleLine(
                            text = parsed.errorMessage,
                            level = ConsoleLevel.ERROR
                        )
                        AppResult.Success(
                            CodeExecutionOutput.Console(
                                lines = allLines,
                                hasRuntimeError = true,
                                durationMs = elapsed
                            )
                        )
                    } else {
                        AppResult.Success(
                            CodeExecutionOutput.Console(
                                lines = parsed.consoleLines,
                                hasRuntimeError = false,
                                durationMs = elapsed
                            )
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                AppResult.Failure(CodeExecutionError.ExecutionTimeout)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(CodeExecutionError.JsEngineUnavailable)
            } finally {
                runCatchingCleanly {
                    webView?.stopLoading()
                    webView?.destroy()
                }
            }
        }

    private suspend fun checkStaticSyntax(
        language: String,
        code: String
    ): AppResult<CodeExecutionOutput, CodeExecutionError> = withContext(defaultDispatcher) {
        val startedAt = clock.nowMillis()
        val report = StaticSyntaxChecker.analyze(code, language)
        val elapsed = (clock.nowMillis() - startedAt).coerceAtLeast(0L)

        if (report.isValid) {
            AppResult.Success(
                CodeExecutionOutput.SyntaxCheckPassed(
                    lineCount = report.lineCount,
                    durationMs = elapsed
                )
            )
        } else {
            AppResult.Failure(
                CodeExecutionError.StaticSyntaxFailure(
                    issueType = report.issueType ?: SyntaxIssueType.MISMATCHED_DELIMITER,
                    line = report.issueLine,
                    expectedToken = report.expectedToken,
                    actualToken = report.actualToken
                )
            )
        }
    }

    private fun readCursorRow(cursor: Cursor, columnCount: Int): List<String> {
        val row = ArrayList<String>(columnCount)
        for (i in 0 until columnCount) {
            val cell = when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> "NULL"
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i).toString()
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i).toString()
                Cursor.FIELD_TYPE_BLOB -> "<BLOB ${cursor.getBlob(i)?.size ?: 0} B>"
                else -> cursor.getString(i) ?: ""
            }
            row += cell
        }
        return row
    }

    private fun queryChangesCount(db: SQLiteDatabase, signal: CancellationSignal): Int {
        return try {
            db.rawQuery("SELECT changes()", null, signal).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun sanitizeSqliteError(raw: String?): String {
        if (raw.isNullOrBlank()) return "SQL syntax error"
        return raw.substringBefore("(code ").trim().ifBlank { raw.take(180) }
    }

    private fun sanitizeJsonError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Invalid JSON syntax"
        return raw.lineSequence().firstOrNull()?.take(200) ?: "Invalid JSON syntax"
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configureSandboxedWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            blockNetworkImage = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            databaseEnabled = false
            domStorageEnabled = false
            setSupportMultipleWindows(false)
        }
    }

    private inline fun runCatchingCleanly(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Temizlik hatası yoksayılır
        }
    }

    private companion object {
        const val SQL_TIMEOUT_MS = 3_000L
        const val JS_TIMEOUT_MS = 4_000L
        const val MAX_SQL_ROWS = 100
    }
}
