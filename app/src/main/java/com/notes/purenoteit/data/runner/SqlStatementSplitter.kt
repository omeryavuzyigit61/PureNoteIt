package com.notes.purenoteit.data.runner

internal sealed interface SqlValidationResult {
    data class Valid(val statements: List<SqlParsedStatement>) : SqlValidationResult
    data class Blocked(val keyword: String) : SqlValidationResult
}

internal data class SqlParsedStatement(
    val sql: String,
    val returnsRows: Boolean
)

/**
 * Kullanıcı SQL betiklerini tırnak ve yorum sınırlarına saygı duyarak cümlelere böler;
 * ana kasa dosyalarına veya eklenti yüklemeye erişebilecek tehlikeli SQLite ifadelerini engeller.
 */
internal object SqlStatementSplitter {

    private val BLOCKED_KEYWORDS = setOf("ATTACH", "DETACH")
    private val BLOCKED_IDENTIFIERS = listOf("load_extension", "writable_schema")

    fun parseAndValidate(rawSql: String): SqlValidationResult {
        val rawStatements = splitStatements(rawSql)
        val parsed = mutableListOf<SqlParsedStatement>()

        for (stmt in rawStatements) {
            val blocked = findBlockedConstruct(stmt)
            if (blocked != null) {
                return SqlValidationResult.Blocked(blocked)
            }
            val returnsRows = isRowReturningStatement(stmt)
            parsed += SqlParsedStatement(sql = stmt, returnsRows = returnsRows)
        }
        return SqlValidationResult.Valid(parsed)
    }

    internal fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        val len = sql.length

        while (i < len) {
            val ch = sql[i]

            // -- satır yorumu
            if (ch == '-' && i + 1 < len && sql[i + 1] == '-') {
                while (i < len && sql[i] != '\n') {
                    current.append(sql[i])
                    i++
                }
                continue
            }

            // /* blok yorumu */
            if (ch == '/' && i + 1 < len && sql[i + 1] == '*') {
                current.append("/*")
                i += 2
                while (i < len) {
                    if (sql[i] == '*' && i + 1 < len && sql[i + 1] == '/') {
                        current.append("*/")
                        i += 2
                        break
                    }
                    current.append(sql[i])
                    i++
                }
                continue
            }

            // '...' tek tırnak, "..." çift tırnak veya `...` geri tırnak sabiti
            if (ch == '\'' || ch == '"' || ch == '`') {
                val quote = ch
                current.append(ch)
                i++
                while (i < len) {
                    val cur = sql[i]
                    current.append(cur)
                    i++
                    if (cur == quote) {
                        // SQLite kaçışlı çift tırnak: '' veya ""
                        if (i < len && sql[i] == quote) {
                            current.append(sql[i])
                            i++
                        } else {
                            break
                        }
                    }
                }
                continue
            }

            if (ch == ';') {
                val trimmed = stripLeadingComments(current.toString()).trim()
                if (trimmed.isNotEmpty()) {
                    statements += current.toString().trim()
                }
                current.clear()
                i++
                continue
            }

            current.append(ch)
            i++
        }

        val tail = stripLeadingComments(current.toString()).trim()
        if (tail.isNotEmpty()) {
            statements += current.toString().trim()
        }

        return statements
    }

    private fun findBlockedConstruct(statement: String): String? {
        val stripped = stripCommentsAndStrings(statement)
        val tokens = Regex("""[A-Za-z_][A-Za-z0-9_]*""").findAll(stripped).map { it.value }.toList()
        for (token in tokens) {
            if (token.uppercase() in BLOCKED_KEYWORDS) {
                return token.uppercase()
            }
            for (forbiddenId in BLOCKED_IDENTIFIERS) {
                if (token.equals(forbiddenId, ignoreCase = true)) {
                    return forbiddenId
                }
            }
        }
        // VACUUM INTO '<dosya_yolu>' dosya yazımını engelle
        val vacuumIdx = tokens.indexOfFirst { it.equals("VACUUM", ignoreCase = true) }
        if (vacuumIdx >= 0 && vacuumIdx + 1 < tokens.size && tokens[vacuumIdx + 1].equals("INTO", ignoreCase = true)) {
            return "VACUUM INTO"
        }
        return null
    }

    private fun isRowReturningStatement(statement: String): Boolean {
        val clean = stripLeadingComments(statement).trimStart()
        val firstWord = Regex("""^[A-Za-z_]+""").find(clean)?.value?.uppercase() ?: return false
        return when (firstWord) {
            "SELECT", "WITH", "VALUES", "EXPLAIN" -> true
            "PRAGMA" -> !clean.contains("=")
            else -> Regex("""\bRETURNING\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(stripCommentsAndStrings(clean))
        }
    }

    private fun stripLeadingComments(sql: String): String {
        var i = 0
        val len = sql.length
        while (i < len) {
            while (i < len && sql[i].isWhitespace()) i++
            if (i + 1 < len && sql[i] == '-' && sql[i + 1] == '-') {
                i += 2
                while (i < len && sql[i] != '\n') i++
            } else if (i + 1 < len && sql[i] == '/' && sql[i + 1] == '*') {
                i += 2
                while (i + 1 < len && !(sql[i] == '*' && sql[i + 1] == '/')) i++
                if (i + 1 < len) i += 2 else i = len
            } else {
                break
            }
        }
        return if (i < len) sql.substring(i) else ""
    }

    private fun stripCommentsAndStrings(sql: String): String {
        val out = StringBuilder(sql.length)
        var i = 0
        val len = sql.length
        while (i < len) {
            val ch = sql[i]
            if (ch == '-' && i + 1 < len && sql[i + 1] == '-') {
                while (i < len && sql[i] != '\n') i++
                out.append(' ')
                continue
            }
            if (ch == '/' && i + 1 < len && sql[i + 1] == '*') {
                i += 2
                while (i < len) {
                    if (sql[i] == '*' && i + 1 < len && sql[i + 1] == '/') {
                        i += 2
                        break
                    }
                    i++
                }
                out.append(' ')
                continue
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                val quote = ch
                i++
                while (i < len) {
                    val cur = sql[i++]
                    if (cur == quote) {
                        if (i < len && sql[i] == quote) i++ else break
                    }
                }
                out.append(' ')
                continue
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }
}
