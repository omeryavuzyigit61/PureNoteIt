package com.notes.purenoteit.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.notes.purenoteit.R
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.model.JsonRootType
import com.notes.purenoteit.domain.usecase.SyntaxIssueType
import com.notes.purenoteit.ui.mvi.BlockExecutionUiState
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.VibrantOrange
import java.io.ByteArrayInputStream

@Composable
fun CodeBlockExecutionPanel(
    executionState: BlockExecutionUiState,
    hasExternalPlayground: Boolean,
    onApplyFormattedJson: (String) -> Unit,
    onOpenExternalPlayground: () -> Unit,
    onClearOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val output = executionState.output
    val error = executionState.error
    if (output == null && error == null) return

    val isErrorPanel = error != null || (output is CodeExecutionOutput.Console && output.hasRuntimeError)
    val panelBorderColor = if (isErrorPanel) Color(0xFF8C3B3B) else Color(0xFF363646)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF14141A),
        border = BorderStroke(0.75.dp, panelBorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            // Başlık Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (isErrorPanel) Color(0xFFFF6B6B) else Color(0xFF50FA7B)
                    val titleText = if (output is CodeExecutionOutput.HtmlPreview) {
                        stringResource(R.string.code_html_preview_title)
                    } else {
                        stringResource(R.string.code_output_title)
                    }
                    Text(
                        text = if (isErrorPanel) "✕ $titleText" else "✓ $titleText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    if (output != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.code_duration_ms, output.durationMs.toInt()),
                            fontSize = 10.sp,
                            color = Color(0xFF8E8E9E)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (output is CodeExecutionOutput.JsonResult) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VibrantOrange.copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, VibrantOrange.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { onApplyFormattedJson(output.formattedJson) }
                        ) {
                            Text(
                                text = "{} " + stringResource(R.string.code_json_apply_format),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (hasExternalPlayground && (output is CodeExecutionOutput.SyntaxCheckPassed || error is CodeExecutionError.StaticSyntaxFailure)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF282834),
                            border = BorderStroke(0.5.dp, Color(0xFF454555)),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { onOpenExternalPlayground() }
                        ) {
                            Text(
                                text = "↗ " + stringResource(R.string.code_open_web_ide),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8BE9FD),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.code_clear_output),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA0A0B2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onClearOutput() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 7.dp),
                thickness = 0.5.dp,
                color = Color(0xFF2B2B36)
            )

            // Hata Mesajı Gösterimi
            if (error != null) {
                Text(
                    text = resolveCodeErrorMessage(error),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFFF8A80),
                    lineHeight = 17.sp
                )
            } else if (output != null) {
                when (output) {
                    is CodeExecutionOutput.Console -> ConsoleOutputView(output)
                    is CodeExecutionOutput.SqlTable -> SqlTableOutputView(output)
                    is CodeExecutionOutput.HtmlPreview -> SandboxedHtmlPreviewView(output.htmlContent)
                    is CodeExecutionOutput.JsonResult -> JsonOutputView(output)
                    is CodeExecutionOutput.SyntaxCheckPassed -> SyntaxCheckPassedView(output)
                }
            }
        }
    }
}

