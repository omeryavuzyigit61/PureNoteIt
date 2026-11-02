package com.notes.purenoteit.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp

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
        "\\sum" to "∑",
        "\\prod" to "∏",
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
        "\\to" to "→",
        "\\in" to "∈",
        "\\subset" to "⊂"
    )

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
        return transformed
    }

    fun formatFormula(latex: String): String = toPlainText(latex)

    /**
     * Converts raw LaTeX text into a beautifully styled math presentation AnnotatedString.
     * Handles:
     * - Superscripts: x^2 or x^{n+1}
     * - Subscripts: x_1 or a_{ij}
     * - Fractions: \frac{a}{b} -> (a / b) with styled fraction formatting
     * - Greek & Math Symbols: \pi, \alpha, \sum, \int, \sqrt, \pm
     * - Variables formatted in mathematical italic serif
     */
    fun formatMathFormula(latex: String, isDark: Boolean = false): AnnotatedString {
        if (latex.isBlank()) return AnnotatedString("")

        var transformed = latex.trim()

        // 1. Replace LaTeX commands with unicode mathematical characters
        GREEK_LATEX_MAP.forEach { (cmd, symbol) ->
            transformed = transformed.replace(cmd, symbol)
        }

        // 2. Handle fractions \frac{num}{den} -> (num / den)
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
                c in "+-=*/<>≤≥≠≈±×÷" -> {
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
                c in "αβγδεθλμπσωΔΣΩ√∫∑∏∞·→∈⊂" -> {
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
}
