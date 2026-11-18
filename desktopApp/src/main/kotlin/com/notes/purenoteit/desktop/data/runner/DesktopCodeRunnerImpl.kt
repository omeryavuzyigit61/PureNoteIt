package com.notes.purenoteit.desktop.data.runner

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.model.ConsoleLine
import com.notes.purenoteit.domain.model.JsonRootType
import com.notes.purenoteit.domain.repository.CodeExecutionRepository
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.domain.usecase.StaticSyntaxChecker
import com.notes.purenoteit.domain.usecase.SyntaxIssueType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import javax.script.ScriptEngineManager
import javax.tools.ToolProvider
import kotlin.concurrent.thread

interface DesktopCodeExecutor : CodeExecutionRepository {
    suspend fun executeWithApproval(
        language: String,
        code: String,
        bashExplicitlyApproved: Boolean
    ): AppResult<CodeExecutionOutput, CodeExecutionError>
}

/**
 * Sandboxed desktop multi-language code runner:
 * - Zero shell wrapper (`cmd /c`, `powershell -c`, `sh -c`, `bash -c` strictly forbidden).
 * - Writes code strictly inside an ephemeral temp sandbox directory and passes direct `[executable, filePath]` args.
 * - Cleans child process environment to an explicit minimal whitelist.
 * - Enforces hard timeout with descendant process tree termination (`destroyForcibly`).
 * - Caps combined `stdout + stderr` buffer to 64 KB (`65_536` bytes) to prevent memory exhaustion DoS.
 */
