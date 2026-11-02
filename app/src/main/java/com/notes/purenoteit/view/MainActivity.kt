package com.notes.purenoteit.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
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
import com.notes.purenoteit.util.LanguageManager
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.SharedPrefencesUtil
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val noteViewModel: NoteViewModel by viewModels()
    private val categoryViewModel: CategoryViewModel by viewModels()

    private var activeIntent by mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
        val openNoteId = intent.getIntExtra("OPEN_NOTE_ID", -1)
        if (openNoteId != -1) {
            AlarmSoundPlayer.stop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedLang = LanguageManager.getSelectedLanguage(this)
        if (savedLang != LanguageManager.LANG_AUTO) {
            val localeList = LocaleListCompat.forLanguageTags(savedLang)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        activeIntent = intent

        // Ensure default categories exist and notes are loaded
        categoryViewModel.seedDefaultCategories()
        noteViewModel.getAllNotes()

        val prefs = SharedPrefencesUtil(applicationContext)

        setContent {
            var isDarkTheme by remember {
                val saved = prefs.getLocal("night", Boolean) as? Boolean
                mutableStateOf(saved ?: false)
            }

            var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }

            PureNoteItTheme(darkTheme = isDarkTheme) {
                Crossfade(targetState = showSplash, label = "splashTransition") { isSplash ->
                    if (isSplash) {
                        AnimatedSplashScreen(onAnimationFinish = { showSplash = false })
                    } else {
                        val navController = rememberNavController()

                        LaunchedEffect(activeIntent) {
                            val currentIntent = activeIntent
                            if (currentIntent != null) {
                                val openNoteId = currentIntent.getIntExtra("OPEN_NOTE_ID", -1)
                                val widgetAction = currentIntent.getStringExtra("WIDGET_ACTION")
                                if (openNoteId != -1) {
                                    AlarmSoundPlayer.stop()
                                    navController.navigate("note_detail/$openNoteId")
                                } else if (widgetAction != null) {
                                    when (widgetAction) {
                                        "DAILY_AGENDA" -> {
                                            noteViewModel.createDailyAgenda { newNote ->
                                                navController.navigate("note_detail/${newNote.id}")
                                            }
                                        }
                                        "VOICE_NOTE" -> {
                                            navController.navigate("note_detail/-1?autoVoice=true")
                                        }
                                        "OCR_SCAN" -> {
                                            navController.navigate("note_detail/-1?autoOcr=true")
                                        }
                                        else -> {
                                            navController.navigate("note_detail/-1")
                                        }
                                    }
                                }
                                activeIntent = null
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
                                route = "note_detail/{noteId}?initialDate={initialDate}&autoVoice={autoVoice}&autoOcr={autoOcr}",
                                arguments = listOf(
                                    navArgument("noteId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("initialDate") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    },
                                    navArgument("autoVoice") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    },
                                    navArgument("autoOcr") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    }
                                )
                            ) { backStackEntry ->
                                val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                                val initialDate = backStackEntry.arguments?.getLong("initialDate") ?: -1L
                                val autoVoice = backStackEntry.arguments?.getBoolean("autoVoice") ?: false
                                val autoOcr = backStackEntry.arguments?.getBoolean("autoOcr") ?: false

                                NoteDetailScreen(
                                    noteId = noteId,
                                    initialDate = initialDate,
                                    autoVoice = autoVoice,
                                    autoOcr = autoOcr,
                                    noteViewModel = noteViewModel,
                                    categoryViewModel = categoryViewModel,
                                    onNavigateBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        }
                                    },
                                    onNavigateToNote = { targetId ->
                                        navController.navigate("note_detail/$targetId")
                                    }
                                )
                            }

                            composable("category_settings") {
                                CategoryManagementScreen(
                                    categoryViewModel = categoryViewModel,
                                    onNavigateBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }

                            composable("password_settings") {
                                PasswordSettingScreen(
                                    onNavigateBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        }
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
