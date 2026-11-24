package com.example.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

sealed class RuntimeValue {
    data class IntVal(val value: Int) : RuntimeValue() {
        override fun toString() = value.toString()
    }
    data class RealVal(val value: Double) : RuntimeValue() {
        override fun toString(): String {
            return if (value % 1.0 == 0.0 && value in -1e12..1e12) {
                value.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
            }
        }
    }
    data class CharVal(val value: Char) : RuntimeValue() {
        override fun toString() = value.toString()
    }
    data class StringVal(val value: String) : RuntimeValue() {
        override fun toString() = value
    }
    data class BoolVal(val value: Boolean) : RuntimeValue() {
        override fun toString() = if (value) "Vrai" else "Faux"
    }
    data class PointerVal(val targetAddress: Long?, val initialized: Boolean = true) : RuntimeValue() {
        override fun toString(): String = when {
            !initialized -> "<non initialisé>"
            targetAddress == null -> "NIL"
            else -> "0x${targetAddress.toString(16)}"
        }
    }
    data class ArrayVal(val dimensions: List<Int>, val elementAddresses: List<Long>) : RuntimeValue() {
        override fun toString() = "[Tableau ${dimensions.joinToString("x")}]"
    }
    data class StructVal(val structName: String, val fields: LinkedHashMap<String, Long>) : RuntimeValue() {
        override fun toString() = "Structure $structName"
    }
    data object Uninitialized : RuntimeValue() {
        override fun toString() = "<non initialisé>"
    }
}

data class MemoryCell(
    val address: Long,
    val label: String,
    val type: AlgoType,
    var value: RuntimeValue,
    val scopeName: String,
    val isConstant: Boolean = false,
    var isFreed: Boolean = false,
    val isHeap: Boolean = false
)

enum class ExecutionStatus {
    IDLE,
    RUNNING,
    PAUSED_STEP,
    WAITING_INPUT,
    FINISHED,
    ERROR
}

data class InputPrompt(
    val variableLabel: String,
    val expectedType: AlgoType,
    val line: Int
)

data class ExecutionSnapshot(
    val status: ExecutionStatus = ExecutionStatus.IDLE,
    val currentLine: Int? = null,
    val outputLines: List<String> = emptyList(),
    val memoryCells: List<MemoryCell> = emptyList(),
    val callStack: List<String> = emptyList(),
    val pendingInput: InputPrompt? = null,
    val errorMessage: String? = null,
    val stepCount: Int = 0
)

class RuntimeAlgoException(val line: Int, message: String) : Exception(message)
private class ReturnSignal(val value: RuntimeValue) : Exception()