@Composable
private fun ConsoleOutputView(output: CodeExecutionOutput.Console) {
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (output.lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.code_output_empty),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF8E8E9E)
                )
            } else {
                output.lines.forEach { line ->
                    val lineColor = when (line.level) {
                        ConsoleLevel.ERROR -> Color(0xFFFF6B6B)
                        ConsoleLevel.WARN -> Color(0xFFFFD54F)
                        ConsoleLevel.INFO -> Color(0xFF8BE9FD)
                        ConsoleLevel.OUTPUT -> Color(0xFFF1F1F4)
                    }
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = lineColor,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SqlTableOutputView(output: CodeExecutionOutput.SqlTable) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (output.columns.isEmpty()) {
            Text(
                text = stringResource(
                    R.string.code_sql_affected,
                    output.statementsExecuted,
                    output.rowsAffected
                ),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF8BE9FD)
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.code_sql_rows_summary,
                    output.rows.size,
                    output.statementsExecuted
                ),
                fontSize = 11.sp,
                color = Color(0xFF8BE9FD),
                fontWeight = FontWeight.Medium
            )
            if (output.isTruncated) {
                Text(
                    text = stringResource(R.string.code_sql_truncated),
                    fontSize = 10.sp,
                    color = Color(0xFFFFD54F)
                )
            }
        }

        if (output.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.code_sql_empty_table),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = Color(0xFF8E8E9E)
            )
        } else {
            val horizontalState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(horizontalState)
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color(0xFF333342), RoundedCornerShape(6.dp))
                ) {
                    // Başlık Satırı
                    Row(modifier = Modifier.background(Color(0xFF252532))) {
                        output.columns.forEach { col ->
                            Text(
                                text = col,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(min = 84.dp, max = 180.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    output.rows.forEachIndexed { rIdx, row ->
                        val rowBg = if (rIdx % 2 == 0) Color(0xFF17171F) else Color(0xFF1C1C26)
                        Row(modifier = Modifier.background(rowBg)) {
                            row.forEach { cell ->
                                Text(
                                    text = cell,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (cell == "NULL") Color(0xFF7E7E8F) else Color(0xFFECECF1),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(min = 84.dp, max = 180.dp)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
private fun SandboxedHtmlPreviewView(htmlContent: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PureWhite)
            .border(0.5.dp, Color(0xFF3B3B4B), RoundedCornerShape(8.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
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
                    webViewClient = object : WebViewClient() {
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
                }
            },
            update = { webView ->
                if (webView.tag != htmlContent) {
                    webView.tag = htmlContent
                    webView.loadDataWithBaseURL(
                        "about:blank",
                        htmlContent,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxWidth().height(210.dp)
        )
    }
}

@Composable
private fun JsonOutputView(output: CodeExecutionOutput.JsonResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val typeLabel = when (output.rootType) {
            JsonRootType.OBJECT -> "Object"
            JsonRootType.ARRAY -> "Array"
            JsonRootType.PRIMITIVE -> "Value"
        }
        Text(
            text = stringResource(R.string.code_json_valid, typeLabel, output.itemCount),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF50FA7B),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        SelectionContainer {
            Text(
                text = output.formattedJson,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = Color(0xFFF1FA8C),
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun SyntaxCheckPassedView(output: CodeExecutionOutput.SyntaxCheckPassed) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.code_syntax_valid_msg, output.lineCount),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            color = Color(0xFF50FA7B),
            lineHeight = 16.sp
        )
        Text(
            text = stringResource(R.string.code_syntax_external_hint),
            fontSize = 10.5.sp,
            color = Color(0xFF9A9AAC)
        )
    }
}

@Composable
private fun resolveCodeErrorMessage(error: CodeExecutionError): String {
    return when (error) {
        CodeExecutionError.EmptyCode -> stringResource(R.string.code_error_empty)
        CodeExecutionError.ExecutionTimeout -> stringResource(R.string.code_error_timeout)
        CodeExecutionError.JsEngineUnavailable -> stringResource(R.string.code_error_js_unavailable)
        is CodeExecutionError.SecurityBlocked -> stringResource(R.string.code_error_security_blocked, error.operation)
        is CodeExecutionError.RuntimeOrSyntax -> error.message
        is CodeExecutionError.StaticSyntaxFailure -> {
            val lineNum = error.line ?: 1
            when (error.issueType) {
                SyntaxIssueType.UNCLOSED_DELIMITER -> stringResource(
                    R.string.code_error_unclosed_delimiter,
                    lineNum,
                    error.expectedToken?.toString().orEmpty()
                )
                SyntaxIssueType.UNEXPECTED_CLOSING_DELIMITER -> stringResource(
                    R.string.code_error_unexpected_closing,
                    lineNum,
                    error.actualToken?.toString().orEmpty()
                )
                SyntaxIssueType.MISMATCHED_DELIMITER -> stringResource(
                    R.string.code_error_mismatched_delimiter,
                    lineNum,
                    error.expectedToken?.toString().orEmpty(),
                    error.actualToken?.toString().orEmpty()
                )
                SyntaxIssueType.UNCLOSED_STRING -> stringResource(
                    R.string.code_error_unclosed_string,
                    lineNum,
                    error.expectedToken?.toString().orEmpty()
                )
                SyntaxIssueType.UNCLOSED_BLOCK_COMMENT -> stringResource(
                    R.string.code_error_unclosed_comment,
                    lineNum
                )
            }
        }
    }
}
