package com.example.engine

class CTranslator(private val analysis: AnalysisResult) {

    fun translate(): String {
        val program = analysis.program ?: return "// Corrigez les erreurs syntaxiques pour générer le code C."
        val sb = StringBuilder()
        val usesMath = hasPowerOp(program)
        val usesHeap = hasHeapOp(program)
        val usesBool = hasBoolType(program)

        sb.appendLine("/* Traduction automatique depuis l'algorithme : ${program.name} */")
        sb.appendLine("#include <stdio.h>")
        if (usesHeap) sb.appendLine("#include <stdlib.h>")
        if (usesMath) sb.appendLine("#include <math.h>")
        if (usesBool) sb.appendLine("#include <stdbool.h>")
        sb.appendLine()

        // Structs
        for (s in program.structs) {
            sb.appendLine("typedef struct {")
            for (f in s.fields) {
                val cType = toCType(f.type)
                for (name in f.names) {
                    if (f.type == AlgoType.Chaine) {
                        sb.appendLine("    char $name[100];")
                    } else {
                        sb.appendLine("    $cType $name;")
                    }
                }
            }
            sb.appendLine("} ${s.name};")
            sb.appendLine()
        }

        // Global constants
        for (c in program.constants) {
            sb.appendLine("#define ${c.name} ${exprToC(c.value, emptyMap())}")
        }
        if (program.constants.isNotEmpty()) sb.appendLine()

        // Global variables & arrays
        val globalTypes = mutableMapOf<String, AlgoType>()
        for (v in program.variables) {
            for (name in v.names) {
                globalTypes[name.lowercase()] = v.type
                sb.appendLine("${declVarToC(name, v.type)};")
            }
        }
        for (a in program.arrays) {
            globalTypes[a.name.lowercase()] = AlgoType.Array(a.elementType, emptyList())
            val dims = a.dimensionExprs.joinToString("") { "[${exprToC(it, globalTypes)}]" }
            sb.appendLine("${toCType(a.elementType)} ${a.name}$dims;")
        }
        if (program.variables.isNotEmpty() || program.arrays.isNotEmpty()) sb.appendLine()

        // Subprograms
        for (sub in program.subprograms) {
            val localTypes = globalTypes.toMutableMap()
            val retC = if (sub.isFunction) toCType(sub.returnType ?: AlgoType.Entier) else "void"
            val paramsC = sub.params.joinToString(", ") { p ->
                localTypes[p.name.lowercase()] = p.type
                if (p.type == AlgoType.Chaine) "char ${p.name}[]" else "${toCType(p.type)} ${p.name}"
            }
            sb.appendLine("$retC ${sub.name}($paramsC) {")
            for (c in sub.constants) {
                sb.appendLine("    const int ${c.name} = ${exprToC(c.value, localTypes)};")
            }
            for (v in sub.variables) {
                for (name in v.names) {
                    localTypes[name.lowercase()] = v.type
                    sb.appendLine("    ${declVarToC(name, v.type)};")
                }
            }
            for (a in sub.arrays) {
                localTypes[a.name.lowercase()] = AlgoType.Array(a.elementType, emptyList())
                val dims = a.dimensionExprs.joinToString("") { "[${exprToC(it, localTypes)}]" }
                sb.appendLine("    ${toCType(a.elementType)} ${a.name}$dims;")
            }
            stmtsToC(sub.body, localTypes, indent = 1, sb = sb)
            sb.appendLine("}")
            sb.appendLine()
        }

        // main()
        sb.appendLine("int main() {")
        stmtsToC(program.body, globalTypes, indent = 1, sb = sb)
        sb.appendLine("    return 0;")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun stmtsToC(
        stmts: List<Stmt>,
        types: Map<String, AlgoType>,
        indent: Int,
        sb: StringBuilder
    ) {
        val pad = "    ".repeat(indent)
        for (s in stmts) {
            when (s) {
                is AssignStmt -> {
                    sb.appendLine("$pad${lvalueToC(s.target, types)} = ${exprToC(s.value, types)};")
                }
                is ReadStmt -> {
                    for (t in s.targets) {
                        val tType = guessLValueType(t, types)
                        val fmt = formatSpecifier(tType)
                        val targetStr = lvalueToC(t, types)
                        val arg = if (tType == AlgoType.Chaine) targetStr else "&$targetStr"
                        sb.appendLine("${pad}scanf(\"$fmt\", $arg);")
                    }
                }
                is WriteStmt -> {
                    val fmtBuilder = StringBuilder()
                    val args = mutableListOf<String>()
                    for (e in s.expressions) {
                        if (e is StringLit) {
                            fmtBuilder.append(e.value.replace("\"", "\\\""))
                        } else {
                            val eType = guessExprType(e, types)
                            fmtBuilder.append(formatSpecifier(eType))
                            args.add(exprToC(e, types))
                        }
                    }
                    fmtBuilder.append("\\n")
                    if (args.isEmpty()) {
                        sb.appendLine("${pad}printf(\"${fmtBuilder}\");")
                    } else {
                        sb.appendLine("${pad}printf(\"${fmtBuilder}\", ${args.joinToString(", ")});")
                    }
                }
                is IfStmt -> {
                    sb.appendLine("${pad}if (${exprToC(s.condition, types)}) {")
                    stmtsToC(s.thenBranch, types, indent + 1, sb)
                    if (s.elseBranch != null) {
                        sb.appendLine("$pad} else {")
                        stmtsToC(s.elseBranch, types, indent + 1, sb)
                    }
                    sb.appendLine("$pad}")
                }
                is SwitchStmt -> {
                    sb.appendLine("${pad}switch (${exprToC(s.selector, types)}) {")
                    for (c in s.cases) {
                        for (v in c.values) {
                            sb.appendLine("${pad}    case ${exprToC(v, types)}:")
                        }
                        stmtsToC(c.body, types, indent + 2, sb)
                        sb.appendLine("${pad}        break;")
                    }
                    if (s.defaultBranch != null) {
                        sb.appendLine("${pad}    default:")
                        stmtsToC(s.defaultBranch, types, indent + 2, sb)
                        sb.appendLine("${pad}        break;")
                    }
                    sb.appendLine("$pad}")
                }
                is ForStmt -> {
                    val startC = exprToC(s.start, types)
                    val endC = exprToC(s.end, types)
                    val stepC = s.step?.let { exprToC(it, types) }
                    val isDesc = s.step == null && s.start is IntLit && s.end is IntLit && s.start.value > s.end.value
                    val cond = if (isDesc || (s.step is UnaryExpr && s.step.op == UnaryOp.NEGATE)) {
                        "${s.varName} >= $endC"
                    } else {
                        "${s.varName} <= $endC"
                    }
                    val update = when {
                        stepC != null -> "${s.varName} += $stepC"
                        isDesc -> "${s.varName}--"
                        else -> "${s.varName}++"
                    }
                    sb.appendLine("${pad}for (${s.varName} = $startC; $cond; $update) {")
                    stmtsToC(s.body, types, indent + 1, sb)
                    sb.appendLine("$pad}")
                }
                is WhileStmt -> {
                    sb.appendLine("${pad}while (${exprToC(s.condition, types)}) {")
                    stmtsToC(s.body, types, indent + 1, sb)
                    sb.appendLine("$pad}")
                }
                is RepeatStmt -> {
                    sb.appendLine("${pad}do {")
                    stmtsToC(s.body, types, indent + 1, sb)
                    // Course uses stopping condition, C uses continuation condition !cond
                    sb.appendLine("$pad} while (!(${exprToC(s.stopCondition, types)}));")
                }
                is CallStmt -> {
                    val args = s.arguments.joinToString(", ") { exprToC(it, types) }
                    sb.appendLine("$pad${s.name}($args);")
                }
                is ReturnStmt -> {
                    sb.appendLine("${pad}return ${exprToC(s.value, types)};")
                }
                is FreeStmt -> {
                    sb.appendLine("${pad}free(${lvalueToC(s.target, types)});")
                }
            }
        }
    }

    private fun declVarToC(name: String, type: AlgoType): String {
        return if (type == AlgoType.Chaine) "char $name[100]" else "${toCType(type)} $name"
    }

    private fun toCType(type: AlgoType): String = when (type) {
        AlgoType.Entier -> "int"
        AlgoType.Reel -> "float"
        AlgoType.Caractere -> "char"
        AlgoType.Chaine -> "char*"
        AlgoType.Booleen -> "bool"
        is AlgoType.Struct -> type.name
        is AlgoType.Pointer -> "${toCType(type.targetType)}*"
        else -> "int"
    }

    private fun formatSpecifier(type: AlgoType): String = when (type) {
        AlgoType.Entier -> "%d"
        AlgoType.Reel -> "%f"
        AlgoType.Caractere -> " %c"
        AlgoType.Chaine -> "%s"
        AlgoType.Booleen -> "%d"
        is AlgoType.Pointer -> "%p"
        else -> "%d"
    }

    private fun lvalueToC(target: LValue, types: Map<String, AlgoType>): String = when (target) {
        is VarTarget -> target.name
        is ArrayTarget -> "${lvalueToC(target.base, types)}${target.indices.joinToString("") { "[${exprToC(it, types)}]" }}"
        is FieldTarget -> "${lvalueToC(target.base, types)}.${target.fieldName}"
        is DerefTarget -> "*${exprToC(target.pointerExpr, types)}"
    }

    private fun exprToC(expr: Expr, types: Map<String, AlgoType>): String = when (expr) {
        is IntLit -> expr.value.toString()
        is RealLit -> expr.value.toString()
        is CharLit -> "'${expr.value}'"
        is StringLit -> "\"${expr.value.replace("\"", "\\\"")}\""
        is BoolLit -> if (expr.value) "true" else "false"
        is NilLit -> "NULL"
        is VarExpr -> expr.name
        is ArrayAccessExpr -> "${exprToC(expr.base, types)}${expr.indices.joinToString("") { "[${exprToC(it, types)}]" }}"
        is FieldAccessExpr -> "${exprToC(expr.base, types)}.${expr.fieldName}"
        is CallExpr -> "${expr.name}(${expr.arguments.joinToString(", ") { exprToC(it, types) }})"
        is AllocExpr -> "(${toCType(expr.allocType)}*)malloc(sizeof(${toCType(expr.allocType)}))"
        is AddrOfExpr -> "&${lvalueToC(expr.target, types)}"
        is DerefExpr -> "*${exprToC(expr.pointer, types)}"
        is UnaryExpr -> when (expr.op) {
            UnaryOp.NEGATE -> "-${exprToC(expr.operand, types)}"
            UnaryOp.PLUS -> "+${exprToC(expr.operand, types)}"
            UnaryOp.NOT -> "!(${exprToC(expr.operand, types)})"
        }
        is BinaryExpr -> {
            val l = exprToC(expr.left, types)
            val r = exprToC(expr.right, types)
            when (expr.op) {
                BinaryOp.ADD -> "$l + $r"
                BinaryOp.SUB -> "$l - $r"
                BinaryOp.MUL -> "$l * $r"
                BinaryOp.DIV_REAL -> "(float)($l) / ($r)"
                BinaryOp.DIV_INT -> "$l / $r"
                BinaryOp.MOD -> "$l % $r"
                BinaryOp.POWER -> "pow($l, $r)"
                BinaryOp.EQ -> "$l == $r"
                BinaryOp.NEQ -> "$l != $r"
                BinaryOp.LT -> "$l < $r"
                BinaryOp.LTE -> "$l <= $r"
                BinaryOp.GT -> "$l > $r"
                BinaryOp.GTE -> "$l >= $r"
                BinaryOp.AND -> "($l) && ($r)"
                BinaryOp.OR -> "($l) || ($r)"
                BinaryOp.NAND -> "!(($l) && ($r))"
                BinaryOp.NOR -> "!(($l) || ($r))"
            }
        }
    }

    private fun guessLValueType(target: LValue, types: Map<String, AlgoType>): AlgoType = when (target) {
        is VarTarget -> types[target.name.lowercase()] ?: AlgoType.Entier
        is ArrayTarget -> {
            val baseT = guessLValueType(target.base, types)
            if (baseT is AlgoType.Array) baseT.elementType else AlgoType.Entier
        }
        is FieldTarget -> {
            val baseT = guessLValueType(target.base, types)
            if (baseT is AlgoType.Struct) {
                analysis.structs[baseT.name.lowercase()]?.fields?.get(target.fieldName.lowercase()) ?: AlgoType.Entier
            } else AlgoType.Entier
        }
        is DerefTarget -> {
            val pT = guessExprType(target.pointerExpr, types)
            if (pT is AlgoType.Pointer) pT.targetType else AlgoType.Entier
        }
    }

    private fun guessExprType(expr: Expr, types: Map<String, AlgoType>): AlgoType = when (expr) {
        is IntLit -> AlgoType.Entier
        is RealLit -> AlgoType.Reel
        is CharLit -> AlgoType.Caractere
        is StringLit -> AlgoType.Chaine
        is BoolLit -> AlgoType.Booleen
        is NilLit -> AlgoType.NullPointer
        is VarExpr -> types[expr.name.lowercase()] ?: AlgoType.Entier
        is CallExpr -> analysis.subprograms[expr.name.lowercase()]?.returnType ?: AlgoType.Entier
        is ArrayAccessExpr -> {
            val b = guessExprType(expr.base, types)
            if (b is AlgoType.Array) b.elementType else AlgoType.Entier
        }
        is FieldAccessExpr -> {
            val b = guessExprType(expr.base, types)
            if (b is AlgoType.Struct) {
                analysis.structs[b.name.lowercase()]?.fields?.get(expr.fieldName.lowercase()) ?: AlgoType.Entier
            } else AlgoType.Entier
        }
        is DerefExpr -> {
            val p = guessExprType(expr.pointer, types)
            if (p is AlgoType.Pointer) p.targetType else AlgoType.Entier
        }
        is BinaryExpr -> when (expr.op) {
            BinaryOp.DIV_REAL -> AlgoType.Reel
            BinaryOp.ADD, BinaryOp.SUB, BinaryOp.MUL, BinaryOp.POWER -> {
                if (guessExprType(expr.left, types) == AlgoType.Reel || guessExprType(expr.right, types) == AlgoType.Reel) {
                    AlgoType.Reel
                } else AlgoType.Entier
            }
            BinaryOp.DIV_INT, BinaryOp.MOD -> AlgoType.Entier
            else -> AlgoType.Booleen
        }
        else -> AlgoType.Entier
    }

    private fun hasPowerOp(program: ProgramNode): Boolean =
        program.body.any { stmtHasPower(it) } || program.subprograms.any { sub -> sub.body.any { stmtHasPower(it) } }

    private fun stmtHasPower(s: Stmt): Boolean = when (s) {
        is AssignStmt -> exprHasPower(s.value)
        is WriteStmt -> s.expressions.any { exprHasPower(it) }
        is IfStmt -> exprHasPower(s.condition) || s.thenBranch.any { stmtHasPower(it) } || (s.elseBranch?.any { stmtHasPower(it) } == true)
        is ForStmt -> s.body.any { stmtHasPower(it) }
        is WhileStmt -> s.body.any { stmtHasPower(it) }
        is RepeatStmt -> s.body.any { stmtHasPower(it) }
        is ReturnStmt -> exprHasPower(s.value)
        else -> false
    }

    private fun exprHasPower(e: Expr): Boolean = when (e) {
        is BinaryExpr -> e.op == BinaryOp.POWER || exprHasPower(e.left) || exprHasPower(e.right)
        is UnaryExpr -> exprHasPower(e.operand)
        else -> false
    }

    private fun hasHeapOp(program: ProgramNode): Boolean =
        program.body.any { it is FreeStmt } ||
            program.variables.any { it.type is AlgoType.Pointer } ||
            program.subprograms.any { sub -> sub.body.any { it is FreeStmt } }

    private fun hasBoolType(program: ProgramNode): Boolean =
        program.variables.any { it.type == AlgoType.Booleen } ||
            program.subprograms.any { sub -> sub.variables.any { it.type == AlgoType.Booleen } || sub.returnType == AlgoType.Booleen }
}
