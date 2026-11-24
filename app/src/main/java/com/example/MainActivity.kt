package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AlgoViewModel
import com.example.ui.MainTab
import com.example.ui.editor.EditorScreen
import com.example.ui.screens.CourseScreen
import com.example.ui.screens.ExercisesScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.theme.AlgoStrictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlgoStrictTheme {
                AlgoStrictApp()
            }
        }
    }
}

private data class NavDestination(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AlgoStrictApp(viewModel: AlgoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedFiles by viewModel.savedFiles.collectAsStateWithLifecycle()
    val exerciseProgress by viewModel.exerciseProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    if (uiState.activeTab != MainTab.EDITOR) {
        BackHandler {
            viewModel.selectTab(MainTab.EDITOR)
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(MainTab.EDITOR, "Éditeur", Icons.Filled.Code, Icons.Outlined.Code),
            NavDestination(MainTab.COURSE, "Cours", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
            NavDestination(MainTab.EXERCISES, "Exercices", Icons.Filled.Assignment, Icons.Outlined.Assignment),
            NavDestination(MainTab.FILES, "Fichiers", Icons.Filled.Folder, Icons.Outlined.Folder)
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = uiState.activeTab == dest.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEach { dest ->
                            val selected = uiState.activeTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                }

                when (uiState.activeTab) {
                    MainTab.EDITOR -> EditorScreen(
                        state = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                    MainTab.COURSE -> CourseScreen(
                        onLoadExample = { viewModel.loadCourseExample(it) },
                        modifier = Modifier.weight(1f)
                    )
                    MainTab.EXERCISES -> ExercisesScreen(
                        progressMap = exerciseProgress,
                        onOpenExercise = { ex, ref -> viewModel.openExercise(ex, ref) },
                        modifier = Modifier.weight(1f)
                    )
                    MainTab.FILES -> FilesScreen(
                        files = savedFiles,
                        onOpenFile = { viewModel.openSavedFile(it) },
                        onDeleteFile = { viewModel.deleteSavedFile(it) },
                        onCreateFromTemplate = { viewModel.createFromTemplate(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
