package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.engine.Lexer

class PseudoCodeVisualTransformation(
    private val isDark: Boolean
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = highlightPseudoCode(text.text, isDark),
            offsetMapping = OffsetMapping.Identity
        )
    }

    companion object {
        private val structKeywords = setOf(
            "algorithme", "debut", "fin", "var", "const", "tableau",
            "structure", "finstructure", "fonction", "procedure", "retourner"
        )
        private val controlKeywords = setOf(
            "si", "alors", "sinon", "finsi", "selon", "defaut", "finselon",
            "pour", "de", "a", "pas", "finpour", "tant", "que", "faire",
            "fintq", "fintantque", "fintant", "repeter", "jusqu'a"
        )
        private val typeKeywords = setOf(
            "entier", "reel", "caractere", "chaine", "booleen", "pointeur", "sur"
        )
        private val builtinKeywords = setOf(
            "lire", "ecrire", "nouveau", "liberer", "nil",
            "div", "mod", "et", "ou", "non", "vrai", "faux"
        )

        fun highlightPseudoCode(source: String, isDark: Boolean): AnnotatedString {
            val kwColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
            val ctrlColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
            val typeColor = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
            val builtinColor = if (isDark) Color(0xFF22D3EE) else Color(0xFF0E7490)
            val stringColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
            val numberColor = if (isDark) Color(0xFFF472B6) else Color(0xFFBE185D)
            val commentColor = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
            val arrowColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

            return buildAnnotatedString {
                append(source)
                var i = 0
                val n = source.length
                while (i < n) {
                    val ch = source[i]
                    when {
                        // Line comment
                        ch == '/' && i + 1 < n && source[i + 1] == '/' -> {
                            val start = i
                            while (i < n && source[i] != '\n') i++
                            addStyle(
                                SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                                start,
                                i
                            )
                        }
                        // Block comment
                        ch == '/' && i + 1 < n && source[i + 1] == '*' -> {
                            val start = i
                            i += 2
                            while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                            i = (i + 2).coerceAtMost(n)
                            addStyle(
                                SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                                start,
                                i
                            )
                        }
                        // String literal
                        ch == '"' -> {
                            val start = i
                            i++
                            while (i < n && source[i] != '"' && source[i] != '\n') {
                                if (source[i] == '\\' && i + 1 < n) i += 2 else i++
                            }
                            if (i < n && source[i] == '"') i++
                            addStyle(SpanStyle(color = stringColor), start, i)
                        }
                        // Char literal
                        ch == '\'' -> {
                            val start = i
                            i++
                            while (i < n && source[i] != '\'' && source[i] != '\n') i++
                            if (i < n && source[i] == '\'') i++
                            addStyle(SpanStyle(color = stringColor), start, i)
                        }
                        // Assignment arrow
                        ch == '←' -> {
                            addStyle(SpanStyle(color = arrowColor, fontWeight = FontWeight.Bold), i, i + 1)
                            i++
                        }
                        ch == '<' && i + 1 < n && source[i + 1] == '-' -> {
                            addStyle(SpanStyle(color = arrowColor, fontWeight = FontWeight.Bold), i, i + 2)
                            i += 2
                        }
                        // Number
                        ch.isDigit() -> {
                            val start = i
                            while (i < n && (source[i].isDigit() || source[i] == '.')) i++
                            addStyle(SpanStyle(color = numberColor), start, i)
                        }
                        // Identifier / Keyword
                        ch.isLetter() || ch == '_' -> {
                            val start = i
                            while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                            // Handle Jusqu'à / Jusqu'a
                            val wordSoFar = Lexer.stripAccents(source.substring(start, i)).lowercase()
                            if (wordSoFar == "jusqu" && i + 1 < n && source[i] == '\'' &&
                                (source[i + 1] == 'à' || source[i + 1] == 'a' || source[i + 1] == 'À' || source[i + 1] == 'A')
                            ) {
                                i += 2
                            }
                            val norm = Lexer.stripAccents(source.substring(start, i)).lowercase()
                            when {
                                norm in structKeywords -> addStyle(
                                    SpanStyle(color = kwColor, fontWeight = FontWeight.Bold),
                                    start,
                                    i
                                )
                                norm in controlKeywords -> addStyle(
                                    SpanStyle(color = ctrlColor, fontWeight = FontWeight.Bold),
                                    start,
                                    i
                                )
                                norm in typeKeywords -> addStyle(
                                    SpanStyle(color = typeColor, fontWeight = FontWeight.SemiBold),
                                    start,
                                    i
                                )
                                norm in builtinKeywords -> addStyle(
                                    SpanStyle(color = builtinColor, fontWeight = FontWeight.SemiBold),
                                    start,
                                    i
                                )
                            }
                        }
                        else -> i++
                    }
                }
            }
        }
    }
}
