package com.notes.purenoteit.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notes.purenoteit.reminder.AlarmSoundPlayer
import com.notes.purenoteit.ui.screens.AnimatedSplashScreen
import com.notes.purenoteit.ui.screens.CategoryManagementScreen
import com.notes.purenoteit.ui.screens.HomeScreen
import com.notes.purenoteit.ui.screens.NoteDetailScreen
import com.notes.purenoteit.ui.screens.PasswordSettingScreen
import com.notes.purenoteit.ui.theme.PureNoteItTheme
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.SharedPrefencesUtil
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val noteViewModel: NoteViewModel by viewModels()
    private val categoryViewModel: CategoryViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openNoteId = intent.getIntExtra("OPEN_NOTE_ID", -1)
        if (openNoteId != -1) {
            AlarmSoundPlayer.stop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure default categories exist and notes are loaded
        categoryViewModel.seedDefaultCategories()
        noteViewModel.getAllNotes()

        val prefs = SharedPrefencesUtil(applicationContext)

        setContent {
            var isDarkTheme by remember {
                val saved = prefs.getLocal("night", Boolean) as? Boolean
                mutableStateOf(saved ?: false)
            }

            var showSplash by remember { mutableStateOf(true) }

            PureNoteItTheme(darkTheme = isDarkTheme) {
                Crossfade(targetState = showSplash, label = "splashTransition") { isSplash ->
                    if (isSplash) {
                        AnimatedSplashScreen(onAnimationFinish = { showSplash = false })
                    } else {
                        val navController = rememberNavController()

                        val openNoteId = intent?.getIntExtra("OPEN_NOTE_ID", -1) ?: -1
                        LaunchedEffect(openNoteId) {
                            if (openNoteId != -1) {
                                AlarmSoundPlayer.stop()
                                navController.navigate("note_detail/$openNoteId")
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                HomeScreen(
                                    noteViewModel = noteViewModel,
                                    categoryViewModel = categoryViewModel,
                                    onNavigateToAddNote = { noteId ->
                                        navController.navigate("note_detail/$noteId")
                                    },
                                    onNavigateToAddNoteWithDate = { noteId, date ->
                                        navController.navigate("note_detail/$noteId?initialDate=$date")
                                    },
                                    onNavigateToPasswordSettings = {
                                        navController.navigate("password_settings")
                                    },
                                    onNavigateToCategorySettings = {
                                        navController.navigate("category_settings")
                                    },
                                    onToggleDarkTheme = { newTheme ->
                                        isDarkTheme = newTheme
                                        prefs.addLocal("night", newTheme)
                                    },
                                    isDarkTheme = isDarkTheme
                                )
                            }

                            composable(
                                route = "note_detail/{noteId}?initialDate={initialDate}",
                                arguments = listOf(
                                    navArgument("noteId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("initialDate") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    }
                                )
                            ) { backStackEntry ->
                                val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                                val initialDate = backStackEntry.arguments?.getLong("initialDate") ?: -1L
                                NoteDetailScreen(
                                    noteId = noteId,
                                    initialDate = initialDate,
                                    noteViewModel = noteViewModel,
                                    categoryViewModel = categoryViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("category_settings") {
                                CategoryManagementScreen(
                                    categoryViewModel = categoryViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("password_settings") {
                                PasswordSettingScreen(
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Bitwarden Vault Timeout: Lock session when app is backgrounded
        SecurityUtil.lockVault()
    }
}
