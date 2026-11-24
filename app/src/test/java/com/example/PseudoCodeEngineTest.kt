package com.example

import com.example.data.CourseContent
import com.example.engine.CTranslator
import com.example.engine.ExecutionStatus
import com.example.engine.Interpreter
import com.example.engine.SemanticAnalyzer
import com.example.engine.StrictnessLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PseudoCodeEngineTest {

    @Test
    fun `all bundled course examples and exercise solutions pass strict analysis`() {
        for (chapter in CourseContent.chapters) {
            for (ex in chapter.examples) {
                val res = SemanticAnalyzer(StrictnessLevel.STANDARD_IFRI).analyze(ex.code)
                assertFalse(
                    "Example ${ex.id} failed with errors: ${res.diagnostics}",
                    res.hasErrors
                )
            }
        }
        for (ex in CourseContent.exercises) {
            val res = SemanticAnalyzer(StrictnessLevel.STANDARD_IFRI).analyze(ex.referenceSolution)
            assertFalse(
                "Exercise ${ex.id} solution failed with errors: ${res.diagnostics}",
                res.hasErrors
            )
        }
    }

    @Test
    fun `rejects C syntax leaks and assignment with equals`() {
        val code = """
            Algorithme Mauvais_C ;
            Var x : entier ;
            Début
                x = 5 ;
                Si (x == 5 && x != 0) Alors
                    printf("ok") ;
                FinSi
            Fin
        """.trimIndent()
        val res = SemanticAnalyzer().analyze(code)
        assertTrue(res.hasErrors)
        assertTrue(res.diagnostics.any { it.message.contains("'='") || it.message.contains("==") })
        assertTrue(res.diagnostics.any { it.message.contains("&&") })
        assertTrue(res.diagnostics.any { it.message.contains("printf") })
    }

    @Test
    fun `enforces Chap 2 type compatibility table strictly`() {
        val validWidening = """
            Algorithme Test_Reel ;
            Var x : entier ; z : réel ;
            Début
                x ← 5 ;
                z ← x ;
                Ecrire (z) ;
            Fin
        """.trimIndent()
        assertFalse(SemanticAnalyzer().analyze(validWidening).hasErrors)

        val invalidNarrowing = """
            Algorithme Test_Erreur_Type ;
            Var x : entier ; z : réel ; a : caractère ;
            Début
                z ← 3.5 ;
                x ← z ;
                x ← a ;
            Fin
        """.trimIndent()
        val res = SemanticAnalyzer().analyze(invalidNarrowing)
        assertTrue(res.hasErrors)
        assertTrue(res.diagnostics.count { it.message.contains("Incompatibilité de types") } >= 2)
    }

    @Test
    fun `executes Repeter Jusqua as stopping condition per Chap 4`() = runBlocking {
        val code = """
            Algorithme Exo_Repeter ;
            Var encore : booléen ;
            Début
                encore ← Vrai ;
                Répéter
                    Ecrire ("Bonjour") ;
                Jusqu'à (encore) ;
                Ecrire ("fin") ;
            Fin
        """.trimIndent()
        val analysis = SemanticAnalyzer().analyze(code)
        assertFalse(analysis.hasErrors)
        val snap = Interpreter(analysis).execute()
        assertEquals(ExecutionStatus.FINISHED, snap.status)
        assertEquals(listOf("Bonjour", "fin"), snap.outputLines)
    }

    @Test
    fun `executes recursive FACT and variable shadowing per Chap 7`() = runBlocking {
        val factCode = CourseContent.chapters[6].examples[0].code
        val factAnalysis = SemanticAnalyzer().analyze(factCode)
        val interp = Interpreter(factAnalysis)
        interp.setQueuedInputs(listOf("5"))
        val snap = interp.execute()
        assertEquals(ExecutionStatus.FINISHED, snap.status)
        assertTrue(snap.outputLines.last().contains("120"))

        val scopeCode = CourseContent.chapters[6].examples[1].code
        val scopeSnap = Interpreter(SemanticAnalyzer().analyze(scopeCode)).execute()
        assertEquals(
            listOf("Dans Secondaire : x=3 y=8", "Dans Principal : x=5 y=8"),
            scopeSnap.outputLines
        )
    }

    @Test
    fun `executes pointers pass by address and detects NIL dereference per Chap 8`() = runBlocking {
        val ptrCode = CourseContent.chapters[7].examples[1].code
        val ptrSnap = Interpreter(SemanticAnalyzer().analyze(ptrCode)).execute()
        assertEquals(ExecutionStatus.FINISHED, ptrSnap.status)
        assertEquals(
            listOf("Valeur de x avant l'appel : 1", "Valeur de x après l'appel : 2"),
            ptrSnap.outputLines
        )

        val nilCode = """
            Algorithme Nil_Test ;
            Var p : *entier ;
            Début
                p ← NIL ;
                *p ← 10 ;
            Fin
        """.trimIndent()
        val nilAnalysis = SemanticAnalyzer().analyze(nilCode)
        assertFalse(nilAnalysis.hasErrors)
        val nilSnap = Interpreter(nilAnalysis).execute()
        assertEquals(ExecutionStatus.ERROR, nilSnap.status)
        assertTrue(nilSnap.errorMessage?.contains("NIL") == true)
    }

    @Test
    fun `translates Tri_Insertion into valid C code`() {
        val code = CourseContent.chapters[4].examples[1].code
        val analysis = SemanticAnalyzer().analyze(code)
        assertFalse(analysis.hasErrors)
        val cCode = CTranslator(analysis).translate()
        assertTrue(cCode.contains("#include <stdio.h>"))
        assertTrue(cCode.contains("int main()"))
        assertTrue(cCode.contains("while"))
    }

    @Test
    fun `all TD exercise reference solutions pass all their automated test cases`() = runBlocking {
        for (ex in CourseContent.exercises) {
            val analysis = SemanticAnalyzer().analyze(ex.referenceSolution)
            assertFalse("Solution for ${ex.id} has errors", analysis.hasErrors)
            for (tc in ex.testCases) {
                val interp = Interpreter(analysis)
                interp.setQueuedInputs(tc.inputs)
                val snap = interp.execute()
                assertEquals("Exercise ${ex.id} (${tc.label}) failed: ${snap.errorMessage}", ExecutionStatus.FINISHED, snap.status)
                val output = snap.outputLines.joinToString("\n")
                for (expected in tc.expectedSubstrings) {
                    assertTrue(
                        "Exercise ${ex.id} (${tc.label}) missing '$expected' in '$output'",
                        output.contains(expected, ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun `executes Tri_Insertion and nested structures and detects use after Liberer`() = runBlocking {
        val triCode = CourseContent.chapters[4].examples[1].code
        val triSnap = Interpreter(SemanticAnalyzer().analyze(triCode)).execute()
        assertEquals(ExecutionStatus.FINISHED, triSnap.status)
        assertEquals(listOf("Tableau trié :", "2", "4", "10", "13", "18", "22", "25", "31"), triSnap.outputLines)

        val structCode = CourseContent.chapters[5].examples[0].code
        val structSnap = Interpreter(SemanticAnalyzer().analyze(structCode)).execute()
        assertEquals(ExecutionStatus.FINISHED, structSnap.status)
        assertTrue(structSnap.outputLines.first().contains("Amina"))
        assertTrue(structSnap.outputLines.first().contains("2025"))

        val uafCode = """
            Algorithme Use_After_Free ;
            Var p : *entier ;
            Début
                p ← nouveau entier ;
                *p ← 42 ;
                Libérer p ;
                *p ← 99 ;
            Fin
        """.trimIndent()
        val uafAnalysis = SemanticAnalyzer().analyze(uafCode)
        assertFalse(uafAnalysis.hasErrors)
        val uafSnap = Interpreter(uafAnalysis).execute()
        assertEquals(ExecutionStatus.ERROR, uafSnap.status)
        assertTrue(uafSnap.errorMessage?.contains("Libérer") == true)
    }
}
