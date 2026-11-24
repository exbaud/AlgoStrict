package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Diagnostic
import com.example.engine.DiagnosticSeverity
import com.example.engine.ExecutionSnapshot
import com.example.engine.ExecutionStatus
import com.example.engine.MemoryCell
import com.example.engine.RuntimeValue
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ConsoleInspector(
    execution: ExecutionSnapshot,
    onSubmitInput: (String) -> Unit
) {
    var inputBuffer by remember(execution.pendingInput) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Inline Lire() input prompt
        if (execution.status == ExecutionStatus.WAITING_INPUT && execution.pendingInput != null) {
            val prompt = execution.pendingInput
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputBuffer,
                        onValueChange = { inputBuffer = it },
                        label = { Text("Lire (${prompt.variableLabel} : ${prompt.expectedType})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                onSubmitInput(inputBuffer)
                                inputBuffer = ""
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("console_lire_input")
                    )
                    Button(
                        onClick = {
                            onSubmitInput(inputBuffer)
                            inputBuffer = ""
                        },
                        modifier = Modifier.testTag("console_lire_submit")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer la saisie")
                    }
                }
            }
        }

        // Runtime Error banner
        if (execution.status == ExecutionStatus.ERROR && execution.errorMessage != null) {
            Surface(
                color = CrimsonError.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = CrimsonError)
                    Text(
                        text = execution.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonError,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Output console box
        Surface(
            color = Color(0xFF0A101D),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (execution.outputLines.isEmpty() && execution.status == ExecutionStatus.IDLE) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Appuyez sur Exécuter ou Pas-à-pas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(execution.outputLines) { line ->
                        Text(
                            text = "> $line",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                    if (execution.status == ExecutionStatus.FINISHED) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "✓ Exécution terminée (${execution.stepCount} étapes)",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = EmeraldSuccess
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsInspector(
    diagnostics: List<Diagnostic>,
    onJumpToLine: (Int) -> Unit
) {
    if (diagnostics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aucune erreur détectée",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldSuccess
                )
                Text(
                    "Votre pseudo-code respecte strictement la grammaire et le typage du cours IFRI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(diagnostics) { diag ->
            val isError = diag.severity == DiagnosticSeverity.ERROR
            val accent = if (isError) CrimsonError else AmberWarning
            Surface(
                color = accent.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onJumpToLine(diag.line) }
                    .testTag("diagnostic_line_${diag.line}")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Error else Icons.Default.Warning,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Ligne ${diag.line}",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = diag.chapterRef,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = diag.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (diag.hint != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = diag.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryInspector(execution: ExecutionSnapshot) {
    if (execution.memoryCells.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Espace mémoire vide",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Lancez le mode Pas-à-pas pour observer les adresses, variables, tableaux et pointeurs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val scalarAndPtrCells = remember(execution.memoryCells) {
        execution.memoryCells.filter {
            it.value !is RuntimeValue.ArrayVal && it.value !is RuntimeValue.StructVal
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Call Stack Strip (Chap. 7 & 8)
        if (execution.callStack.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(execution.callStack) { frame ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = frame,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Memory Table Header (Figure 3 / 8 / 9 du cours)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text("Adresse", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.27f))
            Text("Nom", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.28f))
            Text("Valeur", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.28f))
            Text("Portée", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.17f))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(scalarAndPtrCells, key = { it.address }) { cell ->
                MemoryCellRow(cell)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun MemoryCellRow(cell: MemoryCell) {
    val valStr = if (cell.isFreed) "<LIBÉRÉ>" else cell.value.toString()
    val valColor = when {
        cell.isFreed -> CrimsonError
        cell.value is RuntimeValue.Uninitialized -> MaterialTheme.colorScheme.onSurfaceVariant
        cell.value is RuntimeValue.PointerVal -> CyanAccent
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "0x${cell.address.toString(16)}",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.27f)
        )
        Text(
            text = cell.label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (cell.isConstant) AmberWarning else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.28f)
        )
        Text(
            text = valStr,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            color = valColor,
            modifier = Modifier.weight(0.28f)
        )
        Text(
            text = cell.scopeName.substringBefore("("),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.17f)
        )
    }
}
