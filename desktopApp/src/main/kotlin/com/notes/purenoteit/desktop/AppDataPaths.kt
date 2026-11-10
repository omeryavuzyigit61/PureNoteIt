package com.notes.purenoteit.desktop

import java.io.File

data class DesktopAppPaths(
    val rootDir: File,
    val databaseFile: File,
    val mediaFilesDir: File,
    val cacheDir: File,
    val syncCursorFile: File,
    val vaultSecurityFile: File
) {
    fun ensureDirectories() {
        rootDir.mkdirs()
        mediaFilesDir.mkdirs()
        cacheDir.mkdirs()
    }

    companion object {
        fun forCurrentOs(appFolderName: String = "PureNoteIt"): DesktopAppPaths {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            val userHome = System.getProperty("user.home").orEmpty()
            val baseDir = when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    if (appData != null) File(appData) else File(userHome, "AppData/Roaming")
                }
                osName.contains("mac") -> File(userHome, "Library/Application Support")
                else -> {
                    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    if (xdg != null) File(xdg) else File(userHome, ".local/share")
                }
            }
            val root = File(baseDir, appFolderName)
            return DesktopAppPaths(
                rootDir = root,
                databaseFile = File(root, "purenoteit_desktop.db"),
                mediaFilesDir = File(root, "files"),
                cacheDir = File(root, "cache"),
                syncCursorFile = File(root, "sync_cursor.json"),
                vaultSecurityFile = File(root, "vault_security.json")
            ).apply { ensureDirectories() }
        }
    }
}
