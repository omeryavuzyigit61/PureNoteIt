package com.notes.purenoteit.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = SharedPrefencesUtil(applicationContext)

        setContent {
            var isDarkTheme by remember {
                val saved = prefs.getLocal("night", Boolean) as? Boolean
                mutableStateOf(saved ?: false)
            }

            PureNoteItTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

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
                        route = "note_detail/{noteId}",
                        arguments = listOf(
                            navArgument("noteId") {
                                type = NavType.IntType
                                defaultValue = -1
                            }
                        )
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                        NoteDetailScreen(
                            noteId = noteId,
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

    override fun onStop() {
        super.onStop()
        // Bitwarden Vault Timeout: Lock session when app is backgrounded
        SecurityUtil.lockVault()
    }
}
