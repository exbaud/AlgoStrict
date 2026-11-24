package com.example.engine

enum class TokenType {
    // Structure & declarations
    ALGORITHME,
    DEBUT,
    FIN,
    VAR,
    CONST,
    TABLEAU,
    STRUCTURE,
    FIN_STRUCTURE,
    FONCTION,
    PROCEDURE,
    RETOURNER,

    // Types
    TYPE_ENTIER,
    TYPE_REEL,
    TYPE_CARACTERE,
    TYPE_CHAINE,
    TYPE_BOOLEEN,

    // I/O & Memory
    LIRE,
    ECRIRE,
    NOUVEAU,
    LIBERER,
    NIL,
    POINTEUR,
    SUR,

    // Control flow
    SI,
    ALORS,
    SINON,
    FIN_SI,
    SELON,
    DEFAUT,
    FIN_SELON,
    POUR,
    DE,
    A,
    PAS,
    FIN_POUR,
    TANT,
    QUE,
    FAIRE,
    FIN_TQ,
    REPETER,
    JUSQU_A,

    // Literals & Identifiers
    IDENTIFIER,
    INT_LITERAL,
    REAL_LITERAL,
    CHAR_LITERAL,
    STRING_LITERAL,
    BOOL_LITERAL,

    // Operators
    ASSIGN,        // <- or ←
    PLUS,          // +
    MINUS,         // -
    STAR,          // *
    SLASH,         // /
    POWER,         // ^ or **
    DIV,           // div
    MOD,           // mod
    AMPERSAND,     // &
    EQ,            // =
    NEQ,           // ≠ or <>
    LT,            // <
    LTE,           // <= or ≤
    GT,            // >
    GTE,           // >= or ≥
    ET,            // ET
    OU,            // OU
    NON,           // NON

    // Punctuation
    COLON,         // :
    SEMICOLON,     // ;
    COMMA,         // ,
    DOT,           // .
    LPAREN,        // (
    RPAREN,        // )
    LBRACKET,      // [
    RBRACKET,      // ]

    EOF
}

data class SourceSpan(
    val line: Int,
    val column: Int,
    val length: Int = 1
)

data class Token(
    val type: TokenType,
    val lexeme: String,
    val span: SourceSpan
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING
}

data class Diagnostic(
    val line: Int,
    val column: Int,
    val message: String,
    val chapterRef: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val hint: String? = null
)

enum class StrictnessLevel {
    STANDARD_IFRI,   // Enforces full grammar, types, ';', '<-', blocks, rejects C syntax; tolerates missing accents with optional warnings
    ULTRA_STRICT     // Also enforces French accents on keywords (Début, Réel, Chaîne, Répéter, Jusqu'à, Procédure, Libérer, Défaut)
}
