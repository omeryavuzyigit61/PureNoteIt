package com.notes.purenoteit.util

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

enum class MathCategory(val title: String) {
    TEMEL("Temel"),
    CEBIR("Cebir & Kesir"),
    ANALIZ("Analiz"),
    YUNAN("Yunan & Mantık"),
    SABLONLAR("Şablonlar")
}

data class MathSymbol(
    val label: String,
    val insertText: String,
    val description: String = ""
)

sealed interface MathSegment {
    data class Text(val annotatedString: AnnotatedString) : MathSegment
    data class Fraction(val numerator: AnnotatedString, val denominator: AnnotatedString) : MathSegment
}

object MathFormulaRenderer {

    val QUICK_MATH_SYMBOLS = listOf(
        "π" to "Pi",
        "√" to "Kök",
        "∫" to "İntegral",
        "∑" to "Toplam",
        "∞" to "Sonsuz",
        "±" to "Artı/Eksi",
        "≤" to "Küçük Eşit",
        "≥" to "Büyük Eşit",
        "≠" to "Eşit Değil",
        "≈" to "Yaklaşık",
        "×" to "Çarpı",
        "÷" to "Bölü",
        "²" to "Kare",
        "³" to "Küp",
        "α" to "Alfa",
        "β" to "Beta",
        "θ" to "Teta",
        "λ" to "Lambda",
        "μ" to "Mü"
    )

