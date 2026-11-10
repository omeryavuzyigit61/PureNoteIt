package com.notes.purenoteit.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.lifecycleScope
import com.notes.purenoteit.data.security.AndroidOAuthPkceManager
import com.notes.purenoteit.reminder.AlarmSoundPlayer
import com.notes.purenoteit.security.AppIntegrityGuard
import com.notes.purenoteit.ui.animation.LocalNavAnimatedVisibilityScope
import com.notes.purenoteit.ui.animation.LocalPagerTransitionCoordinator
import com.notes.purenoteit.ui.animation.LocalSharedTransitionScope
import com.notes.purenoteit.ui.animation.PagerTransitionCoordinator
import com.notes.purenoteit.ui.navigation.AppRoute
import com.notes.purenoteit.ui.screens.AnimatedSplashScreen
import com.notes.purenoteit.ui.screens.CategoryManagementScreen
import com.notes.purenoteit.ui.screens.HomeScreen
import com.notes.purenoteit.ui.screens.NoteDetailScreen
import com.notes.purenoteit.ui.screens.PasswordSettingScreen
import com.notes.purenoteit.ui.theme.PureNoteItTheme
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.LanguageManager
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.SharedPrefencesUtil
import com.notes.purenoteit.util.ThemeManager
import com.notes.purenoteit.util.ThemeMode
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.CloudSyncViewModel
import com.notes.purenoteit.viewmodel.CodePlaygroundViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var oauthPkceManager: AndroidOAuthPkceManager

    private val noteViewModel: NoteViewModel by viewModels()
    private val categoryViewModel: CategoryViewModel by viewModels()
    private val codePlaygroundViewModel: CodePlaygroundViewModel by viewModels()
    private val cloudSyncViewModel: CloudSyncViewModel by viewModels()

    private var activeIntent by mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
        lifecycleScope.launch {
            if (oauthPkceManager.handleRedirectIntent(intent)) {
                cloudSyncViewModel.refreshStatus()
                noteViewModel.getAllNotes()
            }
        }
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
        window.decorView.filterTouchesWhenObscured = true

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
        lifecycleScope.launch {
            if (oauthPkceManager.handleRedirectIntent(intent)) {
                cloudSyncViewModel.refreshStatus()
                noteViewModel.getAllNotes()
            }
        }

        // Synchronous RASP gate BEFORE database initialization
        val initialIntegrity = AppIntegrityGuard.checkAppIntegritySync(this)
        if (initialIntegrity.isSafe) {
            categoryViewModel.seedDefaultCategories()
            noteViewModel.getAllNotes()
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(this@MainActivity)) }
            val isDarkTheme = ThemeManager.isDarkTheme(this@MainActivity, themeMode)

            var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            var integrityViolation by remember {
                mutableStateOf(if (!initialIntegrity.isSafe) initialIntegrity.message else null)
            }

            LaunchedEffect(Unit) {
                if (integrityViolation == null) {
                    val portOpen = withContext(Dispatchers.IO) {
                        AppIntegrityGuard.isLocalHookingPortOpen(this@MainActivity)
                    }
                    if (portOpen) {
                        integrityViolation = "Bellek dinleme portu (Frida) tespit edildi. Kasa kilitlendi."
                    }
                }
            }

            LaunchedEffect(integrityViolation) {
                if (integrityViolation != null) {
                    SecurityUtil.lockVault()
                    delay(2500)
                    finishAffinity()
                }
            }

            PureNoteItTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (integrityViolation != null) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = {
                                Text(
                                    text = "Güvenlik Uyarısı",
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            },
                            text = {
                                Text(
                                    text = integrityViolation ?: "Uygulama bütünlüğü doğrulanamadı. Güvenlik nedeniyle uygulama kapatılıyor.",
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { finishAffinity() }) {
                                    Text(text = "Kapat", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    } else {
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
                                    navController.navigate(AppRoute.NoteDetail(noteId = openNoteId))
                                } else if (widgetAction != null) {
                                    when (widgetAction) {
                                        "DAILY_AGENDA" -> {
                                            noteViewModel.createDailyAgenda { newNote ->
                                                navController.navigate(AppRoute.NoteDetail(noteId = newNote.id))
                                            }
                                        }
                                        "VOICE_NOTE" -> {
                                            navController.navigate(AppRoute.NoteDetail(autoVoice = true))
                                        }
                                        "OCR_SCAN" -> {
                                            navController.navigate(AppRoute.NoteDetail(autoOcr = true))
                                        }
                                        else -> {
                                            navController.navigate(AppRoute.NoteDetail())
                                        }
                                    }
                                }
                                activeIntent = null
                            }
                        }

                        val pagerTransitionCoordinator = remember { PagerTransitionCoordinator() }

                        @OptIn(ExperimentalSharedTransitionApi::class)
                        SharedTransitionLayout {
                            CompositionLocalProvider(
                                LocalSharedTransitionScope provides this,
                                LocalPagerTransitionCoordinator provides pagerTransitionCoordinator
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = AppRoute.Home,
                                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                                    exitTransition = { fadeOut(animationSpec = tween(180)) },
                                    popEnterTransition = { fadeIn(animationSpec = tween(180)) },
                                    popExitTransition = { fadeOut(animationSpec = tween(160)) }
                                ) {
                                    composable<AppRoute.Home> {
                                        CompositionLocalProvider(
                                            LocalNavAnimatedVisibilityScope provides this
                                        ) {
                                            HomeScreen(
                                                noteViewModel = noteViewModel,
                                                categoryViewModel = categoryViewModel,
                                                cloudSyncViewModel = cloudSyncViewModel,
                                                onNavigateToAddNote = { noteId ->
                                                    navController.navigate(AppRoute.NoteDetail(noteId = noteId)) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onNavigateToNoteWithScope = { noteId, originScope ->
                                                    navController.navigate(
                                                        AppRoute.NoteDetail(
                                                            noteId = noteId,
                                                            originScope = originScope
                                                        )
                                                    ) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onNavigateToAddNoteWithDate = { noteId, date ->
                                                    navController.navigate(
                                                        AppRoute.NoteDetail(
                                                            noteId = noteId,
                                                            initialDate = date
                                                        )
                                                    ) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onNavigateToAddNoteWithStatus = { status ->
                                                    navController.navigate(
                                                        AppRoute.NoteDetail(initialStatus = status)
                                                    ) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onNavigateToPasswordSettings = {
                                                    navController.navigate(AppRoute.PasswordSettings)
                                                },
                                                onNavigateToCategorySettings = {
                                                    navController.navigate(AppRoute.CategorySettings)
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
                                    }

                                    composable<AppRoute.NoteDetail> { backStackEntry ->
                                        val routeArgs = backStackEntry.toRoute<AppRoute.NoteDetail>()

                                        CompositionLocalProvider(
                                            LocalNavAnimatedVisibilityScope provides this
                                        ) {
                                            NoteDetailScreen(
                                                noteId = routeArgs.noteId,
                                                initialDate = routeArgs.initialDate,
                                                initialStatus = routeArgs.initialStatus,
                                                autoVoice = routeArgs.autoVoice,
                                                autoOcr = routeArgs.autoOcr,
                                                originScope = routeArgs.originScope,
                                                noteViewModel = noteViewModel,
                                                categoryViewModel = categoryViewModel,
                                                codePlaygroundViewModel = codePlaygroundViewModel,
                                                onNavigateBack = {
                                                    if (navController.previousBackStackEntry != null) {
                                                        navController.popBackStack()
                                                    }
                                                },
                                                onNavigateToNote = { targetId ->
                                                    val prevEntry = navController.previousBackStackEntry
                                                    val prevMatchesTarget = prevEntry != null &&
                                                        prevEntry.destination.hasRoute<AppRoute.NoteDetail>() &&
                                                        prevEntry.toRoute<AppRoute.NoteDetail>().noteId == targetId
                                                    if (prevMatchesTarget) {
                                                        navController.popBackStack()
                                                    } else {
                                                        val targetRoute = AppRoute.NoteDetail(noteId = targetId)
                                                        val existsInBackStack = try {
                                                            navController.getBackStackEntry(targetRoute)
                                                            true
                                                        } catch (_: IllegalArgumentException) {
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
                                                }
                                            )
                                        }
                                    }

                                    composable<AppRoute.CategorySettings> {
                                        CategoryManagementScreen(
                                            categoryViewModel = categoryViewModel,
                                            onNavigateBack = {
                                                if (navController.previousBackStackEntry != null) {
                                                    navController.popBackStack()
                                                }
                                            }
                                        )
                                    }

                                    composable<AppRoute.PasswordSettings> {
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
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Vault Timeout: Lock session when app is backgrounded
        SecurityUtil.lockVault()
    }
}
