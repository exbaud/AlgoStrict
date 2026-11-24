package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DiagnosticSeverity
import com.example.engine.ExecutionStatus
import com.example.engine.StrictnessLevel
import com.example.ui.AlgoViewModel
import com.example.ui.InspectorTab
import com.example.ui.MainUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltLight
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: MainUiState,
    viewModel: AlgoViewModel,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExercisePrompt by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val errorCount = state.analysis.diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = state.analysis.diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
    val errorLines = remember(state.analysis.diagnostics) {
        state.analysis.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.map { it.line }.toSet()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Studio Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StrictnessBadge(
                                strictness = state.strictness,
                                errorCount = errorCount,
                                warningCount = warningCount,
                                onClick = { viewModel.toggleStrictness() }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleDarkEditor() },
                            modifier = Modifier.testTag("toggle_editor_theme_btn")
                        ) {
                            Icon(
                                imageVector = if (state.isDarkEditor) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Changer le thème de l'éditeur"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.generateCCode() },
                            modifier = Modifier.testTag("translate_c_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Traduire en langage C"
                            )
                        }
                        IconButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.testTag("save_file_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Enregistrer l'algorithme"
                            )
                        }
                    }
                }

                // Active TD Exercise Banner
                if (state.activeExercise != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${state.activeExercise.serie} • ${state.activeExercise.title}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { showExercisePrompt = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("exercise_prompt_btn")
                                ) {
                                    Text("Énoncé")
                                }
                                Button(
                                    onClick = { viewModel.verifyActiveExercise() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("verify_exercise_btn")
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Vérifier")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick-Insert Symbol & Keyword Toolbar
        QuickInsertToolbar(
            onInsert = { viewModel.insertSnippet(it) }
        )

        // Code Editor Surface
        val editorBg = if (state.isDarkEditor) Color(0xFF0D1526) else Color(0xFFFFFFFF)
        val editorFg = if (state.isDarkEditor) Color(0xFFE2E8F0) else Color(0xFF0F172A)
        val gutterBg = if (state.isDarkEditor) Color(0xFF111C33) else Color(0xFFF1F5F9)
        val gutterText = if (state.isDarkEditor) Color(0xFF64748B) else Color(0xFF64748B)
        val activeDebugLine = state.execution.currentLine?.takeIf {
            state.execution.status == ExecutionStatus.PAUSED_STEP ||
                state.execution.status == ExecutionStatus.WAITING_INPUT
        }

        val lines = remember(state.editorField.text) {
            state.editorField.text.split('\n')
        }
        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f)
                .background(editorBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(vertical = 8.dp)
            ) {
                // Line Numbers Gutter
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .background(gutterBg)
                        .padding(end = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.forEachIndexed { idx, _ ->
                        val lineNum = idx + 1
                        val isError = lineNum in errorLines
                        val isActive = lineNum == activeDebugLine
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(21.dp)
                                .background(
                                    when {
                                        isActive -> CyanAccent.copy(alpha = 0.25f)
                                        isError -> CrimsonError.copy(alpha = 0.22f)
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isError) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CrimsonError)
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                text = lineNum.toString(),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 21.sp,
                                color = when {
                                    isActive -> CyanAccent
                                    isError -> CrimsonError
                                    else -> gutterText
                                },
                                fontWeight = if (isActive || isError) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Editable Pseudo-code Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(horizontal = 10.dp)
                ) {
                    val visualTransformation = remember(state.isDarkEditor) {
                        PseudoCodeVisualTransformation(state.isDarkEditor)
                    }
                    BasicTextField(
                        value = state.editorField,
                        onValueChange = { viewModel.onCodeChange(it) },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                            color = editorFg
                        ),
                        cursorBrush = SolidColor(CobaltLight),
                        visualTransformation = visualTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .testTag("code_editor_input")
                    )
                }
            }
        }

        // Execution Control Bar
        ExecutionControlBar(
            status = state.execution.status,
            currentLine = state.execution.currentLine,
            onAnalyze = { viewModel.analyzeNow() },
            onRun = { viewModel.runCode(stepByStep = false) },
            onStep = { viewModel.runCode(stepByStep = true) },
            onStepNext = { viewModel.stepNext() },
            onContinue = { viewModel.continueRun() },
            onStop = { viewModel.stopExecution() }
        )

        // Bottom Inspector Tabs (Console | Diagnostics | Mémoire)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = state.inspectorTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = state.inspectorTab == InspectorTab.CONSOLE,
                        onClick = { viewModel.selectInspectorTab(InspectorTab.CONSOLE) },
                        modifier = Modifier.testTag("tab_console"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Console", maxLines = 1)
                            }
                        }
                    )
                    Tab(
                        selected = state.inspectorTab == InspectorTab.DIAGNOSTICS,
                        onClick = { viewModel.selectInspectorTab(InspectorTab.DIAGNOSTICS) },
                        modifier = Modifier.testTag("tab_diagnostics"),
                        text = {
                            val totalDiag = state.analysis.diagnostics.size
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = if (errorCount > 0) Icons.Default.Error else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (errorCount > 0) CrimsonError else EmeraldSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Règles ($totalDiag)", maxLines = 1)
                            }
                        }
                    )
                    Tab(
                        selected = state.inspectorTab == InspectorTab.MEMORY,
                        onClick = { viewModel.selectInspectorTab(InspectorTab.MEMORY) },
                        modifier = Modifier.testTag("tab_memory"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Mémoire", maxLines = 1)
                            }
                        }
                    )
                }

                when (state.inspectorTab) {
                    InspectorTab.CONSOLE -> ConsoleInspector(
                        execution = state.execution,
                        onSubmitInput = { viewModel.submitInput(it) }
                    )
                    InspectorTab.DIAGNOSTICS -> DiagnosticsInspector(
                        diagnostics = state.analysis.diagnostics,
                        onJumpToLine = { viewModel.jumpToLine(it) }
                    )
                    InspectorTab.MEMORY -> MemoryInspector(
                        execution = state.execution
                    )
                }
            }
        }
    }

    // Save File Dialog
    if (showSaveDialog) {
        var titleInput by remember(state.currentTitle) { mutableStateOf(state.currentTitle) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Enregistrer l'algorithme") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Nom du fichier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentFile(titleInput)
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_btn")
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Exercise Prompt Dialog
    if (showExercisePrompt && state.activeExercise != null) {
        val ex = state.activeExercise
        AlertDialog(
            onDismissRequest = { showExercisePrompt = false },
            title = { Text("${ex.serie} — ${ex.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ex.prompt, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Chapitre : ${ex.chapterRef} • Difficulté : ${ex.difficulty}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showExercisePrompt = false }) {
                    Text("Compris")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExercisePrompt = false
                    viewModel.openExercise(ex, useReference = true)
                }) {
                    Text("Voir le corrigé")
                }
            }
        )
    }

    // Exercise Automated Verification Sheet
    if (state.exerciseTestResults != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissExerciseResults() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Résultats des tests automatisés", style = MaterialTheme.typography.titleLarge)
                state.exerciseTestResults.forEach { res ->
                    Surface(
                        color = if (res.passed) EmeraldSuccess.copy(alpha = 0.12f) else CrimsonError.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (res.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (res.passed) EmeraldSuccess else CrimsonError
                            )
                            Column {
                                Text(res.label, style = MaterialTheme.typography.titleSmall)
                                Text(res.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Generated C Code Sheet
    if (state.generatedCCode != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissCCode() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Traduction en langage C", style = MaterialTheme.typography.titleLarge)
                        Text("Partie III — Polycopié IFRI", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(
                        onClick = { clipboard.setText(AnnotatedString(state.generatedCCode)) },
                        modifier = Modifier.testTag("copy_c_code_btn")
                    ) {
                        Text("Copier")
                    }
                }
                Surface(
                    color = Color(0xFF0D1526),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    Text(
                        text = state.generatedCCode,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StrictnessBadge(
    strictness: StrictnessLevel,
    errorCount: Int,
    warningCount: Int,
    onClick: () -> Unit
) {
    val label = if (strictness == StrictnessLevel.ULTRA_STRICT) "Ultra-Strict" else "Strict IFRI"
    val statusColor = when {
        errorCount > 0 -> CrimsonError
        warningCount > 0 -> AmberWarning
        else -> EmeraldSuccess
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(statusColor.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("strictness_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = "$label • ${if (errorCount == 0) "Valide" else "$errorCount err."}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickInsertToolbar(onInsert: (String) -> Unit) {
    val snippets = remember {
        listOf(
            "← " to "←",
            " ;" to ";",
            " : " to ":",
            " ≠ " to "≠",
            " ≤ " to "≤",
            " ≥ " to "≥",
            "Var " to "Var",
            "Début\n    " to "Début",
            "\nFin" to "Fin",
            "Si () Alors\n    \nFinSi" to "Si",
            "Sinon\n    " to "Sinon",
            "Pour i de 0 à \n    \nFinPour" to "Pour",
            "Tant que () Faire\n    \nFinTq" to "Tant que",
            "Ecrire () ;" to "Ecrire",
            "Lire () ;" to "Lire",
            "[]" to "[]",
            "*" to "*",
            "&" to "&",
            "// " to "//"
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(snippets) { (insertText, label) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onInsert(insertText) }
                        .testTag("quick_insert_$label")
                ) {
                    Text(
                        text = label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionControlBar(
    status: ExecutionStatus,
    currentLine: Int?,
    onAnalyze: () -> Unit,
    onRun: () -> Unit,
    onStep: () -> Unit,
    onStepNext: () -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (status == ExecutionStatus.PAUSED_STEP) {
                Button(
                    onClick = onStepNext,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color(0xFF06202A)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("step_next_btn")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Suivant (L.${currentLine ?: "-"})")
                }
                FilledTonalButton(
                    onClick = onContinue,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("step_continue_btn")
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Continuer")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onStop, modifier = Modifier.testTag("stop_exec_btn")) {
                    Icon(Icons.Default.Stop, contentDescription = "Arrêter", tint = CrimsonError)
                }
            } else {
                OutlinedButton(
                    onClick = onAnalyze,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("analyze_btn")
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Analyser")
                }
                Button(
                    onClick = onRun,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("run_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Exécuter")
                }
                FilledTonalButton(
                    onClick = onStep,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("step_start_btn")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pas-à-pas")
                }
                if (status == ExecutionStatus.RUNNING || status == ExecutionStatus.WAITING_INPUT) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onStop, modifier = Modifier.testTag("stop_exec_btn")) {
                        Icon(Icons.Default.Stop, contentDescription = "Arrêter", tint = CrimsonError)
                    }
                }
            }
        }
    }
}