class Interpreter(
    private val analysis: AnalysisResult
) {
    private val _snapshot = MutableStateFlow(ExecutionSnapshot())
    val snapshot: StateFlow<ExecutionSnapshot> = _snapshot.asStateFlow()

    private val memory = linkedMapOf<Long, MemoryCell>()
    private val globalEnv = linkedMapOf<String, Long>()
    private val callFrames = mutableListOf<Pair<String, LinkedHashMap<String, Long>>>()
    private val outputLines = mutableListOf<String>()

    private var nextAddress = 0xFDE23A50L
    private var stepCount = 0
    private val maxSteps = 15_000
    private val maxCallDepth = 150

    private var isStepMode = false
    private var stepGate: CompletableDeferred<Unit>? = null
    private var inputGate: CompletableDeferred<String>? = null
    private val queuedInputs = ArrayDeque<String>()
    private var lastLine: Int? = null

    fun setQueuedInputs(inputs: List<String>) {
        queuedInputs.clear()
        queuedInputs.addAll(inputs)
    }

    fun stepNext() {
        isStepMode = true
        stepGate?.complete(Unit)
    }

    fun continueRun() {
        isStepMode = false
        stepGate?.complete(Unit)
    }

    fun submitInput(rawInput: String) {
        inputGate?.complete(rawInput)
    }

    suspend fun execute(stepByStep: Boolean = false): ExecutionSnapshot {
        val program = analysis.program
            ?: return ExecutionSnapshot(
                status = ExecutionStatus.ERROR,
                errorMessage = "Impossible d'exécuter : le programme contient des erreurs syntaxiques."
            )

        if (analysis.hasErrors) {
            return ExecutionSnapshot(
                status = ExecutionStatus.ERROR,
                errorMessage = "Corrigez d'abord les erreurs strictes avant l'exécution."
            )
        }

        resetState(stepByStep)

        try {
            // Allocate global constants, variables, arrays
            allocateConstants(program.constants, globalEnv, "Principal", isGlobal = true)
            allocateVariables(program.variables, globalEnv, "Principal")
            allocateArrays(program.arrays, globalEnv, "Principal")

            callFrames.add("Principal" to globalEnv)
            emitSnapshot(ExecutionStatus.RUNNING, program.beginLine)

            executeBlock(program.body, globalEnv)

            emitSnapshot(ExecutionStatus.FINISHED, program.endLine)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeAlgoException) {
            emitSnapshot(ExecutionStatus.ERROR, e.line, errorMsg = "Ligne ${e.line} — ${e.message}")
        } catch (e: Exception) {
            emitSnapshot(ExecutionStatus.ERROR, lastLine, errorMsg = "Erreur interne : ${e.message}")
        }

        return _snapshot.value
    }

    private fun resetState(stepByStep: Boolean) {
        memory.clear()
        globalEnv.clear()
        callFrames.clear()
        outputLines.clear()
        nextAddress = 0xFDE23A50L
        stepCount = 0
        isStepMode = stepByStep
        stepGate = null
        inputGate = null
        lastLine = null
    }

    private fun allocCell(
        label: String,
        type: AlgoType,
        initialValue: RuntimeValue,
        scopeName: String,
        isConstant: Boolean = false,
        isHeap: Boolean = false
    ): Long {
        val addr = nextAddress
        val byteSize = when (type) {
            AlgoType.Entier -> 2L
            AlgoType.Reel -> 4L
            AlgoType.Caractere -> 1L
            AlgoType.Booleen -> 1L
            is AlgoType.Pointer -> 4L
            else -> 4L
        }
        nextAddress += byteSize
        memory[addr] = MemoryCell(
            address = addr,
            label = label,
            type = type,
            value = initialValue,
            scopeName = scopeName,
            isConstant = isConstant,
            isHeap = isHeap
        )
        return addr
    }

    private fun allocateTypeDefault(
        label: String,
        type: AlgoType,
        scopeName: String,
        isHeap: Boolean = false
    ): Long {
        return when (type) {
            is AlgoType.Array -> {
                val total = type.dimensions.fold(1) { acc, d -> acc * d }
                val addrs = MutableList(total) { flatIdx ->
                    val coords = unflattenIndex(flatIdx, type.dimensions)
                    val elemLabel = "$label${coords.joinToString("") { "[$it]" }}"
                    allocateTypeDefault(elemLabel, type.elementType, scopeName, isHeap)
                }
                allocCell(label, type, RuntimeValue.ArrayVal(type.dimensions, addrs), scopeName, isHeap = isHeap)
            }
            is AlgoType.Struct -> {
                val def = analysis.structs[type.name.lowercase()]
                    ?: throw RuntimeAlgoException(1, "Structure '${type.name}' introuvable.")
                val fields = linkedMapOf<String, Long>()
                for ((fKey, fType) in def.fields) {
                    val fAddr = allocateTypeDefault("$label.$fKey", fType, scopeName, isHeap)
                    fields[fKey] = fAddr
                }
                allocCell(label, type, RuntimeValue.StructVal(def.name, fields), scopeName, isHeap = isHeap)
            }
            is AlgoType.Pointer -> {
                allocCell(label, type, RuntimeValue.PointerVal(null, initialized = false), scopeName, isHeap = isHeap)
            }
            else -> {
                allocCell(label, type, RuntimeValue.Uninitialized, scopeName, isHeap = isHeap)
            }
        }
    }

    private suspend fun allocateConstants(
        constants: List<ConstDecl>,
        env: LinkedHashMap<String, Long>,
        scopeName: String,
        isGlobal: Boolean
    ) {
        for (c in constants) {
            val v = evalExpr(c.value, env)
            val t = analysis.globalConstants[c.name.lowercase()]?.type ?: inferRuntimeType(v)
            val addr = allocCell(c.name, t, v, scopeName, isConstant = true)
            env[c.name.lowercase()] = addr
        }
    }

    private fun allocateVariables(
        variables: List<VarDecl>,
        env: LinkedHashMap<String, Long>,
        scopeName: String
    ) {
        for (v in variables) {
            for (name in v.names) {
                val addr = allocateTypeDefault(name, v.type, scopeName)
                env[name.lowercase()] = addr
            }
        }
    }

    private suspend fun allocateArrays(
        arrays: List<ArrayDecl>,
        env: LinkedHashMap<String, Long>,
        scopeName: String
    ) {
        for (a in arrays) {
            val dims = a.dimensionExprs.map { expr ->
                val v = evalExpr(expr, env)
                (v as? RuntimeValue.IntVal)?.value ?: 1
            }
            val arrType = AlgoType.Array(a.elementType, dims)
            val addr = allocateTypeDefault(a.name, arrType, scopeName)
            env[a.name.lowercase()] = addr
        }
    }

    private suspend fun executeBlock(stmts: List<Stmt>, env: LinkedHashMap<String, Long>) {
        for (stmt in stmts) {
            executeStmt(stmt, env)
        }
    }

    private suspend fun checkpoint(line: Int) {
        lastLine = line
        stepCount++
        if (stepCount > maxSteps) {
            throw RuntimeAlgoException(
                line,
                "Limite de sécurité atteinte ($maxSteps instructions). Vérifiez si vous avez une boucle infinie !"
            )
        }
        if (isStepMode) {
            val gate = CompletableDeferred<Unit>()
            stepGate = gate
            emitSnapshot(ExecutionStatus.PAUSED_STEP, line)
            gate.await()
            stepGate = null
            emitSnapshot(ExecutionStatus.RUNNING, line)
        }
    }

    private suspend fun executeStmt(stmt: Stmt, env: LinkedHashMap<String, Long>) {
        checkpoint(stmt.line)
        when (stmt) {
            is AssignStmt -> {
                val addr = resolveLValueAddress(stmt.target, env)
                val cell = memory[addr] ?: throw RuntimeAlgoException(stmt.line, "Adresse mémoire invalide.")
                if (cell.isFreed) {
                    throw RuntimeAlgoException(stmt.line, "Accès à une zone mémoire déjà libérée (Chap. 8, §3).")
                }
                val newVal = evalExpr(stmt.value, env)
                cell.value = coerceValue(newVal, cell.type, stmt.line)
            }

            is ReadStmt -> {
                for (target in stmt.targets) {
                    val addr = resolveLValueAddress(target, env)
                    val cell = memory[addr] ?: throw RuntimeAlgoException(stmt.line, "Adresse mémoire invalide.")
                    val rawInput = readInputValue(cell.label, cell.type, stmt.line)
                    cell.value = parseInputForType(rawInput, cell.type, stmt.line)
                }
            }

            is WriteStmt -> {
                val rendered = buildString {
                    for (expr in stmt.expressions) {
                        val v = evalExpr(expr, env)
                        if (v is RuntimeValue.Uninitialized) {
                            throw RuntimeAlgoException(stmt.line, "Tentative d'affichage d'une variable non initialisée.")
                        }
                        append(v.toString())
                    }
                }
                outputLines.add(rendered)
                if (!isStepMode) {
                    emitSnapshot(ExecutionStatus.RUNNING, stmt.line)
                }
            }

            is IfStmt -> {
                val cond = evalBool(stmt.condition, env, stmt.line)
                if (cond) {
                    executeBlock(stmt.thenBranch, env)
                } else if (stmt.elseBranch != null) {
                    executeBlock(stmt.elseBranch, env)
                }
            }

            is SwitchStmt -> {
                val sel = evalExpr(stmt.selector, env)
                var matched = false
                for (c in stmt.cases) {
                    for (vExpr in c.values) {
                        val caseVal = evalExpr(vExpr, env)
                        if (valuesEqual(sel, caseVal)) {
                            executeBlock(c.body, env)
                            matched = true
                            break
                        }
                    }
                    if (matched) break
                }
                if (!matched && stmt.defaultBranch != null) {
                    executeBlock(stmt.defaultBranch, env)
                }
            }

            is ForStmt -> {
                val varAddr = lookupVarAddress(stmt.varName, env, stmt.line)
                val startVal = evalInt(stmt.start, env, stmt.line)
                val endVal = evalInt(stmt.end, env, stmt.line)
                val stepVal = if (stmt.step != null) {
                    evalInt(stmt.step, env, stmt.line)
                } else {
                    if (startVal <= endVal) 1 else -1
                }
                if (stepVal == 0) {
                    throw RuntimeAlgoException(stmt.line, "Le pas d'une boucle 'Pour' ne peut pas être 0.")
                }

                var i = startVal
                while ((stepVal > 0 && i <= endVal) || (stepVal < 0 && i >= endVal)) {
                    val cell = memory[varAddr]!!
                    cell.value = RuntimeValue.IntVal(i)
                    executeBlock(stmt.body, env)
                    // Re-read in case body modified loop variable, or increment i
                    val currentVal = (memory[varAddr]?.value as? RuntimeValue.IntVal)?.value ?: i
                    i = currentVal + stepVal
                    checkpoint(stmt.line)
                }
            }

            is WhileStmt -> {
                while (evalBool(stmt.condition, env, stmt.line)) {
                    executeBlock(stmt.body, env)
                    checkpoint(stmt.line)
                }
            }

            is RepeatStmt -> {
                do {
                    executeBlock(stmt.body, env)
                    checkpoint(stmt.line)
                } while (!evalBool(stmt.stopCondition, env, stmt.line))
            }

            is CallStmt -> {
                invokeSubprogram(stmt.name, stmt.arguments, env, stmt.line)
            }

            is ReturnStmt -> {
                val retVal = evalExpr(stmt.value, env)
                throw ReturnSignal(retVal)
            }

            is FreeStmt -> {
                val ptrAddr = resolveLValueAddress(stmt.target, env)
                val ptrCell = memory[ptrAddr] ?: throw RuntimeAlgoException(stmt.line, "Pointeur invalide.")
                val ptrVal = ptrCell.value as? RuntimeValue.PointerVal
                    ?: throw RuntimeAlgoException(stmt.line, "'Libérer' requiert un pointeur.")
                if (!ptrVal.initialized || ptrVal.targetAddress == null) {
                    throw RuntimeAlgoException(stmt.line, "Impossible de libérer un pointeur NIL ou non initialisé (Chap. 8, §3).")
                }
                val heapCell = memory[ptrVal.targetAddress]
                    ?: throw RuntimeAlgoException(stmt.line, "Zone mémoire introuvable lors de 'Libérer'.")
                if (!heapCell.isHeap) {
                    throw RuntimeAlgoException(stmt.line, "Impossible de libérer une variable statique (non allouée avec 'nouveau').")
                }
                if (heapCell.isFreed) {
                    throw RuntimeAlgoException(stmt.line, "Double libération mémoire détectée sur '${ptrCell.label}'.")
                }
                markFreedRecursively(heapCell)
            }
        }
    }

    private fun markFreedRecursively(cell: MemoryCell) {
        cell.isFreed = true
        when (val v = cell.value) {
            is RuntimeValue.ArrayVal -> v.elementAddresses.forEach { a -> memory[a]?.let { markFreedRecursively(it) } }
            is RuntimeValue.StructVal -> v.fields.values.forEach { a -> memory[a]?.let { markFreedRecursively(it) } }
            else -> {}
        }
    }

    private suspend fun invokeSubprogram(
        name: String,
        argExprs: List<Expr>,
        callerEnv: LinkedHashMap<String, Long>,
        line: Int
    ): RuntimeValue {
        if (callFrames.size >= maxCallDepth) {
            throw RuntimeAlgoException(line, "Débordement de pile : récursivité trop profonde (>$maxCallDepth appels).")
        }
        val subDef = analysis.subprograms[name.lowercase()]
            ?: throw RuntimeAlgoException(line, "Sous-programme '$name' introuvable.")
        val decl = subDef.decl

        // Evaluate arguments in caller environment before creating local frame
        val argValues = argExprs.map { evalExpr(it, callerEnv) }

        // Local scope inherits globals, shadowed by local params/vars (Chap. 7 §2.1)
        val localEnv = LinkedHashMap<String, Long>(globalEnv)
        val frameTitle = "${decl.name}(${argValues.joinToString(", ")})"
        callFrames.add(frameTitle to localEnv)

        try {
            decl.params.forEachIndexed { idx, param ->
                val pAddr = allocateTypeDefault(param.name, param.type, frameTitle)
                memory[pAddr]!!.value = coerceValue(argValues[idx], param.type, line)
                localEnv[param.name.lowercase()] = pAddr
            }
            allocateConstants(decl.constants, localEnv, frameTitle, isGlobal = false)
            allocateVariables(decl.variables, localEnv, frameTitle)
            allocateArrays(decl.arrays, localEnv, frameTitle)

            try {
                executeBlock(decl.body, localEnv)
            } catch (ret: ReturnSignal) {
                return coerceValue(ret.value, subDef.returnType, line)
            }

            if (decl.isFunction) {
                throw RuntimeAlgoException(line, "La fonction '${decl.name}' s'est terminée sans exécuter 'Retourner'.")
            }
            return RuntimeValue.Uninitialized
        } finally {
            callFrames.removeAt(callFrames.lastIndex)
        }
    }

    private suspend fun resolveLValueAddress(target: LValue, env: LinkedHashMap<String, Long>): Long {
        return when (target) {
            is VarTarget -> lookupVarAddress(target.name, env, target.line)
            is ArrayTarget -> {
                val arrAddr = resolveLValueAddress(target.base, env)
                val arrCell = memory[arrAddr] ?: throw RuntimeAlgoException(target.line, "Tableau introuvable.")
                val arrVal = arrCell.value as? RuntimeValue.ArrayVal
                    ?: throw RuntimeAlgoException(target.line, "'${arrCell.label}' n'est pas un tableau.")
                val indices = target.indices.map { evalInt(it, env, target.line) }
                val flatIdx = computeFlatIndex(indices, arrVal.dimensions, arrCell.label, target.line)
                arrVal.elementAddresses[flatIdx]
            }
            is FieldTarget -> {
                val structAddr = resolveLValueAddress(target.base, env)
                val structCell = memory[structAddr] ?: throw RuntimeAlgoException(target.line, "Structure introuvable.")
                val structVal = structCell.value as? RuntimeValue.StructVal
                    ?: throw RuntimeAlgoException(target.line, "'${structCell.label}' n'est pas une structure.")
                structVal.fields[target.fieldName.lowercase()]
                    ?: throw RuntimeAlgoException(target.line, "Champ '${target.fieldName}' introuvable.")
            }
            is DerefTarget -> {
                val ptrVal = evalExpr(target.pointerExpr, env) as? RuntimeValue.PointerVal
                    ?: throw RuntimeAlgoException(target.line, "L'opérateur '*' requiert un pointeur.")
                if (!ptrVal.initialized) {
                    throw RuntimeAlgoException(target.line, "Erreur mémoire (Chap. 8, §2.2.2) : utilisation d'un pointeur non initialisé !")
                }
                val targetAddr = ptrVal.targetAddress
                    ?: throw RuntimeAlgoException(target.line, "Erreur mémoire (Chap. 8, §2.2.2) : déréférencement d'un pointeur NIL !")
                val targetCell = memory[targetAddr]
                    ?: throw RuntimeAlgoException(target.line, "Adresse pointée invalide.")
                if (targetCell.isFreed) {
                    throw RuntimeAlgoException(target.line, "Erreur mémoire (Chap. 8, §3) : accès après 'Libérer' !")
                }
                targetAddr
            }
        }
    }

    private fun lookupVarAddress(name: String, env: LinkedHashMap<String, Long>, line: Int): Long {
        return env[name.lowercase()]
            ?: throw RuntimeAlgoException(line, "Variable '$name' introuvable en mémoire.")
    }

    private suspend fun evalExpr(expr: Expr, env: LinkedHashMap<String, Long>): RuntimeValue {
        return when (expr) {
            is IntLit -> RuntimeValue.IntVal(expr.value)
            is RealLit -> RuntimeValue.RealVal(expr.value)
            is CharLit -> RuntimeValue.CharVal(expr.value)
            is StringLit -> RuntimeValue.StringVal(expr.value)
            is BoolLit -> RuntimeValue.BoolVal(expr.value)
            is NilLit -> RuntimeValue.PointerVal(null, initialized = true)
            is VarExpr -> {
                val addr = lookupVarAddress(expr.name, env, expr.line)
                val cell = memory[addr]!!
                if (cell.value is RuntimeValue.Uninitialized) {
                    throw RuntimeAlgoException(expr.line, "La variable '${expr.name}' est utilisée avant d'avoir reçu une valeur.")
                }
                cell.value
            }
            is ArrayAccessExpr -> {
                val baseVal = evalExpr(expr.base, env) as? RuntimeValue.ArrayVal
                    ?: throw RuntimeAlgoException(expr.line, "Indexation sur une valeur qui n'est pas un tableau.")
                val indices = expr.indices.map { evalInt(it, env, expr.line) }
                val flatIdx = computeFlatIndex(indices, baseVal.dimensions, "Tableau", expr.line)
                val elemAddr = baseVal.elementAddresses[flatIdx]
                val cell = memory[elemAddr]!!
                if (cell.isFreed) throw RuntimeAlgoException(expr.line, "Accès à un élément déjà libéré.")
                if (cell.value is RuntimeValue.Uninitialized) {
                    throw RuntimeAlgoException(expr.line, "Élément '${cell.label}' non initialisé.")
                }
                cell.value
            }
            is FieldAccessExpr -> {
                val structVal = evalExpr(expr.base, env) as? RuntimeValue.StructVal
                    ?: throw RuntimeAlgoException(expr.line, "Accès à un champ sur une valeur non structurée.")
                val fieldAddr = structVal.fields[expr.fieldName.lowercase()]
                    ?: throw RuntimeAlgoException(expr.line, "Champ '${expr.fieldName}' introuvable.")
                val cell = memory[fieldAddr]!!
                if (cell.value is RuntimeValue.Uninitialized) {
                    throw RuntimeAlgoException(expr.line, "Champ '${cell.label}' non initialisé.")
                }
                cell.value
            }
            is CallExpr -> invokeSubprogram(expr.name, expr.arguments, env, expr.line)
            is AllocExpr -> {
                val heapScope = "Tas (Heap)"
                val addr = allocateTypeDefault("tas#${memory.size + 1}", expr.allocType, heapScope, isHeap = true)
                RuntimeValue.PointerVal(addr, initialized = true)
            }
            is AddrOfExpr -> {
                val addr = resolveLValueAddress(expr.target, env)
                RuntimeValue.PointerVal(addr, initialized = true)
            }
            is DerefExpr -> {
                val ptrVal = evalExpr(expr.pointer, env) as? RuntimeValue.PointerVal
                    ?: throw RuntimeAlgoException(expr.line, "Déréférencement '*' sur un non-pointeur.")
                if (!ptrVal.initialized) {
                    throw RuntimeAlgoException(expr.line, "Déréférencement d'un pointeur non initialisé (Chap. 8, §2.2.2).")
                }
                val addr = ptrVal.targetAddress
                    ?: throw RuntimeAlgoException(expr.line, "Déréférencement d'un pointeur NIL (Chap. 8, §2.2.2).")
                val cell = memory[addr] ?: throw RuntimeAlgoException(expr.line, "Adresse mémoire invalide.")
                if (cell.isFreed) {
                    throw RuntimeAlgoException(expr.line, "Accès à une zone mémoire déjà libérée (Chap. 8, §3).")
                }
                if (cell.value is RuntimeValue.Uninitialized) {
                    throw RuntimeAlgoException(expr.line, "La zone mémoire pointée (${cell.label}) n'a pas encore reçu de valeur.")
                }
                cell.value
            }
            is UnaryExpr -> {
                val v = evalExpr(expr.operand, env)
                when (expr.op) {
                    UnaryOp.NEGATE -> when (v) {
                        is RuntimeValue.IntVal -> RuntimeValue.IntVal(-v.value)
                        is RuntimeValue.RealVal -> RuntimeValue.RealVal(-v.value)
                        else -> throw RuntimeAlgoException(expr.line, "Négation arithmétique invalide.")
                    }
                    UnaryOp.PLUS -> v
                    UnaryOp.NOT -> {
                        val b = (v as? RuntimeValue.BoolVal)?.value
                            ?: throw RuntimeAlgoException(expr.line, "'NON' requiert un booléen.")
                        RuntimeValue.BoolVal(!b)
                    }
                }
            }
            is BinaryExpr -> evalBinary(expr, env)
        }
    }

    private suspend fun evalBinary(expr: BinaryExpr, env: LinkedHashMap<String, Long>): RuntimeValue {
        // Short-circuit for ET / OU
        if (expr.op == BinaryOp.AND) {
            val l = evalBool(expr.left, env, expr.line)
            return if (!l) RuntimeValue.BoolVal(false) else RuntimeValue.BoolVal(evalBool(expr.right, env, expr.line))
        }
        if (expr.op == BinaryOp.OR) {
            val l = evalBool(expr.left, env, expr.line)
            return if (l) RuntimeValue.BoolVal(true) else RuntimeValue.BoolVal(evalBool(expr.right, env, expr.line))
        }

        val l = evalExpr(expr.left, env)
        val r = evalExpr(expr.right, env)

        return when (expr.op) {
            BinaryOp.ADD -> numericOp(l, r, expr.line, { a, b -> a + b }, { a, b -> a + b })
            BinaryOp.SUB -> numericOp(l, r, expr.line, { a, b -> a - b }, { a, b -> a - b })
            BinaryOp.MUL -> numericOp(l, r, expr.line, { a, b -> a * b }, { a, b -> a * b })
            BinaryOp.DIV_REAL -> {
                val ld = toDouble(l, expr.line)
                val rd = toDouble(r, expr.line)
                if (rd == 0.0) throw RuntimeAlgoException(expr.line, "Division par zéro impossible.")
                RuntimeValue.RealVal(ld / rd)
            }
            BinaryOp.DIV_INT -> {
                val li = (l as? RuntimeValue.IntVal)?.value ?: throw RuntimeAlgoException(expr.line, "'div' requiert deux entiers.")
                val ri = (r as? RuntimeValue.IntVal)?.value ?: throw RuntimeAlgoException(expr.line, "'div' requiert deux entiers.")
                if (ri == 0) throw RuntimeAlgoException(expr.line, "Division entière par zéro impossible.")
                RuntimeValue.IntVal(li / ri)
            }
            BinaryOp.MOD -> {
                val li = (l as? RuntimeValue.IntVal)?.value ?: throw RuntimeAlgoException(expr.line, "'mod' requiert deux entiers.")
                val ri = (r as? RuntimeValue.IntVal)?.value ?: throw RuntimeAlgoException(expr.line, "'mod' requiert deux entiers.")
                if (ri == 0) throw RuntimeAlgoException(expr.line, "Modulo par zéro impossible.")
                RuntimeValue.IntVal(li % ri)
            }
            BinaryOp.POWER -> {
                if (l is RuntimeValue.IntVal && r is RuntimeValue.IntVal && r.value >= 0) {
                    RuntimeValue.IntVal(l.value.toDouble().pow(r.value.toDouble()).toInt())
                } else {
                    RuntimeValue.RealVal(toDouble(l, expr.line).pow(toDouble(r, expr.line)))
                }
            }
            BinaryOp.EQ -> RuntimeValue.BoolVal(valuesEqual(l, r))
            BinaryOp.NEQ -> RuntimeValue.BoolVal(!valuesEqual(l, r))
            BinaryOp.LT -> RuntimeValue.BoolVal(compareValues(l, r, expr.line) < 0)
            BinaryOp.LTE -> RuntimeValue.BoolVal(compareValues(l, r, expr.line) <= 0)
            BinaryOp.GT -> RuntimeValue.BoolVal(compareValues(l, r, expr.line) > 0)
            BinaryOp.GTE -> RuntimeValue.BoolVal(compareValues(l, r, expr.line) >= 0)
            BinaryOp.NAND -> {
                val lb = (l as? RuntimeValue.BoolVal)?.value ?: throw RuntimeAlgoException(expr.line, "Booléen attendu.")
                val rb = (r as? RuntimeValue.BoolVal)?.value ?: throw RuntimeAlgoException(expr.line, "Booléen attendu.")
                RuntimeValue.BoolVal(!(lb && rb))
            }
            BinaryOp.NOR -> {
                val lb = (l as? RuntimeValue.BoolVal)?.value ?: throw RuntimeAlgoException(expr.line, "Booléen attendu.")
                val rb = (r as? RuntimeValue.BoolVal)?.value ?: throw RuntimeAlgoException(expr.line, "Booléen attendu.")
                RuntimeValue.BoolVal(!(lb || rb))
            }
            else -> RuntimeValue.BoolVal(false)
        }
    }

    private fun numericOp(
        l: RuntimeValue,
        r: RuntimeValue,
        line: Int,
        intOp: (Int, Int) -> Int,
        realOp: (Double, Double) -> Double
    ): RuntimeValue {
        return if (l is RuntimeValue.IntVal && r is RuntimeValue.IntVal) {
            RuntimeValue.IntVal(intOp(l.value, r.value))
        } else {
            RuntimeValue.RealVal(realOp(toDouble(l, line), toDouble(r, line)))
        }
    }

    private fun toDouble(v: RuntimeValue, line: Int): Double = when (v) {
        is RuntimeValue.IntVal -> v.value.toDouble()
        is RuntimeValue.RealVal -> v.value
        else -> throw RuntimeAlgoException(line, "Valeur numérique attendue (trouvé $v).")
    }

    private fun valuesEqual(l: RuntimeValue, r: RuntimeValue): Boolean = when {
        l is RuntimeValue.IntVal && r is RuntimeValue.IntVal -> l.value == r.value
        (l is RuntimeValue.IntVal || l is RuntimeValue.RealVal) && (r is RuntimeValue.IntVal || r is RuntimeValue.RealVal) ->
            toDouble(l, 1) == toDouble(r, 1)
        l is RuntimeValue.CharVal && r is RuntimeValue.CharVal -> l.value == r.value
        l is RuntimeValue.StringVal && r is RuntimeValue.StringVal -> l.value == r.value
        l is RuntimeValue.BoolVal && r is RuntimeValue.BoolVal -> l.value == r.value
        l is RuntimeValue.PointerVal && r is RuntimeValue.PointerVal -> l.targetAddress == r.targetAddress
        else -> false
    }

    private fun compareValues(l: RuntimeValue, r: RuntimeValue, line: Int): Int = when {
        (l is RuntimeValue.IntVal || l is RuntimeValue.RealVal) && (r is RuntimeValue.IntVal || r is RuntimeValue.RealVal) ->
            toDouble(l, line).compareTo(toDouble(r, line))
        l is RuntimeValue.CharVal && r is RuntimeValue.CharVal -> l.value.compareTo(r.value)
        l is RuntimeValue.StringVal && r is RuntimeValue.StringVal -> l.value.compareTo(r.value)
        else -> throw RuntimeAlgoException(line, "Comparaison impossible entre '$l' et '$r'.")
    }

    private suspend fun evalInt(expr: Expr, env: LinkedHashMap<String, Long>, line: Int): Int {
        val v = evalExpr(expr, env)
        return (v as? RuntimeValue.IntVal)?.value
            ?: throw RuntimeAlgoException(line, "Un entier était attendu (trouvé $v).")
    }

    private suspend fun evalBool(expr: Expr, env: LinkedHashMap<String, Long>, line: Int): Boolean {
        val v = evalExpr(expr, env)
        return (v as? RuntimeValue.BoolVal)?.value
            ?: throw RuntimeAlgoException(line, "Un booléen était attendu (trouvé $v).")
    }

    private fun coerceValue(v: RuntimeValue, targetType: AlgoType, line: Int): RuntimeValue {
        return when {
            targetType == AlgoType.Reel && v is RuntimeValue.IntVal -> RuntimeValue.RealVal(v.value.toDouble())
            targetType == AlgoType.Entier && v is RuntimeValue.RealVal ->
                throw RuntimeAlgoException(line, "Impossible d'affecter un réel à un entier (Chap. 2, §2).")
            else -> v
        }
    }

    private fun computeFlatIndex(indices: List<Int>, dimensions: List<Int>, label: String, line: Int): Int {
        if (indices.size != dimensions.size) {
            throw RuntimeAlgoException(line, "Nombre d'indices incorrect pour '$label'.")
        }
        var flat = 0
        var stride = 1
        for (k in dimensions.indices.reversed()) {
            val idx = indices[k]
            val size = dimensions[k]
            if (idx < 0 || idx >= size) {
                throw RuntimeAlgoException(
                    line,
                    "Dépassement d'indice sur '$label' : indice $idx hors bornes [0..${size - 1}] (Chap. 5, §2)."
                )
            }
            flat += idx * stride
            stride *= size
        }
        return flat
    }

    private fun unflattenIndex(flatIdx: Int, dimensions: List<Int>): List<Int> {
        val coords = MutableList(dimensions.size) { 0 }
        var rem = flatIdx
        for (k in dimensions.indices.reversed()) {
            coords[k] = rem % dimensions[k]
            rem /= dimensions[k]
        }
        return coords
    }

    private suspend fun readInputValue(label: String, type: AlgoType, line: Int): String {
        if (queuedInputs.isNotEmpty()) {
            return queuedInputs.removeFirst()
        }
        val gate = CompletableDeferred<String>()
        inputGate = gate
        emitSnapshot(
            status = ExecutionStatus.WAITING_INPUT,
            line = line,
            pendingInput = InputPrompt(label, type, line)
        )
        val raw = gate.await()
        inputGate = null
        emitSnapshot(ExecutionStatus.RUNNING, line)
        return raw
    }

    private fun parseInputForType(raw: String, type: AlgoType, line: Int): RuntimeValue {
        val trimmed = raw.trim()
        return when (type) {
            AlgoType.Entier -> RuntimeValue.IntVal(
                trimmed.toIntOrNull()
                    ?: throw RuntimeAlgoException(line, "Entrée invalide '$raw' : un entier est attendu.")
            )
            AlgoType.Reel -> RuntimeValue.RealVal(
                trimmed.replace(',', '.').toDoubleOrNull()
                    ?: throw RuntimeAlgoException(line, "Entrée invalide '$raw' : un nombre réel est attendu.")
            )
            AlgoType.Caractere -> RuntimeValue.CharVal(
                raw.firstOrNull()
                    ?: throw RuntimeAlgoException(line, "Entrée vide : un caractère est attendu.")
            )
            AlgoType.Chaine -> RuntimeValue.StringVal(raw)
            AlgoType.Booleen -> {
                when (Lexer.stripAccents(trimmed).lowercase()) {
                    "vrai", "true", "1" -> RuntimeValue.BoolVal(true)
                    "faux", "false", "0" -> RuntimeValue.BoolVal(false)
                    else -> throw RuntimeAlgoException(line, "Entrée invalide '$raw' : 'Vrai' ou 'Faux' attendu.")
                }
            }
            else -> throw RuntimeAlgoException(line, "Type non lisible avec 'Lire'.")
        }
    }

    private fun inferRuntimeType(v: RuntimeValue): AlgoType = when (v) {
        is RuntimeValue.IntVal -> AlgoType.Entier
        is RuntimeValue.RealVal -> AlgoType.Reel
        is RuntimeValue.CharVal -> AlgoType.Caractere
        is RuntimeValue.StringVal -> AlgoType.Chaine
        is RuntimeValue.BoolVal -> AlgoType.Booleen
        is RuntimeValue.PointerVal -> AlgoType.NullPointer
        else -> AlgoType.ErrorType
    }

    private fun emitSnapshot(
        status: ExecutionStatus,
        line: Int?,
        pendingInput: InputPrompt? = null,
        errorMsg: String? = null
    ) {
        _snapshot.value = ExecutionSnapshot(
            status = status,
            currentLine = line,
            outputLines = outputLines.toList(),
            memoryCells = memory.values.map { it.copy() },
            callStack = callFrames.map { it.first },
            pendingInput = pendingInput,
            errorMessage = errorMsg,
            stepCount = stepCount
        )
    }
}
