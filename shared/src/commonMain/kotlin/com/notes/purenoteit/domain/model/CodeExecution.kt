package com.notes.purenoteit.domain.model

import com.notes.purenoteit.domain.usecase.SyntaxIssueType

/**
 * Kod bloğu dillerinin cihaz içi çevrimdışı çalışma yetenekleri.
 */
enum class OfflineExecutionCapability {
    SQL_IN_MEMORY,
    JAVASCRIPT_SANDBOX,
    HTML_PREVIEW,
    JSON_FORMAT_VALIDATE,
    SYNTAX_CHECK_ONLY
}

enum class ConsoleLevel {
    OUTPUT,
    INFO,
    WARN,
    ERROR
}

enum class JsonRootType {
    OBJECT,
    ARRAY,
    PRIMITIVE
}

data class ConsoleLine(
    val text: String,
    val level: ConsoleLevel = ConsoleLevel.OUTPUT
)

sealed interface CodeExecutionOutput {
    val durationMs: Long

    data class Console(
        val lines: List<ConsoleLine>,
        val hasRuntimeError: Boolean = false,
        override val durationMs: Long
    ) : CodeExecutionOutput

    data class SqlTable(
        val columns: List<String>,
        val rows: List<List<String>>,
        val rowsAffected: Int,
        val statementsExecuted: Int,
        val isTruncated: Boolean = false,
        override val durationMs: Long
    ) : CodeExecutionOutput

    data class HtmlPreview(
        val htmlContent: String,
        override val durationMs: Long
    ) : CodeExecutionOutput

    data class JsonResult(
        val formattedJson: String,
        val minifiedJson: String,
        val rootType: JsonRootType,
        val itemCount: Int,
        override val durationMs: Long
    ) : CodeExecutionOutput

    data class SyntaxCheckPassed(
        val lineCount: Int,
        override val durationMs: Long
    ) : CodeExecutionOutput
}

sealed interface CodeExecutionError : AppError {
    data object EmptyCode : CodeExecutionError
    data object ExecutionTimeout : CodeExecutionError
    data object JsEngineUnavailable : CodeExecutionError
    data class SecurityBlocked(val operation: String) : CodeExecutionError
    data class RuntimeOrSyntax(val message: String, val lineHint: Int? = null) : CodeExecutionError
    data class StaticSyntaxFailure(
        val issueType: SyntaxIssueType,
        val line: Int?,
        val expectedToken: Char? = null,
        val actualToken: Char? = null
    ) : CodeExecutionError
}

/**
 * Her dil için cihaz içi çevrimdışı çalıştırma kapasitesi ve harici ücretsiz tarayıcı playground eşlemesi.
 */
object CodeLanguageCapabilities {

    fun capabilityFor(languageKey: String): OfflineExecutionCapability {
        return when (languageKey.trim().lowercase()) {
            "sql" -> OfflineExecutionCapability.SQL_IN_MEMORY
            "javascript", "js" -> OfflineExecutionCapability.JAVASCRIPT_SANDBOX
            "html" -> OfflineExecutionCapability.HTML_PREVIEW
            "json" -> OfflineExecutionCapability.JSON_FORMAT_VALIDATE
            else -> OfflineExecutionCapability.SYNTAX_CHECK_ONLY
        }
    }

    fun isFullyExecutableOffline(languageKey: String): Boolean {
        return capabilityFor(languageKey) != OfflineExecutionCapability.SYNTAX_CHECK_ONLY
    }

    fun externalPlaygroundUrl(languageKey: String): String? {
        return when (languageKey.trim().lowercase()) {
            "kotlin" -> "https://play.kotlinlang.org/"
            "python" -> "https://www.programiz.com/python-programming/online-compiler/"
            "java" -> "https://www.programiz.com/java-programming/online-compiler/"
            "cpp" -> "https://www.programiz.com/cpp-programming/online-compiler/"
            "bash" -> "https://www.tutorialspoint.com/execute_bash_online.php"
            else -> null
        }
    }
}
