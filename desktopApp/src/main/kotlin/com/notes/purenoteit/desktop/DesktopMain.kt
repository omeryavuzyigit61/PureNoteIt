package com.notes.purenoteit.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.notes.purenoteit.desktop.ui.DesktopHomeScreenRoot
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEvent
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Dimension
import java.awt.Image as AwtImage
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

fun main() {
    configureMacOsDesktopEnvironment()
    application {
    val container = remember { DesktopAppContainer() }
    val windowIcon = remember { loadDesktopWindowIcon() }
    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 820.dp,
        position = WindowPosition(Alignment.Center)
    )

    DisposableEffect(Unit) {
        onDispose { container.close() }
    }

    Window(
        onCloseRequest = {
            container.close()
            exitApplication()
        },
        state = windowState,
        icon = windowIcon,
        title = "PureNoteIt",
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                container.stateHolder.onEvent(DesktopUiEvent.RecordUserActivity)
                if (container.stateHolder.uiState.value.isVaultLocked) {
                    return@Window false
                }
                val cmdOrCtrl = event.isCtrlPressed || event.isMetaPressed
                when {
                    cmdOrCtrl && event.key == Key.K -> {
                        val visible = !container.stateHolder.uiState.value.showQuickSwitcher
                        container.stateHolder.onEvent(DesktopUiEvent.SetQuickSwitcherVisible(visible))
                        true
                    }
                    cmdOrCtrl && event.key == Key.N -> {
                        container.stateHolder.onEvent(DesktopUiEvent.CreateNewNote)
                        true
                    }
                    cmdOrCtrl && event.key == Key.S -> {
                        container.stateHolder.onEvent(DesktopUiEvent.SaveCurrentNote)
                        true
                    }
                    cmdOrCtrl && event.key == Key.L -> {
                        container.stateHolder.onEvent(DesktopUiEvent.LockVaultNow)
                        true
                    }
                    event.key == Key.Escape -> {
                        container.stateHolder.onEvent(DesktopUiEvent.SetQuickSwitcherVisible(false))
                        container.stateHolder.onEvent(DesktopUiEvent.SetSlashPaletteVisible(false))
                        false
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        window.minimumSize = Dimension(680, 520)
        LaunchedEffect(window) {
            val awtIcons = loadAwtWindowIcons()
            if (awtIcons.isNotEmpty()) {
                window.iconImages = awtIcons
            }
        }
        DesktopHomeScreenRoot(
            stateHolder = container.stateHolder,
            mediaPathResolver = container.mediaPathResolver
        )
    }
    }
}

private fun configureMacOsDesktopEnvironment() {
    val isMac = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)
    if (!isMac) return

    System.setProperty("apple.awt.application.name", "PureNoteIt")
    System.setProperty("apple.awt.application.appearance", "system")

    // When packaged as a native .app bundle, macOS loads Contents/Resources/PureNoteIt.icns natively
    // with full Retina multi-resolution support. For dev runs (./gradlew :desktopApp:run), set Dock icon via Taskbar.
    if (System.getProperty("jpackage.app-path").isNullOrBlank()) {
        runCatching {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                val dockImage = DesktopAppContainer::class.java
                    .getResourceAsStream("/purenoteit_icon.png")
                    ?.use(ImageIO::read)
                if (dockImage != null) {
                    Taskbar.getTaskbar().iconImage = dockImage
                }
            }
        }
    }
}

internal fun loadDesktopWindowIcon(): BitmapPainter? = runCatching {
    DesktopAppContainer::class.java.getResourceAsStream("/purenoteit_icon.png")?.use { stream ->
        BitmapPainter(SkiaImage.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
}.getOrNull()

internal fun loadAwtWindowIcons(): List<AwtImage> = runCatching {
    val src = DesktopAppContainer::class.java.getResourceAsStream("/purenoteit_icon.png")?.use { stream ->
        ImageIO.read(stream)
    } ?: return emptyList()
    listOf(16, 24, 32, 48, 64, 128).map { size ->
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(src, 0, 0, size, size, null)
        } finally {
            g.dispose()
        }
        scaled
    }
}.getOrDefault(emptyList())
