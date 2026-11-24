package com.example.engine

class Parser(
    private val tokens: List<Token>,
    private val strictness: StrictnessLevel = StrictnessLevel.STANDARD_IFRI
) {
    private var pos = 0
    val diagnostics = mutableListOf<Diagnostic>()

    fun parse(): ProgramNode? {
        if (check(TokenType.EOF)) {
            addError(1, 1, "Le code est vide. Un algorithme doit commencer par 'Algorithme <nom> ;'.", "Chap. 1, §4.1")
            return null
        }

        val algoName: String
        val nameLine: Int
        if (match(TokenType.ALGORITHME)) {
            nameLine = previous().span.line
            val idTok = consumeIdentifier("Nom d'algorithme attendu après 'Algorithme'.", "Chap. 1, §4.1")
            algoName = idTok?.lexeme ?: "SansNom"
            expectSemicolon("Point-virgule ';' attendu après le nom de l'algorithme.", "Chap. 1, §4.1")
        } else {
            val tok = peek()
            nameLine = tok.span.line
            algoName = "SansNom"
            addError(
                tok.span.line,
                tok.span.column,
                "Tout algorithme doit commencer par l'entête 'Algorithme <nom> ;'.",
                "Chap. 1, §4.1",
                "Exemple : Algorithme Mon_Programme ;"
            )
        }

        val constants = mutableListOf<ConstDecl>()
        val structs = mutableListOf<StructDecl>()
        val variables = mutableListOf<VarDecl>()
        val arrays = mutableListOf<ArrayDecl>()
        val subprograms = mutableListOf<SubprogramDecl>()

        parseDeclarations(constants, structs, variables, arrays, subprograms, allowSubprograms = true)

        val beginLine: Int
        if (match(TokenType.DEBUT)) {
            beginLine = previous().span.line
        } else {
            val tok = peek()
            beginLine = tok.span.line
            addError(
                tok.span.line,
                tok.span.column,
                "Mot-clé 'Début' attendu avant le corps de l'algorithme.",
                "Chap. 1, §4.1"
            )
        }

        val body = parseStatementList(stopTypes = setOf(TokenType.FIN, TokenType.EOF))

        val endLine: Int
        if (match(TokenType.FIN)) {
            endLine = previous().span.line
            match(TokenType.SEMICOLON) // tolerate optional ; after Fin
        } else {
            val tok = peek()
            endLine = tok.span.line
            addError(
                tok.span.line,
                tok.span.column,
                "Mot-clé 'Fin' attendu pour terminer l'algorithme.",
                "Chap. 1, §4.1"
            )
        }

        if (!check(TokenType.EOF)) {
            val extra = peek()
            addError(
                extra.span.line,
                extra.span.column,
                "Instructions inattendues après 'Fin' (${extra.lexeme}).",
                "Chap. 1, §4.1"
            )
        }

        return ProgramNode(
            name = algoName,
            nameLine = nameLine,
            constants = constants,
            structs = structs,
            variables = variables,
            arrays = arrays,
            subprograms = subprograms,
            body = body,
            beginLine = beginLine,
            endLine = endLine
        )
    }

    private fun parseDeclarations(
        constants: MutableList<ConstDecl>,
        structs: MutableList<StructDecl>,
        variables: MutableList<VarDecl>,
        arrays: MutableList<ArrayDecl>,
        subprograms: MutableList<SubprogramDecl>,
        allowSubprograms: Boolean
    ) {
        while (!check(TokenType.DEBUT) && !check(TokenType.EOF)) {
            when (peek().type) {
                TokenType.CONST -> parseConstBlock(constants)
                TokenType.VAR -> parseVarBlock(variables)
                TokenType.TABLEAU -> arrays.addAll(parseTableauDecl())
                TokenType.STRUCTURE -> {
                    if (!allowSubprograms) {
                        addError(peek().span.line, peek().span.column, "Les structures doivent être déclarées dans l'entête du programme principal.", "Chap. 6, §3")
                    }
                    structs.add(parseStructDecl())
                }
                TokenType.FONCTION -> {
                    if (!allowSubprograms) {
                        addError(peek().span.line, peek().span.column, "Impossible de déclarer une fonction à l'intérieur d'un sous-programme.", "Chap. 7, §3")
                    }
                    subprograms.add(parseSubprogram(isFunction = true))
                }
                TokenType.PROCEDURE -> {
                    if (!allowSubprograms) {
                        addError(peek().span.line, peek().span.column, "Impossible de déclarer une procédure à l'intérieur d'un sous-programme.", "Chap. 7, §4")
                    }
                    subprograms.add(parseSubprogram(isFunction = false))
                }
                else -> {
                    val tok = peek()
                    addError(
                        tok.span.line,
                        tok.span.column,
                        "Déclaration invalide ou instruction placée avant 'Début' : '${tok.lexeme}'.",
                        "Chap. 1, §4.1",
                        "Toutes les variables, constantes, structures et sous-programmes doivent précéder 'Début'."
                    )
                    // Synchronize to next declaration or Début
                    advance()
                }
            }
        }
    }

    private fun parseConstBlock(constants: MutableList<ConstDecl>) {
        advance() // Const
        if (!check(TokenType.IDENTIFIER)) {
            val tok = peek()
            addError(tok.span.line, tok.span.column, "Nom de constante attendu après 'Const'.", "Chap. 1, §4.2.2")
            return
        }
        while (check(TokenType.IDENTIFIER) && isConstLineAhead()) {
            val idTok = advance()
            if (match(TokenType.ASSIGN)) {
                addError(
                    previous().span.line,
                    previous().span.column,
                    "Dans 'Const', utilisez '=' (et non '←') pour fixer la valeur d'une constante.",
                    "Chap. 1, §4.2.2",
                    "Exemple : Const n = 100 ;"
                )
            } else if (!match(TokenType.EQ)) {
                val tok = peek()
                addError(tok.span.line, tok.span.column, "Symbole '=' attendu dans la déclaration de constante '${idTok.lexeme}'.", "Chap. 1, §4.2.2")
            }
            val valueExpr = parseExpr()
            expectSemicolon("Point-virgule ';' attendu après la déclaration de la constante '${idTok.lexeme}'.", "Chap. 1, §4.2.2")
            constants.add(ConstDecl(idTok.lexeme, valueExpr, idTok.span.line))
        }
    }

    private fun isConstLineAhead(): Boolean {
        val next = peek(1)
        return next.type == TokenType.EQ || next.type == TokenType.ASSIGN
    }

    private fun parseVarBlock(variables: MutableList<VarDecl>) {
        advance() // Var
        if (!check(TokenType.IDENTIFIER)) {
            val tok = peek()
            addError(tok.span.line, tok.span.column, "Nom de variable attendu après 'Var'.", "Chap. 1, §4.2.1")
            return
        }
        while (check(TokenType.IDENTIFIER)) {
            val line = peek().span.line
            val names = mutableListOf<String>()
            names.add(advance().lexeme)
            while (match(TokenType.COMMA)) {
                val nextId = consumeIdentifier("Identificateur attendu après ','.", "Chap. 1, §4.2.1")
                if (nextId != null) names.add(nextId.lexeme)
            }
            if (!match(TokenType.COLON)) {
                val tok = peek()
                addError(tok.span.line, tok.span.column, "Deux-points ':' attendu avant le type dans 'Var'.", "Chap. 1, §4.2.1")
                skipUntilSemicolonOrDecl()
                continue
            }
            val type = parseType()
            expectSemicolon("Point-virgule ';' attendu à la fin de la déclaration de variable.", "Chap. 1, §4.2.1")
            variables.add(VarDecl(names, type, line))
        }
    }

    private fun parseTableauDecl(): List<ArrayDecl> {
        val kw = advance() // Tableau
        val result = mutableListOf<ArrayDecl>()
        do {
            val nameTok = consumeIdentifier("Nom du tableau attendu après 'Tableau'.", "Chap. 5, §2.1")
            val name = nameTok?.lexeme ?: "Tab"
            val dims = mutableListOf<Expr>()
            while (match(TokenType.LBRACKET)) {
                dims.add(parseExpr())
                if (!match(TokenType.RBRACKET)) {
                    addError(peek().span.line, peek().span.column, "Crochet fermant ']' attendu dans la déclaration du tableau '$name'.", "Chap. 5, §2.1")
                    break
                }
            }
            if (dims.isEmpty()) {
                addError(kw.span.line, kw.span.column, "La taille du tableau '$name' doit être indiquée entre crochets (ex: Tableau $name[10] : entier ;).", "Chap. 5, §2.1")
            }
            if (!match(TokenType.COLON)) {
                addError(peek().span.line, peek().span.column, "Deux-points ':' attendu avant le type du tableau '$name'.", "Chap. 5, §2.1")
                skipUntilSemicolonOrDecl()
                return result
            }
            val elemType = parseType()
            expectSemicolon("Point-virgule ';' attendu après la déclaration du tableau '$name'.", "Chap. 5, §2.1")
            result.add(ArrayDecl(name, dims, elemType, nameTok?.span?.line ?: kw.span.line))
            break
        } while (false)
        return result
    }

    private fun parseStructDecl(): StructDecl {
        val kw = advance() // Structure
        val nameTok = consumeIdentifier("Nom de structure attendu après 'Structure'.", "Chap. 6, §3")
        val structName = nameTok?.lexeme ?: "SansNom"
        // Allow optional semicolon if user mistakenly writes it? No, course syntax is `Structure Etudiant` (no semicolon)
        val fields = mutableListOf<StructFieldDecl>()
        while (!check(TokenType.FIN_STRUCTURE) && !check(TokenType.EOF) && !check(TokenType.DEBUT)) {
            if (!check(TokenType.IDENTIFIER)) {
                val tok = peek()
                addError(tok.span.line, tok.span.column, "Nom de champ attendu dans la structure '$structName'.", "Chap. 6, §3")
                advance()
                continue
            }
            val fieldLine = peek().span.line
            val names = mutableListOf<String>()
            names.add(advance().lexeme)
            while (match(TokenType.COMMA)) {
                val id = consumeIdentifier("Nom de champ attendu après ','.", "Chap. 6, §3")
                if (id != null) names.add(id.lexeme)
            }
            if (!match(TokenType.COLON)) {
                addError(peek().span.line, peek().span.column, "Deux-points ':' attendu après le nom du champ.", "Chap. 6, §3")
                skipUntilSemicolonOrDecl()
                continue
            }
            val fType = parseType()
            expectSemicolon("Point-virgule ';' attendu après la déclaration du champ.", "Chap. 6, §3")
            fields.add(StructFieldDecl(names, fType, fieldLine))
        }
        if (!match(TokenType.FIN_STRUCTURE)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'FinStructure' attendu pour fermer 'Structure $structName'.", "Chap. 6, §3")
        } else {
            match(TokenType.SEMICOLON) // optional ; after FinStructure
        }
        return StructDecl(structName, fields, kw.span.line)
    }

    private fun parseSubprogram(isFunction: Boolean): SubprogramDecl {
        val kw = advance() // Fonction or Procédure
        val chapter = if (isFunction) "Chap. 7, §3.1" else "Chap. 7, §4.1"
        val nameTok = consumeIdentifier("Nom du sous-programme attendu.", chapter)
        val subName = nameTok?.lexeme ?: "SousProg"

        val params = mutableListOf<ParamDecl>()
        if (!match(TokenType.LPAREN)) {
            addError(peek().span.line, peek().span.column, "Parenthèses '()' obligatoires dans la définition de '$subName' (même sans paramètres).", chapter)
        } else {
            if (!check(TokenType.RPAREN)) {
                parseParamGroup(params, chapter)
                while (match(TokenType.COMMA) || match(TokenType.SEMICOLON)) {
                    parseParamGroup(params, chapter)
                }
            }
            if (!match(TokenType.RPAREN)) {
                addError(peek().span.line, peek().span.column, "Parenthèse fermante ')' attendue après les paramètres de '$subName'.", chapter)
            }
        }

        val returnType: AlgoType? = if (isFunction) {
            if (!match(TokenType.COLON)) {
                addError(peek().span.line, peek().span.column, "Une fonction doit spécifier son type de retour après ':' (ex: Fonction $subName(...) : entier).", "Chap. 7, §3.1")
                AlgoType.ErrorType
            } else {
                parseType()
            }
        } else {
            if (check(TokenType.COLON)) {
                addError(peek().span.line, peek().span.column, "Une procédure ne retourne aucune valeur et ne doit pas avoir ': type'.", "Chap. 7, §4.1")
                advance()
                parseType()
            }
            null
        }

        val constants = mutableListOf<ConstDecl>()
        val structs = mutableListOf<StructDecl>()
        val variables = mutableListOf<VarDecl>()
        val arrays = mutableListOf<ArrayDecl>()
        val nestedSubs = mutableListOf<SubprogramDecl>()
        parseDeclarations(constants, structs, variables, arrays, nestedSubs, allowSubprograms = false)

        if (!match(TokenType.DEBUT)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'Début' attendu dans le sous-programme '$subName'.", chapter)
        }

        val body = parseStatementList(stopTypes = setOf(TokenType.FIN, TokenType.EOF))

        if (!match(TokenType.FIN)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'Fin' attendu pour terminer '$subName'.", chapter)
        } else {
            match(TokenType.SEMICOLON)
        }

        return SubprogramDecl(
            isFunction = isFunction,
            name = subName,
            params = params,
            returnType = returnType,
            constants = constants,
            variables = variables,
            arrays = arrays,
            body = body,
            line = kw.span.line
        )
    }

    private fun parseParamGroup(out: MutableList<ParamDecl>, chapter: String) {
        val names = mutableListOf<Token>()
        val first = consumeIdentifier("Nom de paramètre attendu.", chapter) ?: return
        names.add(first)
        // Support `a, b : entier` or `a : entier, b : entier`
        while (match(TokenType.COMMA)) {
            val nextId = consumeIdentifier("Nom de paramètre attendu après ','.", chapter) ?: break
            names.add(nextId)
        }
        if (!match(TokenType.COLON)) {
            addError(peek().span.line, peek().span.column, "Type du paramètre attendu après ':' (ex: x : entier).", chapter)
            return
        }
        val type = parseType()
        for (tok in names) {
            out.add(ParamDecl(tok.lexeme, type, tok.span.line))
        }
    }

    private fun parseType(): AlgoType {
        return when (peek().type) {
            TokenType.TYPE_ENTIER -> { advance(); AlgoType.Entier }
            TokenType.TYPE_REEL -> { advance(); AlgoType.Reel }
            TokenType.TYPE_CARACTERE -> { advance(); AlgoType.Caractere }
            TokenType.TYPE_CHAINE -> { advance(); AlgoType.Chaine }
            TokenType.TYPE_BOOLEEN -> { advance(); AlgoType.Booleen }
            TokenType.STAR -> {
                advance()
                val target = parseType()
                AlgoType.Pointer(target)
            }
            TokenType.POINTEUR -> {
                advance()
                if (!match(TokenType.SUR)) {
                    addError(peek().span.line, peek().span.column, "Mot-clé 'sur' attendu après 'pointeur' (ex: pointeur sur entier).", "Chap. 8, §2.2.1")
                }
                val target = parseType()
                AlgoType.Pointer(target)
            }
            TokenType.STRUCTURE -> {
                advance()
                val sName = consumeIdentifier("Nom de la structure attendu après 'Structure'.", "Chap. 6, §3")?.lexeme ?: ""
                AlgoType.Struct(sName)
            }
            TokenType.IDENTIFIER -> {
                val tok = advance()
                AlgoType.Struct(tok.lexeme)
            }
            else -> {
                val tok = peek()
                addError(tok.span.line, tok.span.column, "Type inconnu ou manquant : '${tok.lexeme}'. Types autorisés : entier, réel, caractère, chaîne, booléen, *type, Structure.", "Chap. 1, §4.3")
                AlgoType.ErrorType
            }
        }
    }

    private fun parseStatementList(stopTypes: Set<TokenType>): List<Stmt> {
        val list = mutableListOf<Stmt>()
        while (!check(TokenType.EOF) && !stopTypes.contains(peek().type) && !isFinTantQueAhead()) {
            val stmt = parseStatement()
            if (stmt != null) {
                list.add(stmt)
            }
        }
        return list
    }

    private fun isFinTantQueAhead(): Boolean {
        return check(TokenType.IDENTIFIER) &&
            Lexer.stripAccents(peek().lexeme).lowercase() == "fintant" &&
            peek(1).type == TokenType.QUE
    }

    private fun matchFinTantQue(): Boolean {
        if (match(TokenType.FIN_TQ)) return true
        if (isFinTantQueAhead()) {
            advance(2)
            return true
        }
        return false
    }

    private fun parseStatement(): Stmt? {
        val tok = peek()
        return when (tok.type) {
            TokenType.LIRE -> parseReadStmt()
            TokenType.ECRIRE -> parseWriteStmt()
            TokenType.SI -> parseIfStmt()
            TokenType.SELON -> parseSwitchStmt()
            TokenType.POUR -> parseForStmt()
            TokenType.TANT -> parseWhileStmt()
            TokenType.REPETER -> parseRepeatStmt()
            TokenType.RETOURNER -> parseReturnStmt()
            TokenType.LIBERER -> parseFreeStmt()
            TokenType.STAR, TokenType.IDENTIFIER -> parseAssignOrCallStmt()
            TokenType.SEMICOLON -> {
                advance()
                null
            }
            else -> {
                addError(tok.span.line, tok.span.column, "Instruction inattendue : '${tok.lexeme}'.", "Chap. 2")
                advance()
                null
            }
        }
    }

    private fun parseReadStmt(): Stmt {
        val kw = advance() // Lire
        if (!match(TokenType.LPAREN)) {
            addError(
                peek().span.line,
                peek().span.column,
                "Parenthèses obligatoires dans l'instruction 'Lire(variable) ;'.",
                "Chap. 2, §3"
            )
            skipUntilSemicolonOrDecl()
            return ReadStmt(emptyList(), kw.span.line)
        }
        val targets = mutableListOf<LValue>()
        if (!check(TokenType.RPAREN)) {
            targets.add(parseLValue())
            while (match(TokenType.COMMA)) {
                targets.add(parseLValue())
            }
        } else {
            addError(kw.span.line, kw.span.column, "L'instruction 'Lire' doit contenir au moins une variable.", "Chap. 2, §3")
        }
        if (!match(TokenType.RPAREN)) {
            addError(peek().span.line, peek().span.column, "Parenthèse fermante ')' attendue dans 'Lire(...)'.", "Chap. 2, §3")
        }
        expectSemicolon("Point-virgule ';' attendu après 'Lire(...)'.", "Chap. 2, §3")
        return ReadStmt(targets, kw.span.line)
    }

    private fun parseWriteStmt(): Stmt {
        val kw = advance() // Ecrire
        if (!match(TokenType.LPAREN)) {
            addError(
                peek().span.line,
                peek().span.column,
                "Parenthèses obligatoires dans l'instruction 'Ecrire(expression) ;'.",
                "Chap. 2, §4"
            )
            skipUntilSemicolonOrDecl()
            return WriteStmt(emptyList(), kw.span.line)
        }
        val exprs = mutableListOf<Expr>()
        if (!check(TokenType.RPAREN)) {
            exprs.add(parseExpr())
            while (match(TokenType.COMMA)) {
                exprs.add(parseExpr())
            }
        }
        if (!match(TokenType.RPAREN)) {
            addError(peek().span.line, peek().span.column, "Parenthèse fermante ')' attendue dans 'Ecrire(...)'.", "Chap. 2, §4")
        }
        expectSemicolon("Point-virgule ';' attendu après 'Ecrire(...)'.", "Chap. 2, §4")
        return WriteStmt(exprs, kw.span.line)
    }

    private fun parseIfStmt(): Stmt {
        val kw = advance() // Si
        if (!check(TokenType.LPAREN)) {
            val sev = if (strictness == StrictnessLevel.ULTRA_STRICT) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
            if (strictness == StrictnessLevel.ULTRA_STRICT) {
                addError(peek().span.line, peek().span.column, "Mode Ultra-Strict : mettez la condition du 'Si' entre parenthèses.", "Chap. 3, §2.1", sev = sev)
            }
        }
        val condition = parseExpr()
        if (!match(TokenType.ALORS)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'Alors' attendu après la condition du 'Si'.", "Chap. 3, §2.1")
        }

        val thenBranch = parseStatementList(stopTypes = setOf(TokenType.SINON, TokenType.FIN_SI, TokenType.FIN, TokenType.EOF))
        var elseBranch: List<Stmt>? = null

        if (match(TokenType.SINON)) {
            if (check(TokenType.SI)) {
                // Sinon Si (...) Alors ... FinSi (Course Chap. 3 §3: each Sinon Si opens a nested Si closed by its own FinSi)
                elseBranch = listOf(parseIfStmt())
            } else {
                elseBranch = parseStatementList(stopTypes = setOf(TokenType.FIN_SI, TokenType.FIN, TokenType.EOF))
            }
        }

        if (!match(TokenType.FIN_SI)) {
            addError(
                peek().span.line,
                peek().span.column,
                "Mot-clé 'FinSi' manquant pour le 'Si' ouvert à la ligne ${kw.span.line}.",
                "Chap. 3, §2.1 / §3",
                "Remarque : chaque 'Si' et chaque 'Sinon Si' possède son propre 'FinSi'."
            )
        } else {
            match(TokenType.SEMICOLON)
        }

        return IfStmt(condition, thenBranch, elseBranch, kw.span.line)
    }

    private fun parseSwitchStmt(): Stmt {
        val kw = advance() // Selon
        val selector = parseExpr()
        val cases = mutableListOf<SwitchCase>()
        var defaultBranch: List<Stmt>? = null

        while (!check(TokenType.FIN_SELON) && !check(TokenType.EOF) && !check(TokenType.FIN)) {
            if (match(TokenType.DEFAUT)) {
                if (!match(TokenType.COLON)) {
                    addError(peek().span.line, peek().span.column, "Deux-points ':' attendu après 'défaut'.", "Chap. 3, §4")
                }
                defaultBranch = parseSwitchBranchBody()
                break
            }
            val caseLine = peek().span.line
            val values = mutableListOf<Expr>()
            values.add(parseExpr())
            while (match(TokenType.COMMA)) {
                values.add(parseExpr())
            }
            if (!match(TokenType.COLON)) {
                addError(peek().span.line, peek().span.column, "Deux-points ':' attendu après la valeur du cas dans 'Selon'.", "Chap. 3, §4")
                advance()
                continue
            }
            val body = parseSwitchBranchBody()
            cases.add(SwitchCase(values, body, caseLine))
        }

        if (!match(TokenType.FIN_SELON)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'FinSelon' attendu pour fermer 'Selon' (ligne ${kw.span.line}).", "Chap. 3, §4")
        } else {
            match(TokenType.SEMICOLON)
        }

        return SwitchStmt(selector, cases, defaultBranch, kw.span.line)
    }

    private fun parseSwitchBranchBody(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!check(TokenType.FIN_SELON) && !check(TokenType.DEFAUT) && !check(TokenType.EOF) && !check(TokenType.FIN)) {
            // Check if the next tokens form a new case label: <literal/ident> followed by ':' or ','
            if (isSwitchCaseLabelAhead()) break
            val s = parseStatement()
            if (s != null) stmts.add(s)
        }
        return stmts
    }

    private fun isSwitchCaseLabelAhead(): Boolean {
        var i = 0
        // optional leading minus
        if (peek(i).type == TokenType.MINUS) i++
        val t = peek(i).type
        if (t != TokenType.INT_LITERAL && t != TokenType.CHAR_LITERAL && t != TokenType.STRING_LITERAL && t != TokenType.IDENTIFIER) {
            return false
        }
        i++
        while (peek(i).type == TokenType.COMMA) {
            i++
            if (peek(i).type == TokenType.MINUS) i++
            i++
        }
        return peek(i).type == TokenType.COLON
    }

    private fun parseForStmt(): Stmt {
        val kw = advance() // Pour
        val varTok = consumeIdentifier("Variable compteur attendue après 'Pour'.", "Chap. 4, §3")
        val varName = varTok?.lexeme ?: "i"

        if (match(TokenType.ASSIGN)) {
            addError(
                previous().span.line,
                previous().span.column,
                "Dans la boucle 'Pour', la syntaxe stricte du cours utilise 'de' et non '←'.",
                "Chap. 4, §3",
                "Écrivez : Pour $varName de <début> à <fin>"
            )
        } else if (!match(TokenType.DE)) {
            addError(
                peek().span.line,
                peek().span.column,
                "Mot-clé 'de' attendu après '$varName' dans 'Pour'.",
                "Chap. 4, §3",
                "Syntaxe : Pour indice de début à fin [Pas valeur]"
            )
        }

        val startExpr = parseExpr()

        if (!match(TokenType.A)) {
            if (strictness != StrictnessLevel.ULTRA_STRICT &&
                check(TokenType.IDENTIFIER) &&
                peek().lexeme.equals("a", ignoreCase = true)
            ) {
                advance()
            } else {
                addError(peek().span.line, peek().span.column, "Mot-clé 'à' attendu entre les bornes de la boucle 'Pour'.", "Chap. 4, §3")
            }
        }

        val endExpr = parseExpr()

        val stepExpr = if (match(TokenType.PAS)) {
            parseExpr()
        } else {
            null
        }

        if (match(TokenType.FAIRE)) {
            val sev = if (strictness == StrictnessLevel.ULTRA_STRICT) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
            addError(
                previous().span.line,
                previous().span.column,
                "Le mot-clé 'Faire' ne fait pas partie de la syntaxe 'Pour' du cours (réservé à 'Tant que').",
                "Chap. 4, §3",
                "Supprimez 'Faire' après 'Pour ... de ... à ...'.",
                sev = sev
            )
        }

        val body = parseStatementList(stopTypes = setOf(TokenType.FIN_POUR, TokenType.FIN, TokenType.EOF))

        if (!match(TokenType.FIN_POUR)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'FinPour' manquant pour le 'Pour' ouvert à la ligne ${kw.span.line}.", "Chap. 4, §3")
        } else {
            match(TokenType.SEMICOLON)
        }

        return ForStmt(varName, startExpr, endExpr, stepExpr, body, kw.span.line)
    }

    private fun parseWhileStmt(): Stmt {
        val kw = advance() // Tant
        if (!match(TokenType.QUE)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'que' attendu après 'Tant'.", "Chap. 4, §4")
        }
        val cond = parseExpr()
        if (!match(TokenType.FAIRE)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'Faire' attendu après la condition de 'Tant que'.", "Chap. 4, §4")
        }

        val body = parseStatementList(stopTypes = setOf(TokenType.FIN_TQ, TokenType.FIN, TokenType.EOF))

        if (!matchFinTantQue()) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'FinTq' (ou 'FinTant que') manquant pour le 'Tant que' ouvert à la ligne ${kw.span.line}.", "Chap. 4, §4")
        } else {
            match(TokenType.SEMICOLON)
        }

        return WhileStmt(cond, body, kw.span.line)
    }

    private fun parseRepeatStmt(): Stmt {
        val kw = advance() // Répéter
        val body = parseStatementList(stopTypes = setOf(TokenType.JUSQU_A, TokenType.FIN, TokenType.EOF))
        if (!match(TokenType.JUSQU_A)) {
            addError(peek().span.line, peek().span.column, "Mot-clé 'Jusqu'à' manquant pour le 'Répéter' ouvert à la ligne ${kw.span.line}.", "Chap. 4, §6")
            return RepeatStmt(body, BoolLit(true, kw.span.line), kw.span.line)
        }
        val cond = parseExpr()
        expectSemicolon("Point-virgule ';' attendu après 'Jusqu'à (condition)'.", "Chap. 4, §6")
        return RepeatStmt(body, cond, kw.span.line)
    }

    private fun parseReturnStmt(): Stmt {
        val kw = advance() // Retourner
        val expr = parseExpr()
        expectSemicolon("Point-virgule ';' attendu après 'Retourner'.", "Chap. 7, §3.1")
        return ReturnStmt(expr, kw.span.line)
    }

    private fun parseFreeStmt(): Stmt {
        val kw = advance() // Libérer
        val target = parseLValue()
        expectSemicolon("Point-virgule ';' attendu après 'Libérer'.", "Chap. 8, §3")
        return FreeStmt(target, kw.span.line)
    }

    private fun parseAssignOrCallStmt(): Stmt {
        // Check procedure call: IDENTIFIER ( ... ) ;
        if (check(TokenType.IDENTIFIER) && peek(1).type == TokenType.LPAREN) {
            val id = advance()
            advance() // (
            val args = mutableListOf<Expr>()
            if (!check(TokenType.RPAREN)) {
                args.add(parseExpr())
                while (match(TokenType.COMMA)) {
                    args.add(parseExpr())
                }
            }
            if (!match(TokenType.RPAREN)) {
                addError(peek().span.line, peek().span.column, "Parenthèse fermante ')' attendue dans l'appel de '${id.lexeme}'.", "Chap. 7, §4.2")
            }
            expectSemicolon("Point-virgule ';' attendu après l'appel de procédure '${id.lexeme}(...)'.", "Chap. 7, §4.2")
            return CallStmt(id.lexeme, args, id.span.line)
        }

        val target = parseLValue()
        if (match(TokenType.EQ)) {
            addError(
                previous().span.line,
                previous().span.column,
                "Le symbole '=' est réservé aux comparaisons. Pour affecter une valeur, utilisez '←' ou '<-'.",
                "Chap. 2, §2",
                "Remplacez '=' par '←'."
            )
        } else if (!match(TokenType.ASSIGN)) {
            val tok = peek()
            addError(
                tok.span.line,
                tok.span.column,
                "Symbole d'affectation '←' (ou '<-') attendu ici (trouvé '${tok.lexeme}').",
                "Chap. 2, §2"
            )
            skipUntilSemicolonOrDecl()
            return AssignStmt(target, IntLit(0, target.line), target.line)
        }

        val value = parseExpr()
        expectSemicolon("Point-virgule ';' attendu à la fin de l'instruction d'affectation.", "Chap. 2, §2")
        return AssignStmt(target, value, target.line)
    }

    private fun parseLValue(): LValue {
        if (match(TokenType.STAR)) {
            val starTok = previous()
            val ptrExpr = parseUnary()
            var base: LValue = DerefTarget(ptrExpr, starTok.span.line)
            while (check(TokenType.LBRACKET) || check(TokenType.DOT)) {
                base = continueLValueSuffix(base)
            }
            return base
        }

        val id = consumeIdentifier("Nom de variable attendu.", "Chap. 2, §2")
        var base: LValue = VarTarget(id?.lexeme ?: "", id?.span?.line ?: peek().span.line)
        while (check(TokenType.LBRACKET) || check(TokenType.DOT)) {
            base = continueLValueSuffix(base)
        }
        return base
    }

    private fun continueLValueSuffix(base: LValue): LValue {
        return if (match(TokenType.LBRACKET)) {
            val indices = mutableListOf<Expr>()
            indices.add(parseExpr())
            if (!match(TokenType.RBRACKET)) {
                addError(peek().span.line, peek().span.column, "Crochet fermant ']' attendu.", "Chap. 5, §2")
            }
            while (match(TokenType.LBRACKET)) {
                indices.add(parseExpr())
                if (!match(TokenType.RBRACKET)) {
                    addError(peek().span.line, peek().span.column, "Crochet fermant ']' attendu.", "Chap. 5, §3")
                }
            }
            ArrayTarget(base, indices, base.line)
        } else {
            advance() // .
            val field = consumeIdentifier("Nom du champ attendu après '.'.", "Chap. 6, §3")
            FieldTarget(base, field?.lexeme ?: "", field?.span?.line ?: base.line)
        }
    }

    // Expressions with strict operator precedence (Chapitre 2, §2)
    private fun parseExpr(): Expr = parseOr()

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (true) {
            if (match(TokenType.OU)) {
                val right = parseAnd()
                left = BinaryExpr(left, BinaryOp.OR, right, left.line)
            } else if (check(TokenType.NON) && peek(1).type == TokenType.OU) {
                advance(2)
                val right = parseAnd()
                left = BinaryExpr(left, BinaryOp.NOR, right, left.line)
            } else {
                break
            }
        }
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseEquality()
        while (true) {
            if (match(TokenType.ET)) {
                val right = parseEquality()
                left = BinaryExpr(left, BinaryOp.AND, right, left.line)
            } else if (check(TokenType.NON) && peek(1).type == TokenType.ET) {
                advance(2)
                val right = parseEquality()
                left = BinaryExpr(left, BinaryOp.NAND, right, left.line)
            } else {
                break
            }
        }
        return left
    }

    private fun parseEquality(): Expr {
        var left = parseComparison()
        while (match(TokenType.EQ, TokenType.NEQ)) {
            val op = if (previous().type == TokenType.EQ) BinaryOp.EQ else BinaryOp.NEQ
            val right = parseComparison()
            left = BinaryExpr(left, op, right, left.line)
        }
        return left
    }

    private fun parseComparison(): Expr {
        var left = parseAdditive()
        while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
            val op = when (previous().type) {
                TokenType.LT -> BinaryOp.LT
                TokenType.LTE -> BinaryOp.LTE
                TokenType.GT -> BinaryOp.GT
                else -> BinaryOp.GTE
            }
            val right = parseAdditive()
            left = BinaryExpr(left, op, right, left.line)
        }
        return left
    }

    private fun parseAdditive(): Expr {
        var left = parseMultiplicative()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = if (previous().type == TokenType.PLUS) BinaryOp.ADD else BinaryOp.SUB
            val right = parseMultiplicative()
            left = BinaryExpr(left, op, right, left.line)
        }
        return left
    }

    private fun parseMultiplicative(): Expr {
        var left = parsePower()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.DIV, TokenType.MOD)) {
            val op = when (previous().type) {
                TokenType.STAR -> BinaryOp.MUL
                TokenType.SLASH -> BinaryOp.DIV_REAL
                TokenType.DIV -> BinaryOp.DIV_INT
                else -> BinaryOp.MOD
            }
            val right = parsePower()
            left = BinaryExpr(left, op, right, left.line)
        }
        return left
    }

    private fun parsePower(): Expr {
        val left = parseUnary()
        return if (match(TokenType.POWER)) {
            val right = parsePower()
            BinaryExpr(left, BinaryOp.POWER, right, left.line)
        } else {
            left
        }
    }

    private fun parseUnary(): Expr {
        if (match(TokenType.MINUS)) {
            val line = previous().span.line
            return UnaryExpr(UnaryOp.NEGATE, parseUnary(), line)
        }
        if (match(TokenType.PLUS)) {
            val line = previous().span.line
            return UnaryExpr(UnaryOp.PLUS, parseUnary(), line)
        }
        if (match(TokenType.NON)) {
            val line = previous().span.line
            return UnaryExpr(UnaryOp.NOT, parseUnary(), line)
        }
        if (match(TokenType.STAR)) {
            val line = previous().span.line
            return DerefExpr(parseUnary(), line)
        }
        if (match(TokenType.AMPERSAND)) {
            val line = previous().span.line
            val lval = parseLValue()
            return AddrOfExpr(lval, line)
        }
        return parsePostfix()
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (true) {
            if (match(TokenType.LBRACKET)) {
                val indices = mutableListOf<Expr>()
                indices.add(parseExpr())
                if (!match(TokenType.RBRACKET)) {
                    addError(peek().span.line, peek().span.column, "Crochet fermant ']' attendu.", "Chap. 5, §2")
                }
                while (match(TokenType.LBRACKET)) {
                    indices.add(parseExpr())
                    if (!match(TokenType.RBRACKET)) {
                        addError(peek().span.line, peek().span.column, "Crochet fermant ']' attendu.", "Chap. 5, §3")
                    }
                }
                expr = ArrayAccessExpr(expr, indices, expr.line)
            } else if (match(TokenType.DOT)) {
                val field = consumeIdentifier("Nom de champ attendu après '.'.", "Chap. 6, §3")
                expr = FieldAccessExpr(expr, field?.lexeme ?: "", expr.line)
            } else {
                break
            }
        }
        return expr
    }

    private fun parsePrimary(): Expr {
        val tok = peek()
        return when (tok.type) {
            TokenType.INT_LITERAL -> {
                advance()
                IntLit(tok.lexeme.toIntOrNull() ?: 0, tok.span.line)
            }
            TokenType.REAL_LITERAL -> {
                advance()
                RealLit(tok.lexeme.toDoubleOrNull() ?: 0.0, tok.span.line)
            }
            TokenType.CHAR_LITERAL -> {
                advance()
                CharLit(tok.lexeme.firstOrNull() ?: ' ', tok.span.line)
            }
            TokenType.STRING_LITERAL -> {
                advance()
                StringLit(tok.lexeme, tok.span.line)
            }
            TokenType.BOOL_LITERAL -> {
                advance()
                val b = Lexer.stripAccents(tok.lexeme).lowercase() == "vrai"
                BoolLit(b, tok.span.line)
            }
            TokenType.NIL -> {
                advance()
                NilLit(tok.span.line)
            }
            TokenType.NOUVEAU -> {
                advance()
                val allocType = parseType()
                AllocExpr(allocType, tok.span.line)
            }
            TokenType.IDENTIFIER -> {
                advance()
                if (match(TokenType.LPAREN)) {
                    val args = mutableListOf<Expr>()
                    if (!check(TokenType.RPAREN)) {
                        args.add(parseExpr())
                        while (match(TokenType.COMMA)) {
                            args.add(parseExpr())
                        }
                    }
                    if (!match(TokenType.RPAREN)) {
                        addError(peek().span.line, peek().span.column, "Parenthèse fermante ')' attendue dans l'appel '${tok.lexeme}(...)'.", "Chap. 7, §3.2")
                    }
                    CallExpr(tok.lexeme, args, tok.span.line)
                } else {
                    VarExpr(tok.lexeme, tok.span.line)
                }
            }
            TokenType.LPAREN -> {
                advance()
                val inner = parseExpr()
                if (!match(TokenType.RPAREN)) {
                    addError(peek().span.line, peek().span.column, "Parenthèse fermante ')' attendue.", "Chap. 2")
                }
                inner
            }
            else -> {
                addError(tok.span.line, tok.span.column, "Expression attendue avant '${tok.lexeme}'.", "Chap. 2, §2")
                advance()
                IntLit(0, tok.span.line)
            }
        }
    }

    private fun consumeIdentifier(message: String, chapter: String): Token? {
        if (check(TokenType.IDENTIFIER)) return advance()
        val tok = peek()
        addError(tok.span.line, tok.span.column, "$message (trouvé '${tok.lexeme}').", chapter)
        return null
    }

    private fun expectSemicolon(message: String, chapter: String) {
        if (!match(TokenType.SEMICOLON)) {
            val prev = previous()
            addError(
                prev.span.line,
                prev.span.column + prev.span.length,
                message,
                chapter,
                "En pseudo-code strict IFRI, chaque instruction simple et déclaration se termine par ';'."
            )
        }
    }

    private fun skipUntilSemicolonOrDecl() {
        while (!check(TokenType.EOF) &&
            !check(TokenType.SEMICOLON) &&
            !check(TokenType.DEBUT) &&
            !check(TokenType.FIN) &&
            !check(TokenType.FIN_SI) &&
            !check(TokenType.FIN_POUR) &&
            !check(TokenType.FIN_TQ)
        ) {
            advance()
        }
        match(TokenType.SEMICOLON)
    }

    private fun match(vararg types: TokenType): Boolean {
        for (t in types) {
            if (check(t)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean = peek().type == type

    private fun advance(count: Int = 1): Token {
        repeat(count) {
            if (pos < tokens.size - 1) pos++
        }
        return previous()
    }

    private fun peek(offset: Int = 0): Token {
        val idx = (pos + offset).coerceAtMost(tokens.size - 1)
        return tokens[idx]
    }

    private fun previous(): Token = tokens[(pos - 1).coerceAtLeast(0)]

    private fun addError(
        line: Int,
        column: Int,
        message: String,
        chapterRef: String,
        hint: String? = null,
        sev: DiagnosticSeverity = DiagnosticSeverity.ERROR
    ) {
        diagnostics.add(Diagnostic(line, column, message, chapterRef, sev, hint))
    }
}
