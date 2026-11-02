package com.notes.purenoteit.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    val SUPPORTED_LANGUAGES = listOf(
        "kotlin" to "Kotlin",
        "python" to "Python",
        "javascript" to "JavaScript",
        "java" to "Java",
        "cpp" to "C++",
        "sql" to "SQL",
        "html" to "HTML",
        "json" to "JSON",
        "bash" to "Bash/Shell"
    )

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam",
        "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg"
    )

    private val PYTHON_KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
        "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or",
        "pass", "raise", "return", "try", "while", "with", "yield"
    )

    private val JS_KEYWORDS = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do", "else", "export", "extends", "finally",
        "for", "function", "if", "import", "in", "instanceof", "new", "return", "super",
        "switch", "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield",
        "let", "static", "enum", "implements", "interface", "package", "private",
        "protected", "public", "true", "false", "null", "undefined"
    )

    private val SQL_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE", "JOIN",
        "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "ON", "GROUP", "BY", "HAVING",
        "ORDER", "ASC", "DESC", "LIMIT", "OFFSET", "CREATE", "TABLE", "DROP", "ALTER",
        "ADD", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "DEFAULT",
        "AND", "OR", "IN", "BETWEEN", "LIKE", "IS", "AS", "CASE", "WHEN", "THEN", "ELSE", "END"
    )

    fun highlight(code: String, language: String, isDark: Boolean = true): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        val builder = AnnotatedString.Builder(code)

        // Palette (Modern VS Code Dark / Vibrant Monokai)
        val keywordColor = if (isDark) Color(0xFFFF79C6) else Color(0xFF9C27B0)
        val stringColor = if (isDark) Color(0xFFF1FA8C) else Color(0xFF2E7D32)
        val numberColor = if (isDark) Color(0xFFBD93F9) else Color(0xFF1565C0)
        val commentColor = if (isDark) Color(0xFF6272A4) else Color(0xFF757575)
        val annotationColor = if (isDark) Color(0xFF50FA7B) else Color(0xFFD84315)
        val functionColor = if (isDark) Color(0xFF8BE9FD) else Color(0xFF00838F)

        // 1. Comments
        val commentPattern = when (language.lowercase()) {
            "python", "bash" -> Regex("""#.*""")
            "sql" -> Regex("""--.*""")
            else -> Regex("""//.*|/\*[\s\S]*?\*/""")
        }
        for (match in commentPattern.findAll(code)) {
            builder.addStyle(
                SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                match.range.first, match.range.last + 1
            )
        }

        // 2. Strings (Double quote and single quote)
        val stringPattern = Regex(""""(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'""")
        for (match in stringPattern.findAll(code)) {
            builder.addStyle(
                SpanStyle(color = stringColor),
                match.range.first, match.range.last + 1
            )
        }

        // 3. Numbers
        val numberPattern = Regex("""\b\d+(?:\.\d+)?(?:[fFL])?\b""")
        for (match in numberPattern.findAll(code)) {
            builder.addStyle(
                SpanStyle(color = numberColor, fontWeight = FontWeight.SemiBold),
                match.range.first, match.range.last + 1
            )
        }

        // 4. Annotations (@Example)
        val annotationPattern = Regex("""@[a-zA-Z_]\w*""")
        for (match in annotationPattern.findAll(code)) {
            builder.addStyle(
                SpanStyle(color = annotationColor, fontWeight = FontWeight.Bold),
                match.range.first, match.range.last + 1
            )
        }

        // 5. Function calls (word followed by open parenthesis)
        val funcPattern = Regex("""\b([a-zA-Z_]\w*)\s*(?=\()""")
        for (match in funcPattern.findAll(code)) {
            builder.addStyle(
                SpanStyle(color = functionColor, fontWeight = FontWeight.Medium),
                match.range.first, match.range.last + 1
            )
        }

        // 6. Keywords
        val keywords = when (language.lowercase()) {
            "python" -> PYTHON_KEYWORDS
            "javascript", "js", "html" -> JS_KEYWORDS
            "sql" -> SQL_KEYWORDS
            else -> KOTLIN_KEYWORDS
        }

        val wordPattern = Regex("""\b[a-zA-Z_]\w*\b""")
        for (match in wordPattern.findAll(code)) {
            val word = match.value
            val isKeyword = if (language.equals("sql", ignoreCase = true)) {
                keywords.contains(word.uppercase())
            } else {
                keywords.contains(word)
            }
            if (isKeyword) {
                builder.addStyle(
                    SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                    match.range.first, match.range.last + 1
                )
            }
        }

        return builder.toAnnotatedString()
    }
}
