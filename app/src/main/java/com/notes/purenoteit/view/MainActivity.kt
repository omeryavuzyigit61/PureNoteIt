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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.notes.purenoteit.util.ThemeManager
import com.notes.purenoteit.util.ThemeMode
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

        val currentThemeMode = ThemeManager.getThemeMode(this)
        ThemeManager.applyTheme(currentThemeMode)
        val isDarkThemeInitial = ThemeManager.isDarkTheme(this, currentThemeMode)

        // Immediately set native window background to match exact theme color (zero flash)
        val winBgColor = if (isDarkThemeInitial) 0xFF1A1A1C.toInt() else 0xFFF8F8FA.toInt()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(winBgColor))

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { isDarkThemeInitial },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { isDarkThemeInitial }
        )

        activeIntent = intent

        // Ensure default categories exist and notes are loaded
        categoryViewModel.seedDefaultCategories()
        noteViewModel.getAllNotes()

        setContent {
            var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(this@MainActivity)) }
            val isDarkTheme = ThemeManager.isDarkTheme(this@MainActivity, themeMode)

            var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }

            PureNoteItTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Crossfade(
                        targetState = showSplash,
                        animationSpec = tween(300),
                        label = "splashTransition"
                    ) { isSplash ->
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
                                    currentThemeMode = themeMode,
                                    onThemeModeChange = { newMode ->
                                        themeMode = newMode
                                        ThemeManager.setThemeMode(this@MainActivity, newMode)
                                        val dark = ThemeManager.isDarkTheme(this@MainActivity, newMode)
                                        val winColor = if (dark) 0xFF1A1A1C.toInt() else 0xFFF8F8FA.toInt()
                                        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(winColor))
                                    },
                                    onToggleDarkTheme = { newTheme ->
                                        val newMode = if (newTheme) ThemeMode.DARK else ThemeMode.LIGHT
                                        themeMode = newMode
                                        ThemeManager.setThemeMode(this@MainActivity, newMode)
                                        val dark = ThemeManager.isDarkTheme(this@MainActivity, newMode)
                                        val winColor = if (dark) 0xFF1A1A1C.toInt() else 0xFFF8F8FA.toInt()
                                        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(winColor))
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
                                        val targetRoute = "note_detail/$targetId"
                                        val existsInBackStack = try {
                                            navController.getBackStackEntry(targetRoute)
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                        if (existsInBackStack) {
                                            navController.popBackStack(targetRoute, inclusive = false)
                                        } else {
                                            navController.navigate(targetRoute) {
                                                launchSingleTop = true
                                            }
                                        }
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
    }

    override fun onStop() {
        super.onStop()
        // Bitwarden Vault Timeout: Lock session when app is backgrounded
        SecurityUtil.lockVault()
    }
}
