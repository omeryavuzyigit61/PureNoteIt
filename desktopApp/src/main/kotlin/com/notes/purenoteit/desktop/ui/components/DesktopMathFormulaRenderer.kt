package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DesktopMathCategory(val title: String) {
    TEMEL("Temel"),
    CEBIR("Cebir & Kesir"),
    ANALIZ("Analiz"),
    YUNAN("Yunan & Mantık"),
    SABLONLAR("Şablonlar")
}

data class DesktopMathSymbol(
    val label: String,
    val insertText: String,
    val description: String = ""
)

sealed interface DesktopMathSegment {
    data class Text(val annotatedString: AnnotatedString) : DesktopMathSegment
    data class Fraction(val numerator: AnnotatedString, val denominator: AnnotatedString) : DesktopMathSegment
}

object DesktopMathFormulaRenderer {

    val CATEGORIZED_SYMBOLS: Map<DesktopMathCategory, List<DesktopMathSymbol>> = mapOf(
        DesktopMathCategory.TEMEL to listOf(
            DesktopMathSymbol("+", "+", "Artı"),
            DesktopMathSymbol("-", "-", "Eksi"),
            DesktopMathSymbol("×", "×", "Çarpı"),
            DesktopMathSymbol("÷", "÷", "Bölü"),
            DesktopMathSymbol("±", "±", "Artı/Eksi"),
            DesktopMathSymbol("∓", "∓", "Eksi/Artı"),
            DesktopMathSymbol("=", "=", "Eşit"),
            DesktopMathSymbol("≠", "≠", "Eşit Değil"),
            DesktopMathSymbol("≈", "≈", "Yaklaşık"),
            DesktopMathSymbol("≤", "≤", "Küçük Eşit"),
            DesktopMathSymbol("≥", "≥", "Büyük Eşit"),
            DesktopMathSymbol("<", "<", "Küçük"),
            DesktopMathSymbol(">", ">", "Büyük"),
            DesktopMathSymbol("·", "·", "Nokta Çarpım"),
            DesktopMathSymbol("%", "%", "Yüzde")
        ),
        DesktopMathCategory.CEBIR to listOf(
            DesktopMathSymbol("a/b", "\\frac{a}{b}", "Dikey Kesir"),
            DesktopMathSymbol("x²", "^2", "Kare"),
            DesktopMathSymbol("xⁿ", "^{n}", "n. Kuvvet"),
            DesktopMathSymbol("xₙ", "_{n}", "İndis"),
            DesktopMathSymbol("√x", "\\sqrt{x}", "Karekök"),
            DesktopMathSymbol("ⁿ√x", "\\sqrt[n]{x}", "n. Dereceden Kök"),
            DesktopMathSymbol("( )", "()", "Parantez"),
            DesktopMathSymbol("[ ]", "[]", "Köşeli Parantez"),
            DesktopMathSymbol("{ }", "{}", "Küme Parantezi"),
            DesktopMathSymbol("|x|", "|x|", "Mutlak Değer"),
            DesktopMathSymbol("∞", "∞", "Sonsuz"),
            DesktopMathSymbol("log", "\\log(x)", "Logaritma")
        ),
        DesktopMathCategory.ANALIZ to listOf(
            DesktopMathSymbol("∫", "\\int ", "İntegral"),
            DesktopMathSymbol("∫ₐᵇ", "\\int_{a}^{b} f(x) dx", "Belirli İntegral"),
            DesktopMathSymbol("∬", "\\iint ", "İki Katlı İntegral"),
            DesktopMathSymbol("∑", "\\sum ", "Toplam"),
            DesktopMathSymbol("∑ᵢ₌₁ⁿ", "\\sum_{i=1}^{n} ", "Toplam Sınırları"),
            DesktopMathSymbol("∏", "\\prod ", "Çarpım"),
            DesktopMathSymbol("lim", "\\lim_{x \\to 0} ", "Limit"),
            DesktopMathSymbol("dx", "dx", "Diferansiyel"),
            DesktopMathSymbol("dy/dx", "\\frac{dy}{dx}", "Türev"),
            DesktopMathSymbol("∂", "\\partial ", "Kısmi Türev"),
            DesktopMathSymbol("∇", "\\nabla ", "Nabla"),
            DesktopMathSymbol("ln", "\\ln(x)", "Doğal Logaritma")
        ),
        DesktopMathCategory.YUNAN to listOf(
            DesktopMathSymbol("π", "π", "Pi"),
            DesktopMathSymbol("α", "α", "Alfa"),
            DesktopMathSymbol("β", "β", "Beta"),
            DesktopMathSymbol("γ", "γ", "Gama"),
            DesktopMathSymbol("θ", "θ", "Teta"),
            DesktopMathSymbol("λ", "λ", "Lambda"),
            DesktopMathSymbol("μ", "μ", "Mü"),
            DesktopMathSymbol("σ", "σ", "Sigma"),
            DesktopMathSymbol("Δ", "Δ", "Delta"),
            DesktopMathSymbol("Ω", "Ω", "Omega"),
            DesktopMathSymbol("ω", "ω", "Küçük Omega"),
            DesktopMathSymbol("∈", "∈", "Elemanıdır"),
            DesktopMathSymbol("∉", "∉", "Elemanı Değildir"),
            DesktopMathSymbol("⊂", "⊂", "Alt Kümesi"),
            DesktopMathSymbol("∪", "∪", "Birleşim"),
            DesktopMathSymbol("∩", "∩", "Kesişim"),
            DesktopMathSymbol("∀", "∀", "Her"),
            DesktopMathSymbol("∃", "∃", "En Az Bir"),
            DesktopMathSymbol("→", "→", "Gider"),
            DesktopMathSymbol("⇒", "⇒", "Gerektirir")
        ),
        DesktopMathCategory.SABLONLAR to listOf(
            DesktopMathSymbol("İkinci Dereceden Kökler", "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
            DesktopMathSymbol("Euler Özdeşliği", "e^{i\\pi} + 1 = 0"),
            DesktopMathSymbol("Pisagor Teoremi", "a^2 + b^2 = c^2"),
            DesktopMathSymbol("Temel Limit", "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1"),
            DesktopMathSymbol("Gauss İntegrali", "\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}"),
            DesktopMathSymbol("Türev Limit Tanımı", "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}"),
            DesktopMathSymbol("Kütle-Enerji", "E = mc^2"),
            DesktopMathSymbol("2×2 Matris", "|A| = a_{11}a_{22} - a_{12}a_{21}")
        )
    )

    private val GREEK_LATEX_MAP = mapOf(
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\pi" to "π",
        "\\sigma" to "σ",
        "\\omega" to "ω",
        "\\Delta" to "Δ",
        "\\Sigma" to "Σ",
        "\\Omega" to "Ω",
        "\\sqrt" to "√",
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\lim" to "lim",
        "\\infty" to "∞",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\times" to "×",
        "\\div" to "÷",
        "\\leq" to "≤",
        "\\geq" to "≥",
        "\\neq" to "≠",
        "\\approx" to "≈",
        "\\cdot" to "·",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\to" to "→",
        "\\Rightarrow" to "⇒",
        "\\implies" to "⇒",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\cot" to "cot",
        "\\log" to "log",
        "\\ln" to "ln"
    )

    private fun toSuperscript(s: String): String {
        return s.map { c ->
            when (c) {
                '0' -> '⁰'
                '1' -> '¹'
                '2' -> '²'
                '3' -> '³'
                '4' -> '⁴'
                '5' -> '⁵'
                '6' -> '⁶'
                '7' -> '⁷'
                '8' -> '⁸'
                '9' -> '⁹'
                'n' -> 'ⁿ'
                'i' -> 'ⁱ'
                '+' -> '⁺'
                '-' -> '⁻'
                else -> c
            }
        }.joinToString("")
    }

    fun formatMathFormula(latex: String, isDark: Boolean = true): AnnotatedString {
        if (latex.isBlank()) return AnnotatedString("")

        var transformed = latex.trim()
        GREEK_LATEX_MAP.forEach { (cmd, symbol) ->
            transformed = transformed.replace(cmd, symbol)
        }

        val nthRootRegex = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")
        transformed = nthRootRegex.replace(transformed) { m ->
            "${toSuperscript(m.groupValues[1])}√(${m.groupValues[2]})"
        }
        val sqrtRegex = Regex("""\\sqrt\{([^}]+)\}""")
        transformed = sqrtRegex.replace(transformed) { m ->
            "√(${m.groupValues[1]})"
        }

        val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
        transformed = fracRegex.replace(transformed) { m ->
            "(${m.groupValues[1]} / ${m.groupValues[2]})"
        }

        val primaryColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFC67C00)
        val varColor = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
        val operatorColor = if (isDark) Color(0xFFFF8A65) else Color(0xFFD84315)

        val builder = AnnotatedString.Builder()
        var i = 0
        while (i < transformed.length) {
            val c = transformed[i]
            when {
                c == '^' -> {
                    i++
                    if (i < transformed.length && transformed[i] == '{') {
                        val closeIdx = transformed.indexOf('}', i)
                        if (closeIdx != -1) {
                            val supText = transformed.substring(i + 1, closeIdx)
                            builder.pushStyle(
                                SpanStyle(
                                    fontSize = 11.sp,
                                    baselineShift = BaselineShift.Superscript,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            )
                            builder.append(supText)
                            builder.pop()
                            i = closeIdx + 1
                            continue
                        }
                    } else if (i < transformed.length) {
                        builder.pushStyle(
                            SpanStyle(
                                fontSize = 11.sp,
                                baselineShift = BaselineShift.Superscript,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )
                        builder.append(transformed[i])
                        builder.pop()
                        i++
                        continue
                    }
                }

                c == '_' -> {
                    i++
                    if (i < transformed.length && transformed[i] == '{') {
                        val closeIdx = transformed.indexOf('}', i)
                        if (closeIdx != -1) {
                            val subText = transformed.substring(i + 1, closeIdx)
                            builder.pushStyle(
                                SpanStyle(
                                    fontSize = 11.sp,
                                    baselineShift = BaselineShift.Subscript,
                                    color = primaryColor
                                )
                            )
                            builder.append(subText)
                            builder.pop()
                            i = closeIdx + 1
                            continue
                        }
                    } else if (i < transformed.length) {
                        builder.pushStyle(
                            SpanStyle(
                                fontSize = 11.sp,
                                baselineShift = BaselineShift.Subscript,
                                color = primaryColor
                            )
                        )
                        builder.append(transformed[i])
                        builder.pop()
                        i++
                        continue
                    }
                }

                c in "+-=*/<>≤≥≠≈±×÷∓" -> {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = operatorColor))
                    builder.append(" $c ")
                    builder.pop()
                    i++
                }

                c in "αβγδεθλμπσωΔΣΩ√∫∬∑∏∞·→⇒∈∉⊂∪∩∀∃∂∇" -> {
                    builder.pushStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryColor))
                    builder.append(c)
                    builder.pop()
                    i++
                }

                c.isLetter() -> {
                    builder.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = varColor
                        )
                    )
                    builder.append(c)
                    builder.pop()
                    i++
                }

                else -> {
                    builder.append(c)
                    i++
                }
            }
        }
        return builder.toAnnotatedString()
    }

    fun parseMathSegments(latex: String, isDark: Boolean = true): List<DesktopMathSegment> {
        if (latex.isBlank()) return emptyList()

        val segments = mutableListOf<DesktopMathSegment>()
        val fracRegex = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
        var lastIndex = 0

        for (match in fracRegex.findAll(latex)) {
            if (match.range.first > lastIndex) {
                val textPart = latex.substring(lastIndex, match.range.first)
                if (textPart.isNotEmpty()) {
                    segments.add(DesktopMathSegment.Text(formatMathFormula(textPart, isDark)))
                }
            }
            segments.add(
                DesktopMathSegment.Fraction(
                    numerator = formatMathFormula(match.groupValues[1], isDark),
                    denominator = formatMathFormula(match.groupValues[2], isDark)
                )
            )
            lastIndex = match.range.last + 1
        }

        if (lastIndex < latex.length) {
            val remaining = latex.substring(lastIndex)
            if (remaining.isNotEmpty()) {
                segments.add(DesktopMathSegment.Text(formatMathFormula(remaining, isDark)))
            }
        }

        if (segments.isEmpty()) {
            segments.add(DesktopMathSegment.Text(formatMathFormula(latex, isDark)))
        }
        return segments
    }
}

@Composable
fun DesktopMathFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    fontSize: TextUnit = 18.sp
) {
    val segments = remember(latex, isDark) {
        DesktopMathFormulaRenderer.parseMathSegments(latex, isDark)
    }

    val fractionLineColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)

    if (segments.isEmpty() || latex.isBlank()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "—",
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is DesktopMathSegment.Text -> {
                        Text(
                            text = segment.annotatedString,
                            fontSize = fontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (fontSize.value * 1.3f).sp
                        )
                    }
                    is DesktopMathSegment.Fraction -> {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Min)
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = segment.numerator,
                                fontSize = (fontSize.value * 0.85f).sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.8.dp)
                                    .background(fractionLineColor, RoundedCornerShape(1.dp))
                            )
                            Text(
                                text = segment.denominator,
                                fontSize = (fontSize.value * 0.85f).sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