    val CATEGORIZED_SYMBOLS: Map<MathCategory, List<MathSymbol>> = mapOf(
        MathCategory.TEMEL to listOf(
            MathSymbol("+", "+", "Artı"),
            MathSymbol("-", "-", "Eksi"),
            MathSymbol("×", "×", "Çarpı"),
            MathSymbol("÷", "÷", "Bölü"),
            MathSymbol("±", "±", "Artı/Eksi"),
            MathSymbol("∓", "∓", "Eksi/Artı"),
            MathSymbol("=", "=", "Eşit"),
            MathSymbol("≠", "≠", "Eşit Değil"),
            MathSymbol("≈", "≈", "Yaklaşık"),
            MathSymbol("≤", "≤", "Küçük Eşit"),
            MathSymbol("≥", "≥", "Büyük Eşit"),
            MathSymbol("<", "<", "Küçük"),
            MathSymbol(">", ">", "Büyük"),
            MathSymbol("·", "·", "Nokta Çarpım"),
            MathSymbol("%", "%", "Yüzde"),
            MathSymbol("‰", "‰", "Binde")
        ),
        MathCategory.CEBIR to listOf(
            MathSymbol("a/b", "\\frac{a}{b}", "Dikey Kesir"),
            MathSymbol("x²", "^2", "Kare"),
            MathSymbol("xⁿ", "^{n}", "n. Kuvvet"),
            MathSymbol("xₙ", "_{n}", "İndis"),
            MathSymbol("√x", "\\sqrt{x}", "Karekök"),
            MathSymbol("ⁿ√x", "\\sqrt[n]{x}", "n. Dereceden Kök"),
            MathSymbol("( )", "()", "Parantez"),
            MathSymbol("[ ]", "[]", "Köşeli Parantez"),
            MathSymbol("{ }", "{}", "Küme Parantezi"),
            MathSymbol("|x|", "|x|", "Mutlak Değer"),
            MathSymbol("∞", "∞", "Sonsuz"),
            MathSymbol("log", "\\log(x)", "Logaritma")
        ),
        MathCategory.ANALIZ to listOf(
            MathSymbol("∫", "\\int ", "İntegral"),
            MathSymbol("∫ₐᵇ", "\\int_{a}^{b} f(x) dx", "Belirli İntegral"),
            MathSymbol("∬", "\\iint ", "İki Katlı İntegral"),
            MathSymbol("∑", "\\sum ", "Toplam"),
            MathSymbol("∑ᵢ₌₁ⁿ", "\\sum_{i=1}^{n} ", "Toplam Sınırları"),
            MathSymbol("∏", "\\prod ", "Çarpım"),
            MathSymbol("lim", "\\lim_{x \\to 0} ", "Limit"),
            MathSymbol("dx", "dx", "Diferansiyel"),
            MathSymbol("dy/dx", "\\frac{dy}{dx}", "Türev"),
            MathSymbol("∂", "\\partial ", "Kısmi Türev"),
            MathSymbol("∇", "\\nabla ", "Nabla (Gradyan)"),
            MathSymbol("ln", "\\ln(x)", "Doğal Logaritma")
        ),
        MathCategory.YUNAN to listOf(
            MathSymbol("π", "π", "Pi"),
            MathSymbol("α", "α", "Alfa"),
            MathSymbol("β", "β", "Beta"),
            MathSymbol("γ", "γ", "Gama"),
            MathSymbol("θ", "θ", "Teta"),
            MathSymbol("λ", "λ", "Lambda"),
            MathSymbol("μ", "μ", "Mü"),
            MathSymbol("σ", "σ", "Sigma"),
            MathSymbol("Δ", "Δ", "Delta"),
            MathSymbol("Ω", "Ω", "Omega"),
            MathSymbol("ω", "ω", "Küçük Omega"),
            MathSymbol("∈", "∈", "Elemanıdır"),
            MathSymbol("∉", "∉", "Elemanı Değildir"),
            MathSymbol("⊂", "⊂", "Alt Kümesi"),
            MathSymbol("∪", "∪", "Birleşim"),
            MathSymbol("∩", "∩", "Kesişim"),
            MathSymbol("∀", "∀", "Her"),
            MathSymbol("∃", "∃", "En Az Bir"),
            MathSymbol("→", "→", "Gider"),
            MathSymbol("⇒", "⇒", "Gerektirir")
        ),
        MathCategory.SABLONLAR to listOf(
            MathSymbol("İkinci Dereceden Kökler", "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
            MathSymbol("Euler Özdeşliği", "e^{i\\pi} + 1 = 0"),
            MathSymbol("Pisagor Teoremi", "a^2 + b^2 = c^2"),
            MathSymbol("Temel Limit", "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1"),
            MathSymbol("Gauss İntegrali", "\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}"),
            MathSymbol("Türev Limit Tanımı", "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}")
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

    fun toSuperscript(s: String): String {
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

    /**
     * Plain text unicode representation of LaTeX formula (for PDF and text exports)
     */
    fun toPlainText(latex: String): String {
        if (latex.isBlank()) return ""
        var transformed = latex.trim()
        GREEK_LATEX_MAP.forEach { (cmd, symbol) ->
            transformed = transformed.replace(cmd, symbol)
        }
        val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
        transformed = fracRegex.replace(transformed) { m ->
            "(${m.groupValues[1]} / ${m.groupValues[2]})"
        }
        val nthRootRegex = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")
        transformed = nthRootRegex.replace(transformed) { m ->
            "${toSuperscript(m.groupValues[1])}√(${m.groupValues[2]})"
        }
        val sqrtRegex = Regex("""\\sqrt\{([^}]+)\}""")
        transformed = sqrtRegex.replace(transformed) { m ->
            "√(${m.groupValues[1]})"
        }
        return transformed
    }

    fun formatFormula(latex: String): String = toPlainText(latex)

    /**
     * Converts raw LaTeX text into a beautifully styled math presentation AnnotatedString.
     */
    fun formatMathFormula(latex: String, isDark: Boolean = false): AnnotatedString {
        if (latex.isBlank()) return AnnotatedString("")

        var transformed = latex.trim()

        // 1. Replace LaTeX commands with unicode mathematical characters
        GREEK_LATEX_MAP.forEach { (cmd, symbol) ->
            transformed = transformed.replace(cmd, symbol)
        }

        // 2. Handle roots
        val nthRootRegex = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")
        transformed = nthRootRegex.replace(transformed) { m ->
            "${toSuperscript(m.groupValues[1])}√(${m.groupValues[2]})"
        }
        val sqrtRegex = Regex("""\\sqrt\{([^}]+)\}""")
        transformed = sqrtRegex.replace(transformed) { m ->
            "√(${m.groupValues[1]})"
        }

        // 3. Fallback handle fractions if called outside segment parser
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
                // Superscript: x^2 or x^{abc}
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

                // Subscript: x_1 or x_{abc}
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

                // Math Operators (+, -, =, *, /, <, >)
                c in "+-=*/<>≤≥≠≈±×÷∓" -> {
                    builder.pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = operatorColor
                        )
                    )
                    builder.append(" $c ")
                    builder.pop()
                    i++
                }

                // Greek & Special Symbols
                c in "αβγδεθλμπσωΔΣΩ√∫∬∑∏lim∞·→⇒∈∉⊂∪∩∀∃∂∇" -> {
                    builder.pushStyle(
                        SpanStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    )
                    builder.append(c)
                    builder.pop()
                    i++
                }

                // Mathematical Variables (Letters in Serif Italic)
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

    /**
     * Parses a LaTeX formula into visual segments for rich vertical fraction and inline rendering.
     */
    fun parseMathSegments(latex: String, isDark: Boolean = false): List<MathSegment> {
        if (latex.isBlank()) return emptyList()

        val segments = mutableListOf<MathSegment>()
        val fracRegex = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
        var lastIndex = 0

        for (match in fracRegex.findAll(latex)) {
            if (match.range.first > lastIndex) {
                val textPart = latex.substring(lastIndex, match.range.first)
                if (textPart.isNotEmpty()) {
                    segments.add(MathSegment.Text(formatMathFormula(textPart, isDark)))
                }
            }
            val numStr = match.groupValues[1]
            val denStr = match.groupValues[2]
            segments.add(
                MathSegment.Fraction(
                    numerator = formatMathFormula(numStr, isDark),
                    denominator = formatMathFormula(denStr, isDark)
                )
            )
            lastIndex = match.range.last + 1
        }

        if (lastIndex < latex.length) {
            val remaining = latex.substring(lastIndex)
            if (remaining.isNotEmpty()) {
                segments.add(MathSegment.Text(formatMathFormula(remaining, isDark)))
            }
        }

        if (segments.isEmpty()) {
            segments.add(MathSegment.Text(formatMathFormula(latex, isDark)))
        }

        return segments
    }
}

@Composable
fun MathFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    fontSize: TextUnit = 18.sp
) {
    val segments = remember(latex, isDark) {
        MathFormulaRenderer.parseMathSegments(latex, isDark)
    }

    val fractionLineColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)

    if (segments.isEmpty() || latex.isBlank()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is MathSegment.Text -> {
                        Text(
                            text = segment.annotatedString,
                            fontSize = fontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (fontSize.value * 1.3f).sp
                        )
                    }
                    is MathSegment.Fraction -> {
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
