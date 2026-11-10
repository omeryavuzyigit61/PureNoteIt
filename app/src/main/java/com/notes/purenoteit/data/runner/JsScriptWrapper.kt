package com.notes.purenoteit.data.runner

import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.model.ConsoleLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class JsLogEntryDto(
    @SerialName("level") val level: String = "log",
    @SerialName("text") val text: String = ""
)

@Serializable
internal data class JsExecutionPayloadDto(
    @SerialName("logs") val logs: List<JsLogEntryDto> = emptyList(),
    @SerialName("returnValue") val returnValue: String? = null,
    @SerialName("hasReturnValue") val hasReturnValue: Boolean = false,
    @SerialName("error") val error: String? = null
)

/**
 * İzole WebView JS çalıştırıcısı için güvenli IIFE sarıcı betiği oluşturur ve JSON çıktısını ayrıştırır.
 * Hiçbir @JavascriptInterface köprüsü gerektirmez; konsol çıktılarını, son ifade değerini ve çalışma zamanı
 * hatalarını tek bir deterministik JSON cevabıyla toplar.
 */
internal object JsScriptWrapper {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun buildHarnessScript(userCode: String): String {
        val encodedCode = json.encodeToString(userCode)
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        return """
            (function() {
                var __logs = [];
                var __MAX_LOGS = 200;
                function __formatArg(arg) {
                    if (arg === undefined) return 'undefined';
                    if (arg === null) return 'null';
                    if (typeof arg === 'string') return arg;
                    if (typeof arg === 'function') return '[Function ' + (arg.name || 'anonymous') + ']';
                    if (arg instanceof Error) return (arg.name || 'Error') + ': ' + (arg.message || '');
                    if (typeof arg === 'object') {
                        try {
                            var seen = [];
                            return JSON.stringify(arg, function(k, v) {
                                if (typeof v === 'object' && v !== null) {
                                    if (seen.indexOf(v) !== -1) return '[Circular]';
                                    seen.push(v);
                                }
                                return v;
                            }, 2);
                        } catch (e) {
                            return String(arg);
                        }
                    }
                    return String(arg);
                }
                function __push(level, args) {
                    if (__logs.length >= __MAX_LOGS) return;
                    var parts = [];
                    for (var i = 0; i < args.length; i++) {
                        parts.push(__formatArg(args[i]));
                    }
                    __logs.push({ level: level, text: parts.join(' ') });
                }
                var __origConsole = window.console;
                var __sandboxConsole = {
                    log: function() { __push('log', arguments); },
                    info: function() { __push('info', arguments); },
                    warn: function() { __push('warn', arguments); },
                    error: function() { __push('error', arguments); },
                    debug: function() { __push('log', arguments); }
                };
                window.console = __sandboxConsole;
                var __ret = undefined;
                var __hasRet = false;
                var __err = null;
                try {
                    __ret = (0, eval)($encodedCode);
                    if (__ret !== undefined) {
                        __hasRet = true;
                        __ret = __formatArg(__ret);
                    }
                } catch (e) {
                    __err = (e && e.name ? e.name + ': ' : '') + (e && e.message ? e.message : String(e));
                } finally {
                    window.console = __origConsole;
                }
                return JSON.stringify({
                    logs: __logs,
                    returnValue: __hasRet ? String(__ret) : null,
                    hasReturnValue: __hasRet,
                    error: __err
                });
            })();
        """.trimIndent()
    }

    internal data class ParsedJsResult(
        val consoleLines: List<ConsoleLine>,
        val errorMessage: String?
    )

    fun parseEvaluationResult(rawEvaluateOutput: String?): ParsedJsResult {
        if (rawEvaluateOutput.isNullOrBlank() || rawEvaluateOutput == "null") {
            return ParsedJsResult(
                consoleLines = emptyList(),
                errorMessage = "JavaScript engine returned no result."
            )
        }
        val unwrappedJson = try {
            if (rawEvaluateOutput.startsWith("\"")) {
                json.decodeFromString<String>(rawEvaluateOutput)
            } else {
                rawEvaluateOutput
            }
        } catch (_: Exception) {
            return ParsedJsResult(
                consoleLines = listOf(ConsoleLine(text = rawEvaluateOutput, level = ConsoleLevel.OUTPUT)),
                errorMessage = null
            )
        }

        val payload = try {
            json.decodeFromString<JsExecutionPayloadDto>(unwrappedJson)
        } catch (_: Exception) {
            return ParsedJsResult(
                consoleLines = listOf(ConsoleLine(text = unwrappedJson, level = ConsoleLevel.OUTPUT)),
                errorMessage = null
            )
        }

        val lines = payload.logs.map { entry ->
            val level = when (entry.level) {
                "warn" -> ConsoleLevel.WARN
                "error" -> ConsoleLevel.ERROR
                "info" -> ConsoleLevel.INFO
                else -> ConsoleLevel.OUTPUT
            }
            ConsoleLine(text = entry.text, level = level)
        }.toMutableList()

        if (payload.hasReturnValue && payload.returnValue != null) {
            val prefix = if (lines.isNotEmpty()) "← " else ""
            lines += ConsoleLine(
                text = prefix + payload.returnValue,
                level = if (lines.isNotEmpty()) ConsoleLevel.INFO else ConsoleLevel.OUTPUT
            )
        }

        return ParsedJsResult(
            consoleLines = lines,
            errorMessage = payload.error
        )
    }
}
