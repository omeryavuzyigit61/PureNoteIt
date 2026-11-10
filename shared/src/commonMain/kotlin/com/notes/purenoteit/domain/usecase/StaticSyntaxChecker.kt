package com.notes.purenoteit.domain.usecase

data class StaticSyntaxReport(
    val isValid: Boolean,
    val issueType: SyntaxIssueType? = null,
    val expectedToken: Char? = null,
    val actualToken: Char? = null,
    val issueLine: Int? = null,
    val lineCount: Int = 0
)

enum class SyntaxIssueType {
    UNCLOSED_DELIMITER,
    UNEXPECTED_CLOSING_DELIMITER,
    MISMATCHED_DELIMITER,
    UNCLOSED_STRING,
    UNCLOSED_BLOCK_COMMENT
}

/**
 * Çevrimdışı derleyicisi cihazda bulunmayan diller (Kotlin, Python, Java, C++, Bash) için
 * yorum ve metin sabitlerini atlayarak parantez, tırnak ve blok yorum dengesini denetler.
 */
object StaticSyntaxChecker {

    private data class OpenDelimiter(val ch: Char, val line: Int)

    fun analyze(code: String, language: String): StaticSyntaxReport {
        val lang = language.trim().lowercase()
        val usesHashComment = lang == "python" || lang == "bash"
        val usesCStyleComment = lang == "kotlin" || lang == "java" || lang == "cpp" || lang == "javascript"
        val usesSqlComment = lang == "sql"
        val supportsTripleQuotes = lang == "kotlin" || lang == "python"
        val supportsBackticks = lang == "javascript" || lang == "bash" || lang == "kotlin"

        val stack = ArrayDeque<OpenDelimiter>()
        var line = 1
        var i = 0
        val len = code.length
        val totalLines = if (code.isEmpty()) 0 else code.count { it == '\n' } + 1

        while (i < len) {
            val ch = code[i]
            if (ch == '\n') {
                line++
                i++
                continue
            }

            // Yorum blokları ve satır yorumları
            if (usesHashComment && ch == '#') {
                while (i < len && code[i] != '\n') i++
                continue
            }
            if (usesSqlComment && ch == '-' && i + 1 < len && code[i + 1] == '-') {
                while (i < len && code[i] != '\n') i++
                continue
            }
            if (usesCStyleComment && ch == '/' && i + 1 < len) {
                val next = code[i + 1]
                if (next == '/') {
                    while (i < len && code[i] != '\n') i++
                    continue
                } else if (next == '*') {
                    val startLine = line
                    i += 2
                    var closed = false
                    while (i < len) {
                        if (code[i] == '\n') line++
                        if (code[i] == '*' && i + 1 < len && code[i + 1] == '/') {
                            i += 2
                            closed = true
                            break
                        }
                        i++
                    }
                    if (!closed) {
                        return StaticSyntaxReport(
                            isValid = false,
                            issueType = SyntaxIssueType.UNCLOSED_BLOCK_COMMENT,
                            issueLine = startLine,
                            lineCount = totalLines
                        )
                    }
                    continue
                }
            }

            // Üçlü tırnak (Python / Kotlin)
            if (supportsTripleQuotes && ch == '"' && i + 2 < len && code[i + 1] == '"' && code[i + 2] == '"') {
                val startLine = line
                i += 3
                var closed = false
                while (i < len) {
                    if (code[i] == '\n') line++
                    if (code[i] == '"' && i + 2 < len && code[i + 1] == '"' && code[i + 2] == '"') {
                        i += 3
                        closed = true
                        break
                    }
                    i++
                }
                if (!closed) {
                    return StaticSyntaxReport(
                        isValid = false,
                        issueType = SyntaxIssueType.UNCLOSED_STRING,
                        expectedToken = '"',
                        issueLine = startLine,
                        lineCount = totalLines
                    )
                }
                continue
            }

            // Tekli / Çiftli tırnak veya backtick dizeleri
            if (ch == '"' || ch == '\'' || (supportsBackticks && ch == '`')) {
                val quote = ch
                val startLine = line
                i++
                var closed = false
                val allowsMultiline = quote == '`' || (quote == '"' && lang == "bash")
                while (i < len) {
                    val cur = code[i]
                    if (cur == '\\' && i + 1 < len) {
                        if (code[i + 1] == '\n') line++
                        i += 2
                        continue
                    }
                    if (cur == '\n') {
                        if (!allowsMultiline) {
                            return StaticSyntaxReport(
                                isValid = false,
                                issueType = SyntaxIssueType.UNCLOSED_STRING,
                                expectedToken = quote,
                                issueLine = startLine,
                                lineCount = totalLines
                            )
                        }
                        line++
                    }
                    if (cur == quote) {
                        i++
                        closed = true
                        break
                    }
                    i++
                }
                if (!closed) {
                    return StaticSyntaxReport(
                        isValid = false,
                        issueType = SyntaxIssueType.UNCLOSED_STRING,
                        expectedToken = quote,
                        issueLine = startLine,
                        lineCount = totalLines
                    )
                }
                continue
            }

            // Ayraçlar
            when (ch) {
                '(', '[', '{' -> stack.addLast(OpenDelimiter(ch, line))
                ')', ']', '}' -> {
                    val expectedOpen = when (ch) {
                        ')' -> '('
                        ']' -> '['
                        else -> '{'
                    }
                    val last = stack.removeLastOrNull()
                        ?: return StaticSyntaxReport(
                            isValid = false,
                            issueType = SyntaxIssueType.UNEXPECTED_CLOSING_DELIMITER,
                            actualToken = ch,
                            issueLine = line,
                            lineCount = totalLines
                        )
                    if (last.ch != expectedOpen) {
                        val expectedClose = matchingClose(last.ch)
                        return StaticSyntaxReport(
                            isValid = false,
                            issueType = SyntaxIssueType.MISMATCHED_DELIMITER,
                            expectedToken = expectedClose,
                            actualToken = ch,
                            issueLine = line,
                            lineCount = totalLines
                        )
                    }
                }
            }
            i++
        }

        val unclosed = stack.lastOrNull()
        if (unclosed != null) {
            return StaticSyntaxReport(
                isValid = false,
                issueType = SyntaxIssueType.UNCLOSED_DELIMITER,
                expectedToken = matchingClose(unclosed.ch),
                issueLine = unclosed.line,
                lineCount = totalLines
            )
        }

        return StaticSyntaxReport(isValid = true, lineCount = totalLines)
    }

    private fun matchingClose(open: Char): Char = when (open) {
        '(' -> ')'
        '[' -> ']'
        else -> '}'
    }
}
