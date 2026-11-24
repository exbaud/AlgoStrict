package com.example.ui

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlgoFileEntity
import com.example.data.AlgoRepository
import com.example.data.AppDatabase
import com.example.data.CodeTemplate
import com.example.data.CourseContent
import com.example.data.CourseExample
import com.example.data.CourseExercise
import com.example.data.ExerciseProgressEntity
import com.example.engine.AnalysisResult
import com.example.engine.CTranslator
import com.example.engine.ExecutionSnapshot
import com.example.engine.ExecutionStatus
import com.example.engine.Interpreter
import com.example.engine.SemanticAnalyzer
import com.example.engine.StrictnessLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab {
    EDITOR,
    COURSE,
    EXERCISES,
    FILES
}

enum class InspectorTab {
    CONSOLE,
    DIAGNOSTICS,
    MEMORY
}

data class ExerciseTestResult(
    val label: String,
    val passed: Boolean,
    val details: String
)

data class MainUiState(
    val activeTab: MainTab = MainTab.EDITOR,
    val currentFileId: Long? = null,
    val currentTitle: String = "Moyenne_deux_reels",
    val editorField: TextFieldValue = TextFieldValue(CourseContent.chapters[1].examples.first().code),
    val strictness: StrictnessLevel = StrictnessLevel.STANDARD_IFRI,
    val analysis: AnalysisResult = SemanticAnalyzer().analyze(CourseContent.chapters[1].examples.first().code),
    val inspectorTab: InspectorTab = InspectorTab.CONSOLE,
    val execution: ExecutionSnapshot = ExecutionSnapshot(),
    val activeExercise: CourseExercise? = null,
    val exerciseTestResults: List<ExerciseTestResult>? = null,
    val generatedCCode: String? = null,
    val isDarkEditor: Boolean = true,
    val statusBanner: String? = null
)

class AlgoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlgoRepository(AppDatabase.getInstance(application).algoFileDao())

    val savedFiles: StateFlow<List<AlgoFileEntity>> = repository.files
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exerciseProgress: StateFlow<Map<String, ExerciseProgressEntity>> =
        MutableStateFlow<Map<String, ExerciseProgressEntity>>(emptyMap()).also { flow ->
            viewModelScope.launch {
                repository.exerciseProgress.collect { list ->
                    flow.value = list.associateBy { it.exerciseId }
                }
            }
        }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var interpreterJob: Job? = null
    private var activeInterpreter: Interpreter? = null
    private var snapshotCollectJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectInspectorTab(tab: InspectorTab) {
        _uiState.update { it.copy(inspectorTab = tab) }
    }

    fun toggleDarkEditor() {
        _uiState.update { it.copy(isDarkEditor = !it.isDarkEditor) }
    }

    fun toggleStrictness() {
        _uiState.update { state ->
            val next = if (state.strictness == StrictnessLevel.STANDARD_IFRI) {
                StrictnessLevel.ULTRA_STRICT
            } else {
                StrictnessLevel.STANDARD_IFRI
            }
            val result = SemanticAnalyzer(next).analyze(state.editorField.text)
            state.copy(
                strictness = next,
                analysis = result,
                statusBanner = if (next == StrictnessLevel.ULTRA_STRICT) {
                    "Mode Ultra-Strict activé (accents et parenthèses obligatoires)"
                } else {
                    "Mode Strict IFRI standard activé"
                }
            )
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun onCodeChange(newValue: TextFieldValue) {
        val currentText = _uiState.value.editorField.text
        val analysis = if (newValue.text != currentText) {
            SemanticAnalyzer(_uiState.value.strictness).analyze(newValue.text)
        } else {
            _uiState.value.analysis
        }
        _uiState.update {
            it.copy(
                editorField = newValue,
                analysis = analysis,
                exerciseTestResults = null
            )
        }
    }

    fun insertSnippet(snippet: String) {
        val field = _uiState.value.editorField
        val sel = field.selection
        val start = sel.min.coerceIn(0, field.text.length)
        val end = sel.max.coerceIn(0, field.text.length)
        val updated = buildString {
            append(field.text.substring(0, start))
            append(snippet)
            append(field.text.substring(end))
        }
        val newCursor = start + snippet.length
        onCodeChange(
            TextFieldValue(
                text = updated,
                selection = TextRange(newCursor)
            )
        )
    }

    fun jumpToLine(lineNumber: Int) {
        val text = _uiState.value.editorField.text
        val lines = text.split('\n')
        val targetIdx = (lineNumber - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        var offset = 0
        for (i in 0 until targetIdx) {
            offset += lines[i].length + 1
        }
        _uiState.update {
            it.copy(
                editorField = it.editorField.copy(selection = TextRange(offset))
            )
        }
    }

    fun analyzeNow() {
        val state = _uiState.value
        val result = SemanticAnalyzer(state.strictness).analyze(state.editorField.text)
        _uiState.update {
            it.copy(
                analysis = result,
                inspectorTab = if (result.diagnostics.isNotEmpty()) InspectorTab.DIAGNOSTICS else it.inspectorTab,
                statusBanner = if (result.hasErrors) {
                    "${result.diagnostics.count { d -> d.severity == com.example.engine.DiagnosticSeverity.ERROR }} erreur(s) stricte(s) détectée(s)"
                } else {
                    "Analyse réussie : 0 erreur (conforme au cours IFRI)"
                }
            )
        }
    }

    fun runCode(stepByStep: Boolean = false) {
        stopExecution()
        val state = _uiState.value
        val analysis = SemanticAnalyzer(state.strictness).analyze(state.editorField.text)
        if (analysis.hasErrors) {
            _uiState.update {
                it.copy(
                    analysis = analysis,
                    inspectorTab = InspectorTab.DIAGNOSTICS,
                    statusBanner = "Exécution bloquée : corrigez les erreurs strictes."
                )
            }
            return
        }

        val interpreter = Interpreter(analysis)
        activeInterpreter = interpreter

        _uiState.update {
            it.copy(
                analysis = analysis,
                inspectorTab = if (stepByStep) InspectorTab.MEMORY else InspectorTab.CONSOLE
            )
        }

        snapshotCollectJob = viewModelScope.launch {
            interpreter.snapshot.collect { snap ->
                _uiState.update { it.copy(execution = snap) }
            }
        }

        interpreterJob = viewModelScope.launch {
            withContext(Dispatchers.Default) {
                interpreter.execute(stepByStep = stepByStep)
            }
        }
    }

    fun stepNext() {
        activeInterpreter?.stepNext()
    }

    fun continueRun() {
        activeInterpreter?.continueRun()
    }

    fun submitInput(input: String) {
        activeInterpreter?.submitInput(input)
    }

    fun stopExecution() {
        interpreterJob?.cancel()
        snapshotCollectJob?.cancel()
        interpreterJob = null
        snapshotCollectJob = null
        activeInterpreter = null
        _uiState.update {
            it.copy(execution = ExecutionSnapshot(status = ExecutionStatus.IDLE))
        }
    }

    fun generateCCode() {
        val state = _uiState.value
        val analysis = SemanticAnalyzer(state.strictness).analyze(state.editorField.text)
        if (analysis.hasErrors) {
            _uiState.update {
                it.copy(
                    analysis = analysis,
                    inspectorTab = InspectorTab.DIAGNOSTICS,
                    statusBanner = "Corrigez les erreurs strictes avant la traduction en C."
                )
            }
            return
        }
        val cCode = CTranslator(analysis).translate()
        _uiState.update { it.copy(generatedCCode = cCode) }
    }

    fun dismissCCode() {
        _uiState.update { it.copy(generatedCCode = null) }
    }

    fun loadCourseExample(example: CourseExample) {
        stopExecution()
        val strictness = _uiState.value.strictness
        _uiState.update {
            it.copy(
                activeTab = MainTab.EDITOR,
                currentFileId = null,
                currentTitle = example.title,
                activeExercise = null,
                exerciseTestResults = null,
                editorField = TextFieldValue(example.code),
                analysis = SemanticAnalyzer(strictness).analyze(example.code),
                inspectorTab = InspectorTab.CONSOLE,
                statusBanner = "Exemple chargé : ${example.title}"
            )
        }
    }

    fun openExercise(exercise: CourseExercise, useReference: Boolean = false) {
        stopExecution()
        val saved = exerciseProgress.value[exercise.id]
        val codeToLoad = when {
            useReference -> exercise.referenceSolution
            saved != null && saved.lastCode.isNotBlank() -> saved.lastCode
            else -> exercise.starterCode
        }
        val strictness = _uiState.value.strictness
        _uiState.update {
            it.copy(
                activeTab = MainTab.EDITOR,
                currentFileId = null,
                currentTitle = exercise.serie,
                activeExercise = exercise,
                exerciseTestResults = null,
                editorField = TextFieldValue(codeToLoad),
                analysis = SemanticAnalyzer(strictness).analyze(codeToLoad),
                inspectorTab = InspectorTab.CONSOLE
            )
        }
    }

    fun verifyActiveExercise() {
        val exercise = _uiState.value.activeExercise ?: return
        val code = _uiState.value.editorField.text
        val analysis = SemanticAnalyzer(_uiState.value.strictness).analyze(code)

        if (analysis.hasErrors) {
            _uiState.update {
                it.copy(
                    analysis = analysis,
                    inspectorTab = InspectorTab.DIAGNOSTICS,
                    exerciseTestResults = listOf(
                        ExerciseTestResult(
                            label = "Validation syntaxique stricte",
                            passed = false,
                            details = "Votre algorithme contient des erreurs strictes de syntaxe ou de typage."
                        )
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            val results = withContext(Dispatchers.Default) {
                exercise.testCases.map { tc ->
                    val interp = Interpreter(analysis)
                    interp.setQueuedInputs(tc.inputs)
                    val snap = interp.execute(stepByStep = false)
                    val fullOutput = snap.outputLines.joinToString("\n")
                    if (snap.status == ExecutionStatus.ERROR) {
                        ExerciseTestResult(tc.label, false, snap.errorMessage ?: "Erreur d'exécution")
                    } else {
                        val missing = tc.expectedSubstrings.filter { exp ->
                            !fullOutput.contains(exp, ignoreCase = true)
                        }
                        if (missing.isEmpty()) {
                            ExerciseTestResult(tc.label, true, "Sortie : ${snap.outputLines.joinToString(" | ")}")
                        } else {
                            ExerciseTestResult(
                                tc.label,
                                false,
                                "Attendu ${missing.joinToString(", ")} — Obtenu : ${fullOutput.ifBlank { "<vide>" }}"
                            )
                        }
                    }
                }
            }
            val allPassed = results.all { it.passed }
            repository.saveExerciseProgress(exercise.id, allPassed, code)
            _uiState.update {
                it.copy(
                    exerciseTestResults = results,
                    statusBanner = if (allPassed) {
                        "Bravo ! Tous les tests de ${exercise.serie} sont validés !"
                    } else {
                        "Certains tests ont échoué. Vérifiez la logique."
                    }
                )
            }
        }
    }

    fun dismissExerciseResults() {
        _uiState.update { it.copy(exerciseTestResults = null) }
    }

    fun saveCurrentFile(title: String) {
        val cleanTitle = title.trim().ifBlank { "Algorithme" }
        val code = _uiState.value.editorField.text
        viewModelScope.launch {
            val savedId = repository.saveFile(_uiState.value.currentFileId, cleanTitle, code)
            _uiState.update {
                it.copy(
                    currentFileId = savedId,
                    currentTitle = cleanTitle,
                    statusBanner = "Enregistré : $cleanTitle"
                )
            }
        }
    }

    fun openSavedFile(file: AlgoFileEntity) {
        stopExecution()
        val strictness = _uiState.value.strictness
        _uiState.update {
            it.copy(
                activeTab = MainTab.EDITOR,
                currentFileId = file.id,
                currentTitle = file.title,
                activeExercise = null,
                exerciseTestResults = null,
                editorField = TextFieldValue(file.code),
                analysis = SemanticAnalyzer(strictness).analyze(file.code),
                inspectorTab = InspectorTab.CONSOLE
            )
        }
    }

    fun createFromTemplate(template: CodeTemplate) {
        stopExecution()
        val strictness = _uiState.value.strictness
        _uiState.update {
            it.copy(
                activeTab = MainTab.EDITOR,
                currentFileId = null,
                currentTitle = template.title,
                activeExercise = null,
                exerciseTestResults = null,
                editorField = TextFieldValue(template.code),
                analysis = SemanticAnalyzer(strictness).analyze(template.code),
                inspectorTab = InspectorTab.CONSOLE
            )
        }
    }

    fun deleteSavedFile(file: AlgoFileEntity) {
        viewModelScope.launch {
            repository.deleteFile(file.id)
            if (_uiState.value.currentFileId == file.id) {
                _uiState.update { it.copy(currentFileId = null) }
            }
        }
    }
}