internal class DesktopCodeRunnerImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: AppClock,
    private val processTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
    private val executableResolver: (List<String>) -> String? = { candidates -> findExecutableOnSystem(candidates) }
) : DesktopCodeExecutor {

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val compactJson = Json { prettyPrint = false }
    private val embeddedJvmExecMutex = Mutex()

    override suspend fun executeOffline(
        language: String,
        code: String
    ): AppResult<CodeExecutionOutput, CodeExecutionError> =
        executeWithApproval(language = language, code = code, bashExplicitlyApproved = false)

    override suspend fun executeWithApproval(
        language: String,
        code: String,
        bashExplicitlyApproved: Boolean
    ): AppResult<CodeExecutionOutput, CodeExecutionError> {
        if (code.isBlank()) {
            return AppResult.Failure(CodeExecutionError.EmptyCode)
        }
        return when (language.trim().lowercase()) {
            "sql" -> executeSql(code)
            "json" -> executeJson(code)
            "html" -> AppResult.Success(CodeExecutionOutput.HtmlPreview(code, durationMs = 1L))
            "python", "py", "python3" -> executePython(code)
            "javascript", "js" -> executeNodeJs(code)
            "java" -> executeJava(code)
            "kotlin", "kt" -> executeKotlin(code)
            "cpp", "c++" -> executeCpp(code, isCpp = true)
            "c" -> executeCpp(code, isCpp = false)
            "bash", "sh" -> {
                if (!bashExplicitlyApproved) {
                    AppResult.Failure(CodeExecutionError.SecurityBlocked("BASH_CONFIRMATION_REQUIRED"))
                } else {
                    executeBash(code)
                }
            }
            else -> runStaticSyntaxFallback(language, code, infoBanner = null)
        }
    }

    private suspend fun executeSql(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> =
        withContext(ioDispatcher) {
            val startedAt = clock.nowMillis()
            val validation = DesktopSqlStatementSplitter.parseAndValidate(code)
            if (validation is DesktopSqlValidationResult.Blocked) {
                return@withContext AppResult.Failure(CodeExecutionError.SecurityBlocked(validation.keyword))
            }
            val statements = (validation as DesktopSqlValidationResult.Valid).statements
            if (statements.isEmpty()) {
                return@withContext AppResult.Failure(CodeExecutionError.EmptyCode)
            }

            try {
                withTimeout(processTimeoutMs) {
                    DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
                        var totalAffected = 0
                        var lastColumns = emptyList<String>()
                        var lastRows = emptyList<List<String>>()
                        var truncated = false
                        var executed = 0

                        for (stmt in statements) {
                            conn.createStatement().use { s ->
                                s.queryTimeout = (processTimeoutMs / 1000L).coerceAtLeast(1L).toInt()
                                if (stmt.returnsRows) {
                                    s.executeQuery(stmt.sql).use { rs ->
                                        val md = rs.metaData
                                        val cols = (1..md.columnCount).map { md.getColumnName(it) }
                                        val rows = mutableListOf<List<String>>()
                                        var trunc = false
                                        while (rs.next()) {
                                            if (rows.size >= MAX_SQL_ROWS) {
                                                trunc = true
                                                break
                                            }
                                            rows += (1..md.columnCount).map { idx ->
                                                rs.getString(idx) ?: "NULL"
                                            }
                                        }
                                        lastColumns = cols
                                        lastRows = rows
                                        truncated = trunc
                                    }
                                } else {
                                    val changed = s.executeUpdate(stmt.sql)
                                    if (changed > 0) totalAffected += changed
                                }
                            }
                            executed++
                        }

                        AppResult.Success(
                            CodeExecutionOutput.SqlTable(
                                columns = lastColumns,
                                rows = lastRows,
                                rowsAffected = totalAffected,
                                statementsExecuted = executed,
                                isTruncated = truncated,
                                durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                            )
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                AppResult.Failure(CodeExecutionError.ExecutionTimeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLException) {
                AppResult.Failure(
                    CodeExecutionError.RuntimeOrSyntax(
                        message = e.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "SQL sözdizimi hatası" }
                    )
                )
            } catch (_: Exception) {
                AppResult.Failure(CodeExecutionError.RuntimeOrSyntax("SQL yürütme hatası"))
            }
        }

    private fun executeJson(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val startedAt = clock.nowMillis()
        return try {
            val elem = prettyJson.parseToJsonElement(code.trim())
            val formatted = prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), elem)
            val minified = compactJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), elem)
            val (rootType, count) = when (elem) {
                is JsonObject -> JsonRootType.OBJECT to elem.size
                is JsonArray -> JsonRootType.ARRAY to elem.size
                is JsonPrimitive -> JsonRootType.PRIMITIVE to 1
            }
            AppResult.Success(
                CodeExecutionOutput.JsonResult(
                    formattedJson = formatted,
                    minifiedJson = minified,
                    rootType = rootType,
                    itemCount = count,
                    durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                )
            )
        } catch (e: SerializationException) {
            AppResult.Failure(
                CodeExecutionError.RuntimeOrSyntax(
                    message = e.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "Geçersiz JSON formatı" }
                )
            )
        }
    }

    private suspend fun executePython(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val pythonExe = executableResolver(listOf("python3", "python"))
            ?: return runStaticSyntaxFallback("python", code, "Python bulunamadı; statik sözdizimi doğrulandı.")

        return runInTempSandbox { sandboxDir ->
            val script = sandboxDir.resolve("script.py").toFile()
            script.writeText(code, Charsets.UTF_8)
            runProcess(
                command = listOf(pythonExe, script.absolutePath),
                workingDir = sandboxDir.toFile(),
                extraEnv = mapOf(
                    "PYTHONNOUSERSITE" to "1",
                    "PYTHONDONTWRITEBYTECODE" to "1",
                    "PYTHONIOENCODING" to "UTF-8"
                )
            )
        }
    }

    private suspend fun executeNodeJs(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val nodeExe = executableResolver(listOf("node", "nodejs"))
            ?: return runStaticSyntaxFallback("javascript", code, "Node.js bulunamadı; statik sözdizimi doğrulandı.")

        return runInTempSandbox { sandboxDir ->
            val script = sandboxDir.resolve("script.mjs").toFile()
            script.writeText(code, Charsets.UTF_8)
            runProcess(
                command = listOf(nodeExe, "--no-warnings", script.absolutePath),
                workingDir = sandboxDir.toFile()
            )
        }
    }

    private suspend fun executeJava(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val javacExe = executableResolver(listOf("javac"))
        val javaExe = executableResolver(listOf("java"))
        val (className, normalizedSource) = normalizeJavaSource(code)

        if (javacExe != null && javaExe != null) {
            return runInTempSandbox { sandboxDir ->
                val srcFile = sandboxDir.resolve("$className.java").toFile()
                srcFile.writeText(normalizedSource, Charsets.UTF_8)
                val compileRes = runProcess(
                    command = listOf(javacExe, "-encoding", "UTF-8", srcFile.absolutePath),
                    workingDir = sandboxDir.toFile()
                )
                if (compileRes !is AppResult.Success) return@runInTempSandbox compileRes
                val compileOut = compileRes.data
                if (compileOut is CodeExecutionOutput.Console && compileOut.hasRuntimeError) {
                    return@runInTempSandbox compileRes
                }
                runProcess(
                    command = listOf(javaExe, "-Dfile.encoding=UTF-8", "-cp", sandboxDir.toFile().absolutePath, className),
                    workingDir = sandboxDir.toFile()
                )
            }
        }

        // Embedded JDK compiler fallback so Java also runs inside standalone packaged distributions
        return executeEmbeddedJava(className, normalizedSource)
    }

    private suspend fun executeEmbeddedJava(
        className: String,
        normalizedSource: String
    ): AppResult<CodeExecutionOutput, CodeExecutionError> = runInTempSandbox { sandboxDir ->
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: return@runInTempSandbox runStaticSyntaxFallback(
                "java",
                normalizedSource,
                "JDK derleyicisi bulunamadı; statik sözdizimi doğrulandı."
            )
        val startedAt = clock.nowMillis()
        val srcFile = sandboxDir.resolve("$className.java").toFile()
        srcFile.writeText(normalizedSource, Charsets.UTF_8)
        val compilerDiagnostics = StringWriter()
        val fileManager = compiler.getStandardFileManager(null, null, Charsets.UTF_8)
        val compiledOk = try {
            val units = fileManager.getJavaFileObjectsFromFiles(listOf(srcFile))
            val task = compiler.getTask(
                compilerDiagnostics,
                fileManager,
                null,
                listOf("-d", sandboxDir.toFile().absolutePath, "-encoding", "UTF-8"),
                null,
                units
            )
            task.call() == true
        } finally {
            fileManager.close()
        }

        if (!compiledOk) {
            val diag = compilerDiagnostics.toString().trim().ifBlank { "Java derleme hatası" }
            return@runInTempSandbox AppResult.Success(
                CodeExecutionOutput.Console(
                    lines = diag.lines().map { ConsoleLine(it, ConsoleLevel.ERROR) },
                    hasRuntimeError = true,
                    durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                )
            )
        }

        runEmbeddedCapturedJvm(startedAt) {
            URLClassLoader(arrayOf(sandboxDir.toUri().toURL()), this::class.java.classLoader).use { loader ->
                val cls = loader.loadClass(className)
                val mainMethod = cls.getMethod("main", Array<String>::class.java)
                mainMethod.invoke(null, emptyArray<String>())
            }
        }
    }

    private suspend fun executeKotlin(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val kotlincExe = executableResolver(listOf("kotlinc"))
        val javaExe = executableResolver(listOf("java"))
        if (kotlincExe != null && javaExe != null) {
            val normalized = if (Regex("""\bfun\s+main\s*\(""").containsMatchIn(code)) {
                code
            } else {
                "fun main() {\n$code\n}"
            }
            return runInTempSandbox { sandboxDir ->
                val ktFile = sandboxDir.resolve("Main.kt").toFile()
                val jarFile = sandboxDir.resolve("app.jar").toFile()
                ktFile.writeText(normalized, Charsets.UTF_8)
                val compileRes = runProcess(
                    command = listOf(kotlincExe, ktFile.absolutePath, "-include-runtime", "-d", jarFile.absolutePath),
                    workingDir = sandboxDir.toFile()
                )
                if (compileRes !is AppResult.Success) return@runInTempSandbox compileRes
                val compileOut = compileRes.data
                if (compileOut is CodeExecutionOutput.Console && compileOut.hasRuntimeError) {
                    return@runInTempSandbox compileRes
                }
                runProcess(
                    command = listOf(javaExe, "-Dfile.encoding=UTF-8", "-jar", jarFile.absolutePath),
                    workingDir = sandboxDir.toFile()
                )
            }
        }

        return executeEmbeddedKotlin(code)
    }

    private suspend fun executeEmbeddedKotlin(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> =
        withContext(ioDispatcher) {
            val startedAt = clock.nowMillis()
            val engine = ScriptEngineManager(this::class.java.classLoader).getEngineByExtension("kts")
                ?: return@withContext runStaticSyntaxFallback(
                    "kotlin",
                    code,
                    "Kotlin çalıştırma motoru başlatılamadı; statik sözdizimi kontrolü uygulandı."
                )

            val scriptText = prepareKotlinScriptForEval(code)
            runEmbeddedCapturedJvm(startedAt) {
                val result = engine.eval(scriptText)
                if (result != null && result !is Unit) {
                    println(result)
                }
            }
        }

    private fun prepareKotlinScriptForEval(code: String): String {
        val trimmed = code.trim()
        val hasMainArgs = Regex("""\bfun\s+main\s*\(\s*[a-zA-Z_][a-zA-Z0-9_]*\s*:\s*Array\s*<\s*String\s*>""").containsMatchIn(trimmed)
        val hasMainNoArgs = Regex("""\bfun\s+main\s*\(\s*\)""").containsMatchIn(trimmed)
        return when {
            hasMainArgs -> "$trimmed\nmain(emptyArray())"
            hasMainNoArgs -> "$trimmed\nmain()"
            else -> trimmed
        }
    }

    private suspend fun runEmbeddedCapturedJvm(
        startedAt: Long,
        block: () -> Unit
    ): AppResult<CodeExecutionOutput, CodeExecutionError> = embeddedJvmExecMutex.withLock {
        val outBaos = CappedByteArrayOutputStream(maxOutputBytes)
        val errBaos = CappedByteArrayOutputStream(maxOutputBytes)
        val origOut = System.out
        val origErr = System.err
        var uncaught: Throwable? = null
        val worker = Thread({
            try {
                block()
            } catch (t: Throwable) {
                uncaught = t
            }
        }, "purenote-embedded-exec").apply { isDaemon = true }

        try {
            System.setOut(PrintStream(outBaos, true, Charsets.UTF_8))
            System.setErr(PrintStream(errBaos, true, Charsets.UTF_8))
            worker.start()
            val joined = runInterruptible(ioDispatcher) {
                worker.join(processTimeoutMs)
                !worker.isAlive
            }
            if (!joined) {
                worker.interrupt()
                return AppResult.Failure(CodeExecutionError.ExecutionTimeout)
            }
        } catch (e: CancellationException) {
            worker.interrupt()
            throw e
        } finally {
            runCatching { System.out.flush() }
            runCatching { System.err.flush() }
            System.setOut(origOut)
            System.setErr(origErr)
        }

        val lines = mutableListOf<ConsoleLine>()
        val outText = outBaos.toUtf8String().trimEnd()
        val errText = errBaos.toUtf8String().trimEnd()
        if (outText.isNotBlank()) {
            outText.lines().forEach { lines += ConsoleLine(it, ConsoleLevel.OUTPUT) }
        }
        if (errText.isNotBlank()) {
            errText.lines().forEach { lines += ConsoleLine(it, ConsoleLevel.ERROR) }
        }
        uncaught?.let { ex ->
            val root = generateSequence(ex) { it.cause }.lastOrNull() ?: ex
            val msg = root.message?.takeIf { it.isNotBlank() } ?: root::class.java.simpleName
            lines += ConsoleLine("Çalıştırma hatası: $msg", ConsoleLevel.ERROR)
        }
        if (outBaos.truncated || errBaos.truncated) {
            lines += ConsoleLine(
                "[Çıktı bellek güvenliği için ${maxOutputBytes / 1024} KB sınırında kesildi]",
                ConsoleLevel.WARN
            )
        }
        if (lines.isEmpty()) {
            lines += ConsoleLine("(Çıktı yok — çıkış kodu 0)", ConsoleLevel.INFO)
        }
        AppResult.Success(
            CodeExecutionOutput.Console(
                lines = lines,
                hasRuntimeError = uncaught != null || errText.isNotBlank(),
                durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
            )
        )
    }

    private suspend fun executeCpp(code: String, isCpp: Boolean): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val compiler = if (isCpp) {
            executableResolver(listOf("g++", "clang++"))
        } else {
            executableResolver(listOf("gcc", "clang"))
        } ?: return runStaticSyntaxFallback(
            if (isCpp) "cpp" else "c",
            code,
            "Derleyici (g++/clang++) sistemde bulunamadı (MSYS2 / MinGW / LLVM kuruluysa otomatik çalışır); statik sözdizimi kontrolü uygulandı."
        )

        val ext = if (isCpp) "cpp" else "c"
        val stdFlag = if (isCpp) "-std=c++17" else "-std=c11"
        val isWin = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val binName = if (isWin) "program.exe" else "program"

        return runInTempSandbox { sandboxDir ->
            val srcFile = sandboxDir.resolve("main.$ext").toFile()
            val outBin = sandboxDir.resolve(binName).toFile()
            srcFile.writeText(code, Charsets.UTF_8)
            val compileRes = runProcess(
                command = listOf(compiler, stdFlag, "-O2", srcFile.absolutePath, "-o", outBin.absolutePath),
                workingDir = sandboxDir.toFile()
            )
            if (compileRes !is AppResult.Success) return@runInTempSandbox compileRes
            val compileOut = compileRes.data
            if (compileOut is CodeExecutionOutput.Console && compileOut.hasRuntimeError) {
                return@runInTempSandbox compileRes
            }
            runProcess(
                command = listOf(outBin.absolutePath),
                workingDir = sandboxDir.toFile()
            )
        }
    }

    private suspend fun executeBash(code: String): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val bashExe = executableResolver(listOf("bash", "sh"))
            ?: return runStaticSyntaxFallback("bash", code, "Bash çalıştırıcısı bulunamadı; statik doğrulama yapıldı.")

        return runInTempSandbox { sandboxDir ->
            val script = sandboxDir.resolve("script.sh").toFile()
            script.writeText(code.replace("\r\n", "\n"), Charsets.UTF_8)
            runProcess(
                command = listOf(bashExe, script.absolutePath),
                workingDir = sandboxDir.toFile()
            )
        }
    }

    private suspend fun runInTempSandbox(
        block: suspend (java.nio.file.Path) -> AppResult<CodeExecutionOutput, CodeExecutionError>
    ): AppResult<CodeExecutionOutput, CodeExecutionError> = withContext(ioDispatcher) {
        val sandboxDir = Files.createTempDirectory("purenote_exec_")
        try {
            block(sandboxDir)
        } finally {
            sandboxDir.toFile().deleteRecursively()
        }
    }

    private suspend fun runProcess(
        command: List<String>,
        workingDir: File,
        extraEnv: Map<String, String> = emptyMap()
    ): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val startedAt = clock.nowMillis()
        val pb = ProcessBuilder(command)
        pb.directory(workingDir)

        // Strip host environment to prevent leaking sensitive variables or tokens to user code
        val parentEnv = HashMap(pb.environment())
        pb.environment().clear()
        for (allowedKey in ENV_WHITELIST) {
            val value = parentEnv.entries.firstOrNull { it.key.equals(allowedKey, ignoreCase = true) }?.value
            if (value != null) {
                pb.environment()[allowedKey] = value
            }
        }
        pb.environment().putAll(extraEnv)

        var process: Process? = null
        return try {
            val proc = pb.start()
            process = proc

            val combinedLock = Any()
            var totalBuffered = 0
            var wasTruncated = false

            fun readCappedStream(stream: InputStream): ByteArray {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(2048)
                try {
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        synchronized(combinedLock) {
                            val remaining = maxOutputBytes - totalBuffered
                            if (remaining > 0) {
                                val toWrite = minOf(read, remaining)
                                out.write(buf, 0, toWrite)
                                totalBuffered += toWrite
                                if (read > remaining) {
                                    wasTruncated = true
                                }
                            } else {
                                wasTruncated = true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                return out.toByteArray()
            }

            var stdoutBytes = ByteArray(0)
            var stderrBytes = ByteArray(0)
            val outThread = thread(start = true, isDaemon = true) { stdoutBytes = readCappedStream(proc.inputStream) }
            val errThread = thread(start = true, isDaemon = true) { stderrBytes = readCappedStream(proc.errorStream) }

            val finishedInTime = withTimeout(processTimeoutMs + 1_000L) {
                runInterruptible(ioDispatcher) {
                    proc.waitFor(processTimeoutMs, TimeUnit.MILLISECONDS)
                }
            }
            if (!finishedInTime) {
                destroyProcessTree(proc)
                outThread.join(300L)
                errThread.join(300L)
                return AppResult.Failure(CodeExecutionError.ExecutionTimeout)
            }
            val exitCode = proc.exitValue()
            outThread.join(1_000L)
            errThread.join(1_000L)

            val lines = mutableListOf<ConsoleLine>()
            val outText = String(stdoutBytes, Charsets.UTF_8).trimEnd()
            val errText = String(stderrBytes, Charsets.UTF_8).trimEnd()
            if (outText.isNotBlank()) {
                outText.lines().forEach { lines += ConsoleLine(it, ConsoleLevel.OUTPUT) }
            }
            if (errText.isNotBlank()) {
                errText.lines().forEach { lines += ConsoleLine(it, ConsoleLevel.ERROR) }
            }
            if (wasTruncated) {
                lines += ConsoleLine(
                    "[Çıktı bellek güvenliği için ${maxOutputBytes / 1024} KB sınırında kesildi]",
                    ConsoleLevel.WARN
                )
            }
            if (exitCode != 0 && errText.isBlank()) {
                lines += ConsoleLine("Çıkış kodu: $exitCode", ConsoleLevel.ERROR)
            }
            if (lines.isEmpty()) {
                lines += ConsoleLine("(Çıktı yok — çıkış kodu 0)", ConsoleLevel.INFO)
            }

            AppResult.Success(
                CodeExecutionOutput.Console(
                    lines = lines,
                    hasRuntimeError = exitCode != 0 || errText.isNotBlank(),
                    durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                )
            )
        } catch (_: TimeoutCancellationException) {
            destroyProcessTree(process)
            AppResult.Failure(CodeExecutionError.ExecutionTimeout)
        } catch (e: CancellationException) {
            destroyProcessTree(process)
            throw e
        } catch (t: Exception) {
            destroyProcessTree(process)
            AppResult.Failure(
                CodeExecutionError.RuntimeOrSyntax(
                    message = t.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "Süreç başlatılamadı" }
                )
            )
        } finally {
            process?.let { proc ->
                if (proc.isAlive) {
                    destroyProcessTree(proc)
                }
            }
        }
    }

    private fun destroyProcessTree(process: Process?) {
        if (process == null) return
        try {
            process.descendants().forEach { child ->
                try {
                    child.destroyForcibly()
                } catch (_: Exception) {
                }
            }
            process.destroyForcibly()
            process.waitFor(500L, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
    }

    private fun runStaticSyntaxFallback(
        language: String,
        code: String,
        infoBanner: String?
    ): AppResult<CodeExecutionOutput, CodeExecutionError> {
        val startedAt = clock.nowMillis()
        val report = StaticSyntaxChecker.analyze(code, language)
        if (!report.isValid) {
            return AppResult.Failure(
                CodeExecutionError.StaticSyntaxFailure(
                    issueType = report.issueType ?: SyntaxIssueType.UNCLOSED_DELIMITER,
                    line = report.issueLine,
                    expectedToken = report.expectedToken,
                    actualToken = report.actualToken
                )
            )
        }
        val lineCount = report.lineCount
        return if (infoBanner != null) {
            AppResult.Success(
                CodeExecutionOutput.Console(
                    lines = listOf(
                        ConsoleLine(infoBanner, ConsoleLevel.INFO),
                        ConsoleLine("Statik sözdizimi analizi geçti ($lineCount satır).", ConsoleLevel.OUTPUT)
                    ),
                    hasRuntimeError = false,
                    durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                )
            )
        } else {
            AppResult.Success(
                CodeExecutionOutput.SyntaxCheckPassed(
                    lineCount = lineCount,
                    durationMs = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                )
            )
        }
    }

    private fun normalizeJavaSource(code: String): Pair<String, String> {
        val publicClass = Regex("""public\s+class\s+([A-Za-z_][A-Za-z0-9_]*)""").find(code)?.groupValues?.get(1)
        if (publicClass != null) return publicClass to code
        val anyClass = Regex("""class\s+([A-Za-z_][A-Za-z0-9_]*)""").find(code)?.groupValues?.get(1)
        if (anyClass != null) return anyClass to code
        val wrapped = """
            public class Main {
                public static void main(String[] args) throws Exception {
                    $code
                }
            }
        """.trimIndent()
        return "Main" to wrapped
    }

    companion object {
        const val MAX_OUTPUT_BYTES = 64 * 1024 // 65,536 bytes
        const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val MAX_SQL_ROWS = 200

        private val ENV_WHITELIST = listOf(
            "PATH",
            "Path",
            "SYSTEMROOT",
            "SystemRoot",
            "SystemDrive",
            "WINDIR",
            "LOCALAPPDATA",
            "TEMP",
            "TMP",
            "HOME",
            "USERPROFILE",
            "LANG",
            "LC_ALL"
        )

        fun findExecutableOnSystem(candidates: List<String>): String? {
            val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            val wantsBash = candidates.any { it.equals("bash", ignoreCase = true) || it.equals("sh", ignoreCase = true) }
            val wantsPython = candidates.any { it.startsWith("python", ignoreCase = true) }
            val wantsNode = candidates.any { it.startsWith("node", ignoreCase = true) }
            val wantsJvmTool = candidates.any { it.equals("javac", true) || it.equals("java", true) || it.equals("kotlinc", true) }
            val wantsCppTool = candidates.any { it in setOf("g++", "clang++", "gcc", "clang") }

            val dirs = buildList {
                if (isWindows) {
                    val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                    val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
                    val localAppData = System.getenv("LOCALAPPDATA")
                    val userHome = System.getProperty("user.home")

                    if (wantsBash) {
                        add(File(programFiles, "Git\\bin"))
                        add(File(programFiles, "Git\\usr\\bin"))
                        add(File(programFilesX86, "Git\\bin"))
                        if (!localAppData.isNullOrBlank()) {
                            add(File(localAppData, "Programs\\Git\\bin"))
                        }
                        add(File("C:\\msys64\\usr\\bin"))
                    }
                    if (wantsNode) {
                        add(File(programFiles, "nodejs"))
                        add(File(programFilesX86, "nodejs"))
                    }
                    if (wantsPython && !localAppData.isNullOrBlank()) {
                        File(localAppData, "Programs\\Python")
                            .takeIf { it.isDirectory }
                            ?.listFiles()
                            ?.filter { it.isDirectory }
                            ?.sortedByDescending { it.name }
                            ?.forEach { add(it) }
                    }
                    if (wantsJvmTool) {
                        add(File(programFiles, "Android\\Android Studio\\plugins\\Kotlin\\kotlinc\\bin"))
                        add(File(programFiles, "Android\\Android Studio\\jbr\\bin"))
                        if (!userHome.isNullOrBlank()) {
                            File(userHome, ".jdks").takeIf { it.isDirectory }?.listFiles()
                                ?.filter { it.isDirectory }
                                ?.sortedByDescending { it.name }
                                ?.forEach { add(File(it, "bin")) }
                        }
                        listOf("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Amazon Corretto").forEach { vendor ->
                            File(programFiles, vendor).takeIf { it.isDirectory }?.listFiles()
                                ?.filter { it.isDirectory }
                                ?.sortedByDescending { it.name }
                                ?.forEach { add(File(it, "bin")) }
                        }
                    }
                    if (wantsCppTool) {
                        add(File("C:\\msys64\\ucrt64\\bin"))
                        add(File("C:\\msys64\\mingw64\\bin"))
                        add(File("C:\\msys64\\clang64\\bin"))
                        add(File("C:\\mingw64\\bin"))
                        add(File("C:\\TDM-GCC-64\\bin"))
                        add(File(programFiles, "LLVM\\bin"))
                        add(File(programFilesX86, "LLVM\\bin"))
                    }
                }

                System.getProperty("java.home")?.takeIf { it.isNotBlank() }?.let { add(File(it, "bin")) }
                System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it, "bin")) }
                val rawPath = System.getenv("PATH") ?: System.getenv("Path") ?: ""
                rawPath.split(File.pathSeparatorChar)
                    .filter { it.isNotBlank() }
                    .forEach { add(File(it)) }
            }

            val extensions = if (isWindows) listOf(".exe", ".bat", ".cmd", "") else listOf("")
            for (dir in dirs) {
                if (!dir.isDirectory) continue
                for (name in candidates) {
                    for (ext in extensions) {
                        val file = File(dir, "$name$ext")
                        if (!file.isFile) continue
                        val absLower = file.absolutePath.lowercase()
                        if (isWindows && wantsBash && (absLower.contains("\\system32\\bash.exe") || absLower.contains("\\windowsapps\\bash.exe"))) {
                            // Avoid broken Windows WSL stub when scanning bash interpreters
                            continue
                        }
                        if (isWindows && absLower.contains("windowsapps") && file.length() == 0L) {
                            continue
                        }
                        if (isWindows || file.canExecute()) {
                            return file.absolutePath
                        }
                    }
                }
            }
            return null
        }
    }
}

private class CappedByteArrayOutputStream(private val maxBytes: Int) : ByteArrayOutputStream() {
    var truncated: Boolean = false
        private set

    override fun write(b: Int) {
        if (count < maxBytes) {
            super.write(b)
        } else {
            truncated = true
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val remaining = maxBytes - count
        if (remaining > 0) {
            val toWrite = minOf(len, remaining)
            super.write(b, off, toWrite)
            if (len > remaining) truncated = true
        } else {
            truncated = true
        }
    }

    fun toUtf8String(): String = String(toByteArray(), Charsets.UTF_8)
}
