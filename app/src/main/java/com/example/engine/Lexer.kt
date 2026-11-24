package com.example.engine

import java.text.Normalizer

class Lexer(
    private val source: String,
    private val strictness: StrictnessLevel = StrictnessLevel.STANDARD_IFRI
) {
    private val tokens = mutableListOf<Token>()
    private val diagnostics = mutableListOf<Diagnostic>()
    private var index = 0
    private var line = 1
    private var column = 1

    data class LexResult(
        val tokens: List<Token>,
        val diagnostics: List<Diagnostic>
    )

    fun tokenize(): LexResult {
        while (index < source.length) {
            val ch = source[index]
            when {
                ch == '\n' -> {
                    index++
                    line++
                    column = 1
                }
                ch == '\r' || ch == ' ' || ch == '\t' -> {
                    index++
                    column++
                }
                ch == '/' && peek(1) == '/' -> skipLineComment()
                ch == '/' && peek(1) == '*' -> skipBlockComment()
                ch == '"' -> readStringLiteral()
                ch == '\'' -> readCharLiteral()
                ch == '←' -> {
                    addToken(TokenType.ASSIGN, "←", line, column, 1)
                    advance()
                }
                ch == '≠' -> {
                    addToken(TokenType.NEQ, "≠", line, column, 1)
                    advance()
                }
                ch == '≤' -> {
                    addToken(TokenType.LTE, "≤", line, column, 1)
                    advance()
                }
                ch == '≥' -> {
                    addToken(TokenType.GTE, "≥", line, column, 1)
                    advance()
                }
                ch == '<' -> readLessThanOrAssign()
                ch == '>' -> {
                    if (peek(1) == '=') {
                        addToken(TokenType.GTE, ">=", line, column, 2)
                        advance(2)
                    } else {
                        addToken(TokenType.GT, ">", line, column, 1)
                        advance()
                    }
                }
                ch == '=' -> {
                    if (peek(1) == '=') {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "L'opérateur '==' appartient au langage C, pas au pseudo-code.",
                                chapterRef = "Chap. 2, §2 (c)",
                                hint = "Utilisez '=' pour tester l'égalité, et '←' (ou '<-') pour l'affectation."
                            )
                        )
                        addToken(TokenType.EQ, "==", line, column, 2)
                        advance(2)
                    } else {
                        addToken(TokenType.EQ, "=", line, column, 1)
                        advance()
                    }
                }
                ch == '!' -> {
                    if (peek(1) == '=') {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "L'opérateur '!=' appartient au langage C.",
                                chapterRef = "Chap. 2, §2 (c)",
                                hint = "Utilisez '≠' ou '<>' en pseudo-code."
                            )
                        )
                        addToken(TokenType.NEQ, "!=", line, column, 2)
                        advance(2)
                    } else {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "L'opérateur '!' appartient au langage C.",
                                chapterRef = "Chap. 2, §2 (b)",
                                hint = "Utilisez l'opérateur logique 'NON' en pseudo-code."
                            )
                        )
                        addToken(TokenType.NON, "!", line, column, 1)
                        advance()
                    }
                }
                ch == '&' -> {
                    if (peek(1) == '&') {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "L'opérateur '&&' appartient au langage C.",
                                chapterRef = "Chap. 2, §2 (b)",
                                hint = "Utilisez l'opérateur logique 'ET' en pseudo-code."
                            )
                        )
                        addToken(TokenType.ET, "&&", line, column, 2)
                        advance(2)
                    } else {
                        addToken(TokenType.AMPERSAND, "&", line, column, 1)
                        advance()
                    }
                }
                ch == '|' && peek(1) == '|' -> {
                    diagnostics.add(
                        Diagnostic(
                            line = line,
                            column = column,
                            message = "L'opérateur '||' appartient au langage C.",
                            chapterRef = "Chap. 2, §2 (b)",
                            hint = "Utilisez l'opérateur logique 'OU' en pseudo-code."
                        )
                    )
                    addToken(TokenType.OU, "||", line, column, 2)
                    advance(2)
                }
                ch == '%' -> {
                    diagnostics.add(
                        Diagnostic(
                            line = line,
                            column = column,
                            message = "L'opérateur '%' appartient au langage C.",
                            chapterRef = "Chap. 1, §4.3.1 / Chap. 2, §2 (a)",
                            hint = "Utilisez l'opérateur 'mod' en pseudo-code."
                        )
                    )
                    addToken(TokenType.MOD, "%", line, column, 1)
                    advance()
                }
                ch == '{' || ch == '}' -> {
                    diagnostics.add(
                        Diagnostic(
                            line = line,
                            column = column,
                            message = "Les accolades '$ch' appartiennent au langage C.",
                            chapterRef = "Chap. 1, §4.1",
                            hint = "Utilisez 'Début' ... 'Fin' (ou 'FinSi', 'FinPour', 'FinTq', 'FinStructure') en pseudo-code."
                        )
                    )
                    advance()
                }
                ch == ':' -> {
                    if (peek(1) == '=') {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "L'opérateur ':=' appartient au Pascal.",
                                chapterRef = "Chap. 2, §2",
                                hint = "Utilisez la flèche '←' ou '<-' pour l'affectation."
                            )
                        )
                        addToken(TokenType.ASSIGN, ":=", line, column, 2)
                        advance(2)
                    } else {
                        addToken(TokenType.COLON, ":", line, column, 1)
                        advance()
                    }
                }
                ch == '+' -> {
                    if (peek(1) == '+') {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "L'incrémentation '++' appartient au langage C.",
                                chapterRef = "Chap. 4, §7",
                                hint = "Utilisez 'i ← i + 1 ;' en pseudo-code."
                            )
                        )
                        advance(2)
                    } else {
                        addToken(TokenType.PLUS, "+", line, column, 1)
                        advance()
                    }
                }
                ch == '-' -> {
                    if (peek(1) == '-') {
                        diagnostics.add(
                            Diagnostic(
                                line = line,
                                column = column,
                                message = "La décrémentation '--' appartient au langage C.",
                                chapterRef = "Chap. 4, §7",
                                hint = "Utilisez 'i ← i - 1 ;' en pseudo-code."
                            )
                        )
                        advance(2)
                    } else {
                        addToken(TokenType.MINUS, "-", line, column, 1)
                        advance()
                    }
                }
                ch == '*' -> {
                    if (peek(1) == '*') {
                        addToken(TokenType.POWER, "**", line, column, 2)
                        advance(2)
                    } else {
                        addToken(TokenType.STAR, "*", line, column, 1)
                        advance()
                    }
                }
                ch == '/' -> {
                    addToken(TokenType.SLASH, "/", line, column, 1)
                    advance()
                }
                ch == '^' -> {
                    addToken(TokenType.POWER, "^", line, column, 1)
                    advance()
                }
                ch == ';' -> {
                    addToken(TokenType.SEMICOLON, ";", line, column, 1)
                    advance()
                }
                ch == ',' -> {
                    addToken(TokenType.COMMA, ",", line, column, 1)
                    advance()
                }
                ch == '.' -> {
                    addToken(TokenType.DOT, ".", line, column, 1)
                    advance()
                }
                ch == '(' -> {
                    addToken(TokenType.LPAREN, "(", line, column, 1)
                    advance()
                }
                ch == ')' -> {
                    addToken(TokenType.RPAREN, ")", line, column, 1)
                    advance()
                }
                ch == '[' -> {
                    addToken(TokenType.LBRACKET, "[", line, column, 1)
                    advance()
                }
                ch == ']' -> {
                    addToken(TokenType.RBRACKET, "]", line, column, 1)
                    advance()
                }
                ch.isDigit() -> readNumber()
                isIdentStart(ch) -> readIdentifierOrKeyword()
                else -> {
                    diagnostics.add(
                        Diagnostic(
                            line = line,
                            column = column,
                            message = "Caractère invalide ou non reconnu : '$ch'.",
                            chapterRef = "Chap. 1, §4"
                        )
                    )
                    advance()
                }
            }
        }
        tokens.add(Token(TokenType.EOF, "", SourceSpan(line, column, 0)))
        return LexResult(tokens, diagnostics)
    }

    private fun readLessThanOrAssign() {
        val startCol = column
        when (peek(1)) {
            '-' -> {
                addToken(TokenType.ASSIGN, "<-", line, startCol, 2)
                advance(2)
            }
            '>' -> {
                addToken(TokenType.NEQ, "<>", line, startCol, 2)
                advance(2)
            }
            '=' -> {
                addToken(TokenType.LTE, "<=", line, startCol, 2)
                advance(2)
            }
            else -> {
                addToken(TokenType.LT, "<", line, startCol, 1)
                advance()
            }
        }
    }

    private fun skipLineComment() {
        while (index < source.length && source[index] != '\n') {
            advance()
        }
    }

    private fun skipBlockComment() {
        val startLine = line
        val startCol = column
        advance(2) // skip /*
        while (index < source.length) {
            if (source[index] == '*' && peek(1) == '/') {
                advance(2)
                return
            }
            if (source[index] == '\n') {
                index++
                line++
                column = 1
            } else {
                advance()
            }
        }
        diagnostics.add(
            Diagnostic(
                line = startLine,
                column = startCol,
                message = "Commentaire de bloc '/*' non fermé avant la fin de l'algorithme.",
                chapterRef = "Chap. 4, §3"
            )
        )
    }

    private fun readStringLiteral() {
        val startLine = line
        val startCol = column
        advance() // opening "
        val sb = StringBuilder()
        while (index < source.length && source[index] != '"' && source[index] != '\n') {
            if (source[index] == '\\' && index + 1 < source.length) {
                val escaped = source[index + 1]
                when (escaped) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(escaped)
                }
                advance(2)
            } else {
                sb.append(source[index])
                advance()
            }
        }
        if (index >= source.length || source[index] != '"') {
            diagnostics.add(
                Diagnostic(
                    line = startLine,
                    column = startCol,
                    message = "Chaîne de caractères non fermée (guillemet '\"' manquant).",
                    chapterRef = "Chap. 1, §4.3.5"
                )
            )
        } else {
            advance() // closing "
        }
        addToken(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol, sb.length + 2)
    }

    private fun readCharLiteral() {
        val startLine = line
        val startCol = column
        advance() // opening '
        val sb = StringBuilder()
        while (index < source.length && source[index] != '\'' && source[index] != '\n') {
            sb.append(source[index])
            advance()
        }
        if (index >= source.length || source[index] != '\'') {
            diagnostics.add(
                Diagnostic(
                    line = startLine,
                    column = startCol,
                    message = "Littéral caractère non fermé (apostrophe \"'\" manquante).",
                    chapterRef = "Chap. 1, §4.3.3"
                )
            )
        } else {
            advance()
        }
        val content = sb.toString()
        if (content.length != 1) {
            diagnostics.add(
                Diagnostic(
                    line = startLine,
                    column = startCol,
                    message = "Un type caractère doit contenir exactement un seul caractère entre apostrophes ('$content').",
                    chapterRef = "Chap. 1, §4.3.3",
                    hint = "Utilisez des guillemets doubles \"...\" pour une chaîne de caractères."
                )
            )
        }
        addToken(TokenType.CHAR_LITERAL, content.take(1), startLine, startCol, content.length + 2)
    }

    private fun readNumber() {
        val startCol = column
        val sb = StringBuilder()
        while (index < source.length && source[index].isDigit()) {
            sb.append(source[index])
            advance()
        }
        if (index < source.length && source[index] == '.' && peek(1).isDigit()) {
            sb.append('.')
            advance()
            while (index < source.length && source[index].isDigit()) {
                sb.append(source[index])
                advance()
            }
            addToken(TokenType.REAL_LITERAL, sb.toString(), line, startCol, sb.length)
        } else {
            addToken(TokenType.INT_LITERAL, sb.toString(), line, startCol, sb.length)
        }
    }

    private fun readIdentifierOrKeyword() {
        val startCol = column
        val startLine = line
        val sb = StringBuilder()
        while (index < source.length && isIdentPart(source[index])) {
            sb.append(source[index])
            advance()
        }

        // Special handling for Jusqu'à / Jusqu'a
        val raw = sb.toString()
        val normalizedRaw = stripAccents(raw).lowercase()
        if (normalizedRaw == "jusqu" && index < source.length && source[index] == '\'') {
            val nextChar = peek(1)
            if (nextChar == 'à' || nextChar == 'a' || nextChar == 'À' || nextChar == 'A') {
                val combined = "$raw'$nextChar"
                advance(2)
                if (strictness == StrictnessLevel.ULTRA_STRICT && nextChar != 'à' && nextChar != 'À') {
                    diagnostics.add(
                        Diagnostic(
                            line = startLine,
                            column = startCol,
                            message = "Mode Ultra-Strict : le mot-clé '$combined' doit comporter un accent grave ('Jusqu'à').",
                            chapterRef = "Chap. 4, §6",
                            severity = DiagnosticSeverity.ERROR
                        )
                    )
                }
                addToken(TokenType.JUSQU_A, combined, startLine, startCol, combined.length)
                return
            }
        }

        // Check if C keyword leaked
        cLeakKeywords[normalizedRaw]?.let { (cMsg, hint) ->
            diagnostics.add(
                Diagnostic(
                    line = startLine,
                    column = startCol,
                    message = cMsg,
                    chapterRef = "Chap. 1 & 2 (Syntaxe pseudo-code)",
                    hint = hint
                )
            )
        }

        // Handle accented 'à' preposition strictly vs variable 'a'
        if (raw.lowercase() == "à") {
            addToken(TokenType.A, raw, startLine, startCol, raw.length)
            return
        }

        // Check accented keywords
        val kwType = keywordMap[normalizedRaw]
        if (kwType != null) {
            val expectedAccented = accentedPreferred[normalizedRaw]
            if (expectedAccented != null && raw.lowercase() != expectedAccented) {
                val sev = if (strictness == StrictnessLevel.ULTRA_STRICT) {
                    DiagnosticSeverity.ERROR
                } else {
                    DiagnosticSeverity.WARNING
                }
                if (strictness == StrictnessLevel.ULTRA_STRICT) {
                    diagnostics.add(
                        Diagnostic(
                            line = startLine,
                            column = startCol,
                            message = "Mode Ultra-Strict : accent requis sur le mot-clé '$raw' → '${expectedAccented.replaceFirstChar { it.uppercase() }}'.",
                            chapterRef = "Chap. 1, §4",
                            severity = sev
                        )
                    )
                }
            }
            addToken(kwType, raw, startLine, startCol, raw.length)
            return
        }

        // Handle boolean literals
        if (normalizedRaw == "vrai" || normalizedRaw == "faux") {
            addToken(TokenType.BOOL_LITERAL, raw, startLine, startCol, raw.length)
            return
        }

        addToken(TokenType.IDENTIFIER, raw, startLine, startCol, raw.length)
    }

    private fun isIdentStart(ch: Char): Boolean {
        return ch.isLetter() || ch == '_'
    }

    private fun isIdentPart(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch == '_'
    }

    private fun peek(offset: Int): Char {
        val pos = index + offset
        return if (pos < source.length) source[pos] else '\u0000'
    }

    private fun advance(count: Int = 1) {
        repeat(count) {
            if (index < source.length) {
                index++
                column++
            }
        }
    }

    private fun addToken(type: TokenType, lexeme: String, line: Int, col: Int, len: Int) {
        tokens.add(Token(type, lexeme, SourceSpan(line, col, len)))
    }

    companion object {
        fun stripAccents(input: String): String {
            val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        }

        private val keywordMap = mapOf(
            "algorithme" to TokenType.ALGORITHME,
            "debut" to TokenType.DEBUT,
            "fin" to TokenType.FIN,
            "var" to TokenType.VAR,
            "const" to TokenType.CONST,
            "tableau" to TokenType.TABLEAU,
            "structure" to TokenType.STRUCTURE,
            "finstructure" to TokenType.FIN_STRUCTURE,
            "fonction" to TokenType.FONCTION,
            "procedure" to TokenType.PROCEDURE,
            "retourner" to TokenType.RETOURNER,
            "entier" to TokenType.TYPE_ENTIER,
            "reel" to TokenType.TYPE_REEL,
            "caractere" to TokenType.TYPE_CARACTERE,
            "chaine" to TokenType.TYPE_CHAINE,
            "booleen" to TokenType.TYPE_BOOLEEN,
            "lire" to TokenType.LIRE,
            "ecrire" to TokenType.ECRIRE,
            "nouveau" to TokenType.NOUVEAU,
            "liberer" to TokenType.LIBERER,
            "nil" to TokenType.NIL,
            "pointeur" to TokenType.POINTEUR,
            "sur" to TokenType.SUR,
            "si" to TokenType.SI,
            "alors" to TokenType.ALORS,
            "sinon" to TokenType.SINON,
            "finsi" to TokenType.FIN_SI,
            "selon" to TokenType.SELON,
            "defaut" to TokenType.DEFAUT,
            "finselon" to TokenType.FIN_SELON,
            "pour" to TokenType.POUR,
            "de" to TokenType.DE,
            "pas" to TokenType.PAS,
            "finpour" to TokenType.FIN_POUR,
            "tant" to TokenType.TANT,
            "que" to TokenType.QUE,
            "faire" to TokenType.FAIRE,
            "fintq" to TokenType.FIN_TQ,
            "fintantque" to TokenType.FIN_TQ,
            "repeter" to TokenType.REPETER,
            "div" to TokenType.DIV,
            "mod" to TokenType.MOD,
            "et" to TokenType.ET,
            "ou" to TokenType.OU,
            "non" to TokenType.NON
        )

        private val accentedPreferred = mapOf(
            "debut" to "début",
            "procedure" to "procédure",
            "reel" to "réel",
            "caractere" to "caractère",
            "chaine" to "chaîne",
            "booleen" to "booléen",
            "liberer" to "libérer",
            "defaut" to "défaut",
            "repeter" to "répéter",
            "a" to "à"
        )

        private val cLeakKeywords = mapOf(
            "int" to ("Le mot-clé 'int' appartient au langage C." to "Utilisez 'entier' en pseudo-code."),
            "float" to ("Le mot-clé 'float' appartient au langage C." to "Utilisez 'réel' en pseudo-code."),
            "double" to ("Le mot-clé 'double' appartient au langage C." to "Utilisez 'réel' en pseudo-code."),
            "char" to ("Le mot-clé 'char' appartient au langage C." to "Utilisez 'caractère' en pseudo-code."),
            "void" to ("Le mot-clé 'void' appartient au langage C." to "Utilisez 'Procédure' sans type de retour."),
            "printf" to ("La fonction 'printf' appartient au langage C." to "Utilisez 'Ecrire (...) ;' en pseudo-code."),
            "scanf" to ("La fonction 'scanf' appartient au langage C." to "Utilisez 'Lire (...) ;' en pseudo-code."),
            "if" to ("Le mot-clé 'if' appartient au langage C." to "Utilisez 'Si (condition) Alors ... FinSi'."),
            "else" to ("Le mot-clé 'else' appartient au langage C." to "Utilisez 'Sinon'."),
            "for" to ("Le mot-clé 'for' appartient au langage C." to "Utilisez 'Pour i de ... à ... FinPour'."),
            "while" to ("Le mot-clé 'while' appartient au langage C." to "Utilisez 'Tant que (condition) Faire ... FinTq'."),
            "switch" to ("Le mot-clé 'switch' appartient au langage C." to "Utilisez 'Selon (var) ... FinSelon'."),
            "return" to ("Le mot-clé 'return' appartient au langage C." to "Utilisez 'Retourner'."),
            "malloc" to ("La fonction 'malloc' appartient au langage C." to "Utilisez 'nouveau Type' en pseudo-code."),
            "free" to ("La fonction 'free' appartient au langage C." to "Utilisez 'Libérer pointeur ;' en pseudo-code."),
            "null" to ("'NULL' appartient au langage C." to "Utilisez la constante symbolique 'NIL' en pseudo-code.")
        )
    }
}
