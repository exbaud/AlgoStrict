package com.example.engine

sealed class AlgoType {
    data object Entier : AlgoType() {
        override fun toString() = "entier"
    }
    data object Reel : AlgoType() {
        override fun toString() = "réel"
    }
    data object Caractere : AlgoType() {
        override fun toString() = "caractère"
    }
    data object Chaine : AlgoType() {
        override fun toString() = "chaîne"
    }
    data object Booleen : AlgoType() {
        override fun toString() = "booléen"
    }
    data class Struct(val name: String) : AlgoType() {
        override fun toString() = "Structure $name"
    }
    data class Array(val elementType: AlgoType, val dimensions: List<Int>) : AlgoType() {
        override fun toString(): String {
            val dims = dimensions.joinToString("") { "[$it]" }
            return "Tableau$dims : $elementType"
        }
    }
    data class Pointer(val targetType: AlgoType) : AlgoType() {
        override fun toString() = "*$targetType"
    }
    data object NullPointer : AlgoType() {
        override fun toString() = "NIL"
    }
    data object Void : AlgoType() {
        override fun toString() = "vide"
    }
    data object ErrorType : AlgoType() {
        override fun toString() = "<erreur>"
    }

    fun isNumeric(): Boolean = this == Entier || this == Reel
}

data class ProgramNode(
    val name: String,
    val nameLine: Int,
    val constants: List<ConstDecl>,
    val structs: List<StructDecl>,
    val variables: List<VarDecl>,
    val arrays: List<ArrayDecl>,
    val subprograms: List<SubprogramDecl>,
    val body: List<Stmt>,
    val beginLine: Int,
    val endLine: Int
)

data class ConstDecl(
    val name: String,
    val value: Expr,
    val line: Int
)

data class VarDecl(
    val names: List<String>,
    val type: AlgoType,
    val line: Int
)

data class ArrayDecl(
    val name: String,
    val dimensionExprs: List<Expr>,
    val elementType: AlgoType,
    val line: Int
)

data class StructFieldDecl(
    val names: List<String>,
    val type: AlgoType,
    val line: Int
)

data class StructDecl(
    val name: String,
    val fields: List<StructFieldDecl>,
    val line: Int
)

data class ParamDecl(
    val name: String,
    val type: AlgoType,
    val line: Int
)

data class SubprogramDecl(
    val isFunction: Boolean,
    val name: String,
    val params: List<ParamDecl>,
    val returnType: AlgoType?,
    val constants: List<ConstDecl>,
    val variables: List<VarDecl>,
    val arrays: List<ArrayDecl>,
    val body: List<Stmt>,
    val line: Int
)

sealed interface Stmt {
    val line: Int
}

data class AssignStmt(
    val target: LValue,
    val value: Expr,
    override val line: Int
) : Stmt

data class ReadStmt(
    val targets: List<LValue>,
    override val line: Int
) : Stmt

data class WriteStmt(
    val expressions: List<Expr>,
    override val line: Int
) : Stmt

data class IfStmt(
    val condition: Expr,
    val thenBranch: List<Stmt>,
    val elseBranch: List<Stmt>?,
    override val line: Int
) : Stmt

data class SwitchCase(
    val values: List<Expr>,
    val body: List<Stmt>,
    val line: Int
)

data class SwitchStmt(
    val selector: Expr,
    val cases: List<SwitchCase>,
    val defaultBranch: List<Stmt>?,
    override val line: Int
) : Stmt

data class ForStmt(
    val varName: String,
    val start: Expr,
    val end: Expr,
    val step: Expr?,
    val body: List<Stmt>,
    override val line: Int
) : Stmt

data class WhileStmt(
    val condition: Expr,
    val body: List<Stmt>,
    override val line: Int
) : Stmt

data class RepeatStmt(
    val body: List<Stmt>,
    val stopCondition: Expr,
    override val line: Int
) : Stmt

data class CallStmt(
    val name: String,
    val arguments: List<Expr>,
    override val line: Int
) : Stmt

data class ReturnStmt(
    val value: Expr,
    override val line: Int
) : Stmt

data class FreeStmt(
    val target: LValue,
    override val line: Int
) : Stmt

sealed interface LValue {
    val line: Int
}

data class VarTarget(val name: String, override val line: Int) : LValue
data class ArrayTarget(val base: LValue, val indices: List<Expr>, override val line: Int) : LValue
data class FieldTarget(val base: LValue, val fieldName: String, override val line: Int) : LValue
data class DerefTarget(val pointerExpr: Expr, override val line: Int) : LValue

sealed interface Expr {
    val line: Int
}

data class IntLit(val value: Int, override val line: Int) : Expr
data class RealLit(val value: Double, override val line: Int) : Expr
data class CharLit(val value: Char, override val line: Int) : Expr
data class StringLit(val value: String, override val line: Int) : Expr
data class BoolLit(val value: Boolean, override val line: Int) : Expr
data class NilLit(override val line: Int) : Expr
data class VarExpr(val name: String, override val line: Int) : Expr
data class ArrayAccessExpr(val base: Expr, val indices: List<Expr>, override val line: Int) : Expr
data class FieldAccessExpr(val base: Expr, val fieldName: String, override val line: Int) : Expr
data class CallExpr(val name: String, val arguments: List<Expr>, override val line: Int) : Expr
data class AllocExpr(val allocType: AlgoType, override val line: Int) : Expr
data class AddrOfExpr(val target: LValue, override val line: Int) : Expr
data class DerefExpr(val pointer: Expr, override val line: Int) : Expr

enum class UnaryOp {
    NEGATE,
    PLUS,
    NOT
}

data class UnaryExpr(val op: UnaryOp, val operand: Expr, override val line: Int) : Expr

enum class BinaryOp {
    ADD, SUB, MUL, DIV_REAL, DIV_INT, MOD, POWER,
    EQ, NEQ, LT, LTE, GT, GTE,
    AND, OR, NAND, NOR
}

data class BinaryExpr(
    val left: Expr,
    val op: BinaryOp,
    val right: Expr,
    override val line: Int
) : Expr
