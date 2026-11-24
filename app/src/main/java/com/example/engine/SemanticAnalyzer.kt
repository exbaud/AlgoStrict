package com.example.engine

sealed class SymbolInfo {
    data class Constant(val name: String, val type: AlgoType, val intValue: Int?, val line: Int) : SymbolInfo()
    data class Variable(val name: String, val type: AlgoType, val line: Int) : SymbolInfo()
}

data class StructDef(
    val name: String,
    val fields: LinkedHashMap<String, AlgoType>,
    val orderIndex: Int,
    val line: Int
)

data class SubprogramDef(
    val decl: SubprogramDecl,
    val paramTypes: List<AlgoType>,
    val returnType: AlgoType
)

data class AnalysisResult(
    val program: ProgramNode?,
    val diagnostics: List<Diagnostic>,
    val structs: Map<String, StructDef>,
    val subprograms: Map<String, SubprogramDef>,
    val globalConstants: Map<String, SymbolInfo.Constant>
) {
    val hasErrors: Boolean get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
}

class SemanticAnalyzer(
    private val strictness: StrictnessLevel = StrictnessLevel.STANDARD_IFRI
) {
    private val diagnostics = mutableListOf<Diagnostic>()
    private val structs = linkedMapOf<String, StructDef>()
    private val subprograms = linkedMapOf<String, SubprogramDef>()
    private val globalSymbols = linkedMapOf<String, SymbolInfo>()

    fun analyze(source: String): AnalysisResult {
        val lexResult = Lexer(source, strictness).tokenize()
        diagnostics.addAll(lexResult.diagnostics)

        val parser = Parser(lexResult.tokens, strictness)
        val program = parser.parse()
        diagnostics.addAll(parser.diagnostics)

        if (program != null) {
            analyzeProgram(program)
        }

        val constMap = globalSymbols.values
            .filterIsInstance<SymbolInfo.Constant>()
            .associateBy { it.name.lowercase() }

        return AnalysisResult(
            program = program,
            diagnostics = diagnostics.sortedWith(compareBy({ it.line }, { it.column })),
            structs = structs,
            subprograms = subprograms,
            globalConstants = constMap
        )
    }

    private fun analyzeProgram(program: ProgramNode) {
        // 1. Register Structures in order (nested structs must be declared BEFORE use, Chap. 6 §5)
        program.structs.forEachIndexed { idx, s ->
            val key = s.name.lowercase()
            if (structs.containsKey(key)) {
                error(s.line, "La structure '${s.name}' est déjà déclarée.", "Chap. 6, §3")
            } else {
                val fieldMap = linkedMapOf<String, AlgoType>()
                for (f in s.fields) {
                    val resolvedType = resolveAndValidateType(f.type, f.line, maxStructOrder = idx)
                    for (fname in f.names) {
                        val fKey = fname.lowercase()
                        if (fieldMap.containsKey(fKey)) {
                            error(f.line, "Champ '$fname' déclaré en double dans la structure '${s.name}'.", "Chap. 6, §3")
                        } else {
                            fieldMap[fKey] = resolvedType
                        }
                    }
                }
                structs[key] = StructDef(s.name, fieldMap, idx, s.line)
            }
        }

        // 2. Global Constants
        for (c in program.constants) {
            val key = c.name.lowercase()
            if (globalSymbols.containsKey(key)) {
                error(c.line, "Identificateur '${c.name}' déjà déclaré.", "Chap. 1, §4.2")
            } else {
                val cType = inferExprType(c.value, globalSymbols)
                val intVal = evalConstInt(c.value, globalSymbols)
                globalSymbols[key] = SymbolInfo.Constant(c.name, cType, intVal, c.line)
            }
        }

        // 3. Global Variables
        for (v in program.variables) {
            val resolved = resolveAndValidateType(v.type, v.line)
            for (vName in v.names) {
                val key = vName.lowercase()
                if (globalSymbols.containsKey(key)) {
                    error(v.line, "Variable '$vName' déjà déclarée.", "Chap. 1, §4.2.1")
                } else {
                    globalSymbols[key] = SymbolInfo.Variable(vName, resolved, v.line)
                }
            }
        }

        // 4. Global Arrays
        for (a in program.arrays) {
            val key = a.name.lowercase()
            if (globalSymbols.containsKey(key)) {
                error(a.line, "Tableau '${a.name}' déjà déclaré.", "Chap. 5, §2.1")
            } else {
                val resolvedElem = resolveAndValidateType(a.elementType, a.line)
                val dims = resolveArrayDimensions(a.dimensionExprs, globalSymbols, a.line)
                globalSymbols[key] = SymbolInfo.Variable(a.name, AlgoType.Array(resolvedElem, dims), a.line)
            }
        }

        // 5. Register subprogram signatures first (supports recursion & mutual calls)
        for (sub in program.subprograms) {
            val key = sub.name.lowercase()
            if (subprograms.containsKey(key) || globalSymbols.containsKey(key)) {
                error(sub.line, "Sous-programme '${sub.name}' déjà déclaré.", "Chap. 7, §2")
            } else {
                val pTypes = sub.params.map { resolveAndValidateType(it.type, it.line) }
                val rType = if (sub.isFunction) {
                    resolveAndValidateType(sub.returnType ?: AlgoType.ErrorType, sub.line)
                } else {
                    AlgoType.Void
                }
                subprograms[key] = SubprogramDef(sub, pTypes, rType)
            }
        }

        // 6. Analyze subprogram bodies
        for (sub in program.subprograms) {
            analyzeSubprogram(sub)
        }

        // 7. Analyze main body
        analyzeStatements(program.body, globalSymbols, expectedReturnType = null, subprogramName = null)
    }

    private fun analyzeSubprogram(sub: SubprogramDecl) {
        // Local scope copies global scope, allowing shadowing (Chap. 7 §2.1)
        val localScope = LinkedHashMap<String, SymbolInfo>(globalSymbols)
        val localNames = mutableSetOf<String>()

        for (p in sub.params) {
            val key = p.name.lowercase()
            if (!localNames.add(key)) {
                error(p.line, "Paramètre '${p.name}' déclaré en double dans '${sub.name}'.", "Chap. 7, §2.2")
            }
            localScope[key] = SymbolInfo.Variable(p.name, resolveAndValidateType(p.type, p.line), p.line)
        }

        for (c in sub.constants) {
            val key = c.name.lowercase()
            if (!localNames.add(key)) {
                error(c.line, "Constante locale '${c.name}' déjà déclarée dans '${sub.name}'.", "Chap. 7, §2.1")
            }
            val cType = inferExprType(c.value, localScope)
            val intVal = evalConstInt(c.value, localScope)
            localScope[key] = SymbolInfo.Constant(c.name, cType, intVal, c.line)
        }

        for (v in sub.variables) {
            val resolved = resolveAndValidateType(v.type, v.line)
            for (vName in v.names) {
                val key = vName.lowercase()
                if (!localNames.add(key)) {
                    error(v.line, "Variable locale '$vName' déjà déclarée dans '${sub.name}'.", "Chap. 7, §2.1")
                }
                localScope[key] = SymbolInfo.Variable(vName, resolved, v.line)
            }
        }

        for (a in sub.arrays) {
            val key = a.name.lowercase()
            if (!localNames.add(key)) {
                error(a.line, "Tableau local '${a.name}' déjà déclaré dans '${sub.name}'.", "Chap. 7, §2.1")
            }
            val resolvedElem = resolveAndValidateType(a.elementType, a.line)
            val dims = resolveArrayDimensions(a.dimensionExprs, localScope, a.line)
            localScope[key] = SymbolInfo.Variable(a.name, AlgoType.Array(resolvedElem, dims), a.line)
        }

        val expectedRet = if (sub.isFunction) {
            resolveAndValidateType(sub.returnType ?: AlgoType.ErrorType, sub.line)
        } else {
            AlgoType.Void
        }

        val hasReturn = analyzeStatements(sub.body, localScope, expectedRet, sub.name)
        if (sub.isFunction && !hasReturn) {
            error(
                sub.line,
                "La fonction '${sub.name}' doit contenir au moins une instruction 'Retourner <résultat> ;'.",
                "Chap. 7, §3.1"
            )
        }
    }

    private fun analyzeStatements(
        stmts: List<Stmt>,
        scope: Map<String, SymbolInfo>,
        expectedReturnType: AlgoType?,
        subprogramName: String?
    ): Boolean {
        var sawReturn = false
        for (stmt in stmts) {
            if (analyzeStmt(stmt, scope, expectedReturnType, subprogramName)) {
                sawReturn = true
            }
        }
        return sawReturn
    }

    private fun analyzeStmt(
        stmt: Stmt,
        scope: Map<String, SymbolInfo>,
        expectedReturnType: AlgoType?,
        subprogramName: String?
    ): Boolean {
        when (stmt) {
            is AssignStmt -> {
                if (stmt.target is VarTarget) {
                    val sym = scope[stmt.target.name.lowercase()]
                    if (sym is SymbolInfo.Constant) {
                        error(
                            stmt.line,
                            "Impossible d'affecter une valeur à la constante '${sym.name}'. Une constante ne figure jamais à gauche d'une affectation.",
                            "Chap. 2, §2"
                        )
                        inferExprType(stmt.value, scope)
                        return false
                    }
                }
                val targetType = inferLValueType(stmt.target, scope)
                val valType = inferExprType(stmt.value, scope)
                if (targetType != AlgoType.ErrorType && valType != AlgoType.ErrorType) {
                    if (!isAssignable(targetType, valType)) {
                        error(
                            stmt.line,
                            "Incompatibilité de types lors de l'affectation : impossible d'affecter '$valType' à '$targetType'.",
                            "Chap. 2, §2 (p. 19)",
                            "Exemple du cours : réel ← entier est autorisé, mais entier ← réel ou entier ← caractère sont interdits."
                        )
                    }
                }
                return false
            }

            is ReadStmt -> {
                for (target in stmt.targets) {
                    if (target is VarTarget) {
                        val sym = scope[target.name.lowercase()]
                        if (sym is SymbolInfo.Constant) {
                            error(stmt.line, "Impossible d'utiliser 'Lire' sur la constante '${sym.name}'.", "Chap. 2, §3")
                            continue
                        }
                    }
                    val t = inferLValueType(target, scope)
                    if (t is AlgoType.Array || t is AlgoType.Struct || t is AlgoType.Pointer) {
                        error(stmt.line, "Impossible de lire directement une variable de type '$t' avec 'Lire'. Lisez ses éléments ou champs.", "Chap. 5 & 6")
                    }
                }
                return false
            }

            is WriteStmt -> {
                for (expr in stmt.expressions) {
                    val t = inferExprType(expr, scope)
                    if (t is AlgoType.Array || t is AlgoType.Struct) {
                        error(stmt.line, "Impossible d'afficher directement un type composé '$t' avec 'Ecrire'. Affichez ses éléments ou champs.", "Chap. 5 & 6")
                    }
                }
                return false
            }

            is IfStmt -> {
                val condType = inferExprType(stmt.condition, scope)
                if (condType != AlgoType.ErrorType && condType != AlgoType.Booleen) {
                    error(stmt.line, "La condition du 'Si' doit être de type booléen (trouvé '$condType').", "Chap. 3, §2.1")
                }
                val r1 = analyzeStatements(stmt.thenBranch, scope, expectedReturnType, subprogramName)
                val r2 = if (stmt.elseBranch != null) {
                    analyzeStatements(stmt.elseBranch, scope, expectedReturnType, subprogramName)
                } else {
                    false
                }
                return r1 || r2
            }

            is SwitchStmt -> {
                val selType = inferExprType(stmt.selector, scope)
                if (selType != AlgoType.ErrorType && selType != AlgoType.Entier && selType != AlgoType.Caractere && selType != AlgoType.Chaine) {
                    error(stmt.line, "Le sélecteur de 'Selon' doit être entier, caractère ou chaîne (trouvé '$selType').", "Chap. 3, §4")
                }
                var sawRet = false
                for (c in stmt.cases) {
                    for (v in c.values) {
                        val vt = inferExprType(v, scope)
                        if (selType != AlgoType.ErrorType && vt != AlgoType.ErrorType && !isAssignable(selType, vt)) {
                            error(c.line, "Valeur de cas '$vt' incompatible avec le sélecteur '$selType' dans 'Selon'.", "Chap. 3, §4")
                        }
                    }
                    if (analyzeStatements(c.body, scope, expectedReturnType, subprogramName)) sawRet = true
                }
                if (stmt.defaultBranch != null) {
                    if (analyzeStatements(stmt.defaultBranch, scope, expectedReturnType, subprogramName)) sawRet = true
                }
                return sawRet
            }

            is ForStmt -> {
                val sym = scope[stmt.varName.lowercase()]
                when (sym) {
                    null -> error(stmt.line, "Variable compteur '${stmt.varName}' non déclarée.", "Chap. 1, §4.2 / Chap. 4, §3")
                    is SymbolInfo.Constant -> error(stmt.line, "Le compteur de 'Pour' ne peut pas être une constante ('${stmt.varName}').", "Chap. 4, §3")
                    is SymbolInfo.Variable -> {
                        if (sym.type != AlgoType.Entier) {
                            error(stmt.line, "Le compteur '${stmt.varName}' de 'Pour' doit être de type entier (trouvé '${sym.type}').", "Chap. 4, §3")
                        }
                    }
                }
                val startT = inferExprType(stmt.start, scope)
                val endT = inferExprType(stmt.end, scope)
                if (startT != AlgoType.ErrorType && startT != AlgoType.Entier) {
                    error(stmt.line, "La valeur initiale de 'Pour' doit être de type entier (trouvé '$startT').", "Chap. 4, §3")
                }
                if (endT != AlgoType.ErrorType && endT != AlgoType.Entier) {
                    error(stmt.line, "La valeur finale de 'Pour' doit être de type entier (trouvé '$endT').", "Chap. 4, §3")
                }
                if (stmt.step != null) {
                    val stepT = inferExprType(stmt.step, scope)
                    if (stepT != AlgoType.ErrorType && stepT != AlgoType.Entier) {
                        error(stmt.line, "Le pas de 'Pour' doit être de type entier (trouvé '$stepT').", "Chap. 4, §3")
                    }
                }
                return analyzeStatements(stmt.body, scope, expectedReturnType, subprogramName)
            }

            is WhileStmt -> {
                val condT = inferExprType(stmt.condition, scope)
                if (condT != AlgoType.ErrorType && condT != AlgoType.Booleen) {
                    error(stmt.line, "La condition de 'Tant que' doit être de type booléen (trouvé '$condT').", "Chap. 4, §4")
                }
                return analyzeStatements(stmt.body, scope, expectedReturnType, subprogramName)
            }

            is RepeatStmt -> {
                val r = analyzeStatements(stmt.body, scope, expectedReturnType, subprogramName)
                val condT = inferExprType(stmt.stopCondition, scope)
                if (condT != AlgoType.ErrorType && condT != AlgoType.Booleen) {
                    error(stmt.line, "La condition d'arrêt de 'Jusqu'à' doit être de type booléen (trouvé '$condT').", "Chap. 4, §6")
                }
                return r
            }

            is CallStmt -> {
                val sub = subprograms[stmt.name.lowercase()]
                if (sub == null) {
                    error(stmt.line, "Procédure '${stmt.name}' non déclarée.", "Chap. 7, §4")
                } else {
                    if (sub.decl.isFunction) {
                        error(
                            stmt.line,
                            "'${sub.decl.name}' est une Fonction qui retourne '${sub.returnType}', elle doit être utilisée dans une expression ou affectation (ex: x ← ${sub.decl.name}(...) ;).",
                            "Chap. 7, §3.2"
                        )
                    }
                    checkCallArgs(stmt.name, stmt.arguments, sub, scope, stmt.line)
                }
                return false
            }

            is ReturnStmt -> {
                if (expectedReturnType == null) {
                    error(stmt.line, "'Retourner' ne peut pas être utilisé dans l'algorithme principal.", "Chap. 7, §3.1")
                } else if (expectedReturnType == AlgoType.Void) {
                    error(stmt.line, "Une Procédure ('$subprogramName') ne peut pas retourner de valeur avec 'Retourner'.", "Chap. 7, §4")
                } else {
                    val retType = inferExprType(stmt.value, scope)
                    if (retType != AlgoType.ErrorType && expectedReturnType != AlgoType.ErrorType && !isAssignable(expectedReturnType, retType)) {
                        error(
                            stmt.line,
                            "Type de retour incompatible dans '$subprogramName' : attendu '$expectedReturnType', trouvé '$retType'.",
                            "Chap. 7, §3.1"
                        )
                    }
                }
                return true
            }

            is FreeStmt -> {
                val t = inferLValueType(stmt.target, scope)
                if (t != AlgoType.ErrorType && t !is AlgoType.Pointer) {
                    error(stmt.line, "'Libérer' s'applique uniquement à un pointeur (trouvé '$t').", "Chap. 8, §3")
                }
                return false
            }
        }
    }

    private fun checkCallArgs(
        callName: String,
        args: List<Expr>,
        sub: SubprogramDef,
        scope: Map<String, SymbolInfo>,
        line: Int
    ) {
        if (args.size != sub.paramTypes.size) {
            error(
                line,
                "Nombre d'arguments incorrect pour '$callName' : attendu ${sub.paramTypes.size}, reçu ${args.size}.",
                "Chap. 7, §2.2"
            )
            args.forEach { inferExprType(it, scope) }
            return
        }
        args.forEachIndexed { idx, argExpr ->
            val argType = inferExprType(argExpr, scope)
            val paramType = sub.paramTypes[idx]
            if (argType != AlgoType.ErrorType && paramType != AlgoType.ErrorType && !isAssignable(paramType, argType)) {
                error(
                    line,
                    "Argument ${idx + 1} de '$callName' incompatible : attendu '$paramType', reçu '$argType'.",
                    "Chap. 7, §2.2"
                )
            }
        }
    }

    private fun inferLValueType(target: LValue, scope: Map<String, SymbolInfo>): AlgoType {
        return when (target) {
            is VarTarget -> {
                val sym = scope[target.name.lowercase()]
                if (sym == null) {
                    error(target.line, "Variable '${target.name}' non déclarée.", "Chap. 1, §4.2")
                    AlgoType.ErrorType
                } else {
                    when (sym) {
                        is SymbolInfo.Constant -> sym.type
                        is SymbolInfo.Variable -> sym.type
                    }
                }
            }
            is ArrayTarget -> {
                val baseType = inferLValueType(target.base, scope)
                for (idx in target.indices) {
                    val idxT = inferExprType(idx, scope)
                    if (idxT != AlgoType.ErrorType && idxT != AlgoType.Entier) {
                        error(target.line, "L'indice d'un tableau doit être strictement de type entier (trouvé '$idxT').", "Chap. 5, §2")
                    }
                }
                if (baseType == AlgoType.ErrorType) return AlgoType.ErrorType
                if (baseType !is AlgoType.Array) {
                    error(target.line, "Indexation '[...]' impossible sur le type '$baseType'.", "Chap. 5, §2")
                    return AlgoType.ErrorType
                }
                if (target.indices.size != baseType.dimensions.size) {
                    error(
                        target.line,
                        "Nombre de dimensions incorrect : attendu ${baseType.dimensions.size}, fourni ${target.indices.size}.",
                        "Chap. 5, §3"
                    )
                }
                baseType.elementType
            }
            is FieldTarget -> {
                val baseType = inferLValueType(target.base, scope)
                if (baseType == AlgoType.ErrorType) return AlgoType.ErrorType
                if (baseType !is AlgoType.Struct) {
                    error(target.line, "Accès au champ '.${target.fieldName}' impossible sur le type '$baseType'.", "Chap. 6, §3")
                    return AlgoType.ErrorType
                }
                val def = structs[baseType.name.lowercase()]
                if (def == null) {
                    error(target.line, "Structure '${baseType.name}' inconnue.", "Chap. 6, §3")
                    return AlgoType.ErrorType
                }
                val fType = def.fields[target.fieldName.lowercase()]
                if (fType == null) {
                    error(target.line, "La structure '${def.name}' ne possède pas de champ '${target.fieldName}'.", "Chap. 6, §3")
                    return AlgoType.ErrorType
                }
                fType
            }
            is DerefTarget -> {
                val ptrType = inferExprType(target.pointerExpr, scope)
                if (ptrType == AlgoType.ErrorType) return AlgoType.ErrorType
                if (ptrType !is AlgoType.Pointer) {
                    error(target.line, "L'opérateur de déréférencement '*' requiert un pointeur (trouvé '$ptrType').", "Chap. 8, §2.2.3")
                    return AlgoType.ErrorType
                }
                ptrType.targetType
            }
        }
    }

    private fun inferExprType(expr: Expr, scope: Map<String, SymbolInfo>): AlgoType {
        return when (expr) {
            is IntLit -> AlgoType.Entier
            is RealLit -> AlgoType.Reel
            is CharLit -> AlgoType.Caractere
            is StringLit -> AlgoType.Chaine
            is BoolLit -> AlgoType.Booleen
            is NilLit -> AlgoType.NullPointer
            is VarExpr -> {
                val sym = scope[expr.name.lowercase()]
                if (sym == null) {
                    error(expr.line, "Identificateur '${expr.name}' non déclaré.", "Chap. 1, §4.2")
                    AlgoType.ErrorType
                } else {
                    when (sym) {
                        is SymbolInfo.Constant -> sym.type
                        is SymbolInfo.Variable -> sym.type
                    }
                }
            }
            is ArrayAccessExpr -> {
                val baseType = inferExprType(expr.base, scope)
                for (idx in expr.indices) {
                    val idxT = inferExprType(idx, scope)
                    if (idxT != AlgoType.ErrorType && idxT != AlgoType.Entier) {
                        error(expr.line, "L'indice d'un tableau doit être strictement de type entier (trouvé '$idxT').", "Chap. 5, §2")
                    }
                }
                if (baseType == AlgoType.ErrorType) return AlgoType.ErrorType
                if (baseType !is AlgoType.Array) {
                    error(expr.line, "Indexation '[...]' impossible sur le type '$baseType'.", "Chap. 5, §2")
                    return AlgoType.ErrorType
                }
                if (expr.indices.size != baseType.dimensions.size) {
                    error(expr.line, "Nombre de dimensions incorrect : attendu ${baseType.dimensions.size}, fourni ${expr.indices.size}.", "Chap. 5, §3")
                }
                baseType.elementType
            }
            is FieldAccessExpr -> {
                val baseType = inferExprType(expr.base, scope)
                if (baseType == AlgoType.ErrorType) return AlgoType.ErrorType
                if (baseType !is AlgoType.Struct) {
                    error(expr.line, "Accès au champ '.${expr.fieldName}' impossible sur le type '$baseType'.", "Chap. 6, §3")
                    return AlgoType.ErrorType
                }
                val def = structs[baseType.name.lowercase()]
                if (def == null) {
                    error(expr.line, "Structure '${baseType.name}' inconnue.", "Chap. 6, §3")
                    return AlgoType.ErrorType
                }
                def.fields[expr.fieldName.lowercase()] ?: run {
                    error(expr.line, "La structure '${def.name}' ne possède pas de champ '${expr.fieldName}'.", "Chap. 6, §3")
                    AlgoType.ErrorType
                }
            }
            is CallExpr -> {
                val sub = subprograms[expr.name.lowercase()]
                if (sub == null) {
                    error(expr.line, "Fonction '${expr.name}' non déclarée.", "Chap. 7, §3")
                    expr.arguments.forEach { inferExprType(it, scope) }
                    AlgoType.ErrorType
                } else {
                    if (!sub.decl.isFunction) {
                        error(expr.line, "'${sub.decl.name}' est une Procédure et ne retourne aucune valeur utilisable dans une expression.", "Chap. 7, §4")
                    }
                    checkCallArgs(expr.name, expr.arguments, sub, scope, expr.line)
                    sub.returnType
                }
            }
            is AllocExpr -> {
                val t = resolveAndValidateType(expr.allocType, expr.line)
                AlgoType.Pointer(t)
            }
            is AddrOfExpr -> {
                if (expr.target is VarTarget) {
                    val sym = scope[expr.target.name.lowercase()]
                    if (sym is SymbolInfo.Constant) {
                        error(expr.line, "Impossible de prendre l'adresse '&' d'une constante ('${sym.name}').", "Chap. 8, §2.2.3")
                        return AlgoType.ErrorType
                    }
                }
                val t = inferLValueType(expr.target, scope)
                if (t == AlgoType.ErrorType) AlgoType.ErrorType else AlgoType.Pointer(t)
            }
            is DerefExpr -> {
                val t = inferExprType(expr.pointer, scope)
                if (t == AlgoType.ErrorType) AlgoType.ErrorType
                else if (t !is AlgoType.Pointer) {
                    error(expr.line, "Déréférencement '*' impossible sur le type non-pointeur '$t'.", "Chap. 8, §2.2.3")
                    AlgoType.ErrorType
                } else {
                    t.targetType
                }
            }
            is UnaryExpr -> {
                val opT = inferExprType(expr.operand, scope)
                if (opT == AlgoType.ErrorType) return AlgoType.ErrorType
                when (expr.op) {
                    UnaryOp.NEGATE, UnaryOp.PLUS -> {
                        if (!opT.isNumeric()) {
                            error(expr.line, "Opérateur unaire arithmétique inapplicable au type '$opT'.", "Chap. 2, §2")
                            AlgoType.ErrorType
                        } else opT
                    }
                    UnaryOp.NOT -> {
                        if (opT != AlgoType.Booleen) {
                            error(expr.line, "L'opérateur logique 'NON' requiert un booléen (trouvé '$opT').", "Chap. 2, §2 (b)")
                            AlgoType.ErrorType
                        } else AlgoType.Booleen
                    }
                }
            }
            is BinaryExpr -> {
                val lT = inferExprType(expr.left, scope)
                val rT = inferExprType(expr.right, scope)
                if (lT == AlgoType.ErrorType || rT == AlgoType.ErrorType) return AlgoType.ErrorType
                when (expr.op) {
                    BinaryOp.ADD, BinaryOp.SUB, BinaryOp.MUL -> {
                        if (!lT.isNumeric() || !rT.isNumeric()) {
                            error(expr.line, "Opération arithmétique impossible entre '$lT' et '$rT'.", "Chap. 1, §4.3")
                            AlgoType.ErrorType
                        } else if (lT == AlgoType.Reel || rT == AlgoType.Reel) AlgoType.Reel else AlgoType.Entier
                    }
                    BinaryOp.DIV_REAL -> {
                        if (!lT.isNumeric() || !rT.isNumeric()) {
                            error(expr.line, "La division '/' requiert des opérandes numériques (trouvé '$lT' et '$rT').", "Chap. 1, §4.3.2")
                            AlgoType.ErrorType
                        } else AlgoType.Reel
                    }
                    BinaryOp.DIV_INT, BinaryOp.MOD -> {
                        if (lT != AlgoType.Entier || rT != AlgoType.Entier) {
                            val opName = if (expr.op == BinaryOp.DIV_INT) "div" else "mod"
                            error(expr.line, "L'opérateur '$opName' s'applique uniquement aux entiers (trouvé '$lT' et '$rT').", "Chap. 1, §4.3.1")
                            AlgoType.ErrorType
                        } else AlgoType.Entier
                    }
                    BinaryOp.POWER -> {
                        if (!lT.isNumeric() || !rT.isNumeric()) {
                            error(expr.line, "L'opérateur puissance requiert des opérandes numériques.", "Chap. 2, §2 (a)")
                            AlgoType.ErrorType
                        } else if (lT == AlgoType.Reel || rT == AlgoType.Reel) AlgoType.Reel else AlgoType.Entier
                    }
                    BinaryOp.LT, BinaryOp.LTE, BinaryOp.GT, BinaryOp.GTE -> {
                        val valid = (lT.isNumeric() && rT.isNumeric()) ||
                            (lT == AlgoType.Caractere && rT == AlgoType.Caractere) ||
                            (lT == AlgoType.Chaine && rT == AlgoType.Chaine)
                        if (!valid) {
                            error(expr.line, "Comparaison impossible entre '$lT' et '$rT'.", "Chap. 2, §2 (c)")
                            AlgoType.ErrorType
                        } else AlgoType.Booleen
                    }
                    BinaryOp.EQ, BinaryOp.NEQ -> {
                        val valid = isAssignable(lT, rT) || isAssignable(rT, lT) ||
                            (lT.isNumeric() && rT.isNumeric())
                        if (!valid) {
                            error(expr.line, "Comparaison d'égalité impossible entre '$lT' et '$rT'.", "Chap. 2, §2 (c)")
                            AlgoType.ErrorType
                        } else AlgoType.Booleen
                    }
                    BinaryOp.AND, BinaryOp.OR, BinaryOp.NAND, BinaryOp.NOR -> {
                        if (lT != AlgoType.Booleen || rT != AlgoType.Booleen) {
                            error(expr.line, "Les opérateurs logiques (ET, OU, NON ET, NON OU) requièrent des booléens (trouvé '$lT' et '$rT').", "Chap. 2, §2 (b)")
                            AlgoType.ErrorType
                        } else AlgoType.Booleen
                    }
                }
            }
        }
    }

    private fun resolveAndValidateType(type: AlgoType, line: Int, maxStructOrder: Int? = null): AlgoType {
        return when (type) {
            is AlgoType.Struct -> {
                val def = structs[type.name.lowercase()]
                if (def == null) {
                    error(
                        line,
                        "Structure '${type.name}' inconnue ou déclarée après son utilisation.",
                        "Chap. 6, §5",
                        "Une structure utilisée comme champ d'une autre structure doit être déclarée avant celle-ci."
                    )
                    AlgoType.ErrorType
                } else if (maxStructOrder != null && def.orderIndex >= maxStructOrder) {
                    error(
                        line,
                        "La structure '${type.name}' doit être déclarée avant la structure qui l'utilise.",
                        "Chap. 6, §5"
                    )
                    AlgoType.ErrorType
                } else {
                    AlgoType.Struct(def.name)
                }
            }
            is AlgoType.Pointer -> AlgoType.Pointer(resolveAndValidateType(type.targetType, line, maxStructOrder))
            is AlgoType.Array -> AlgoType.Array(resolveAndValidateType(type.elementType, line, maxStructOrder), type.dimensions)
            else -> type
        }
    }

    private fun resolveArrayDimensions(
        exprs: List<Expr>,
        scope: Map<String, SymbolInfo>,
        line: Int
    ): List<Int> {
        val dims = mutableListOf<Int>()
        for (e in exprs) {
            val t = inferExprType(e, scope)
            if (t != AlgoType.ErrorType && t != AlgoType.Entier) {
                error(line, "La taille d'un tableau doit être un entier constant (trouvé '$t').", "Chap. 5, §2.1")
                dims.add(1)
                continue
            }
            val size = evalConstInt(e, scope)
            if (size == null) {
                error(line, "La taille du tableau doit être une constante entière connue à la compilation.", "Chap. 5, §2.1", "Utilisez un littéral entier (ex: 10) ou une Constante (ex: Const N = 10 ;).")
                dims.add(1)
            } else if (size <= 0) {
                error(line, "La taille d'un tableau doit être strictement positive (trouvé $size).", "Chap. 5, §2.1")
                dims.add(1)
            } else {
                dims.add(size)
            }
        }
        return dims
    }

    private fun evalConstInt(expr: Expr, scope: Map<String, SymbolInfo>): Int? {
        return when (expr) {
            is IntLit -> expr.value
            is VarExpr -> (scope[expr.name.lowercase()] as? SymbolInfo.Constant)?.intValue
            is UnaryExpr -> {
                val v = evalConstInt(expr.operand, scope) ?: return null
                when (expr.op) {
                    UnaryOp.NEGATE -> -v
                    UnaryOp.PLUS -> v
                    UnaryOp.NOT -> null
                }
            }
            is BinaryExpr -> {
                val l = evalConstInt(expr.left, scope) ?: return null
                val r = evalConstInt(expr.right, scope) ?: return null
                when (expr.op) {
                    BinaryOp.ADD -> l + r
                    BinaryOp.SUB -> l - r
                    BinaryOp.MUL -> l * r
                    BinaryOp.DIV_INT -> if (r != 0) l / r else null
                    BinaryOp.MOD -> if (r != 0) l % r else null
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun isAssignable(target: AlgoType, value: AlgoType): Boolean {
        if (target == value) return true
        if (target == AlgoType.Reel && value == AlgoType.Entier) return true
        if (target is AlgoType.Pointer && value == AlgoType.NullPointer) return true
        if (target is AlgoType.Pointer && value is AlgoType.Pointer) {
            return isAssignable(target.targetType, value.targetType) && target.targetType == value.targetType
        }
        if (target is AlgoType.Struct && value is AlgoType.Struct) {
            return target.name.equals(value.name, ignoreCase = true)
        }
        return false
    }

    private fun error(line: Int, message: String, chapter: String, hint: String? = null) {
        diagnostics.add(Diagnostic(line, 1, message, chapter, DiagnosticSeverity.ERROR, hint))
    }
}
