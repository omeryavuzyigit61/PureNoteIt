package com.notes.purenoteit.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Data model for available update details and release highlights.
 */
data class AppUpdateData(
    val currentVersionName: String,
    val currentVersionCode: Long,
    val availableVersionCode: Long,
    val availableVersionName: String,
    val releaseNotesTr: List<String>,
    val releaseNotesEn: List<String>,
    val isFlexible: Boolean = true,
    val playUpdateInfo: AppUpdateInfo? = null
)

/**
 * Helper to manage Google Play In-App Updates and custom version checking.
 */
object AppUpdateHelper {
    private const val TAG = "AppUpdateHelper"
    private const val PREFS_NAME = "purenoteit_update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_update_check_epoch"
    private const val KEY_DISMISSED_VERSION = "dismissed_version_code"
    private const val MIN_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 saatte bir otomatik kontrol

    fun getAppUpdateManager(context: Context): AppUpdateManager {
        return AppUpdateManagerFactory.create(context)
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = getPackageInfo(context)
            pInfo.versionName ?: "1.0.5"
        } catch (e: Exception) {
            "1.0.5"
        }
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = getPackageInfo(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            8L
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(context: Context): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    /**
     * Checks Google Play Store for in-app updates.
     */
    fun checkForAppUpdate(
        activity: Activity,
        isManual: Boolean = false,
        onUpdateFound: (AppUpdateData) -> Unit,
        onUpToDate: (() -> Unit)? = null
    ) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

        // Otomatik kontrolde çok sık kontrol etmeyi engelle
        if (!isManual && (now - lastCheck < MIN_CHECK_INTERVAL_MS)) {
            Log.d(TAG, "Skipping auto update check (checked recently)")
            return
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        val currentVerName = getCurrentVersionName(activity)
        val currentVerCode = getCurrentVersionCode(activity)

        val appUpdateManager = getAppUpdateManager(activity)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val availability = appUpdateInfo.updateAvailability()
            val availableVerCode = appUpdateInfo.availableVersionCode().toLong()

            if (availability == UpdateAvailability.UPDATE_AVAILABLE) {
                Log.d(TAG, "Update available via Google Play! Code: $availableVerCode")
                val updateData = AppUpdateData(
                    currentVersionName = currentVerName,
                    currentVersionCode = currentVerCode,
                    availableVersionCode = availableVerCode,
                    availableVersionName = "v1.0.5",
                    releaseNotesTr = listOf(
                        "📐 Gelişmiş Matematik Formül Editörü: Kesirli ve köklü ifadeler, 160+ sembol.",
                        "🖼️ Görsel Boyutlandırma: Windows tarzı köşe tutamacı ve oran kilidi.",
                        "🕸️ 2D Bilgi Ağı: Bağlantı kopmaları giderildi, çift yönlü senkronizasyon.",
                        "⚡ Koyu Tema & Başlatma: Açılış ve geçişteki beyaz ekran parlaması tamamen giderildi."
                    ),
                    releaseNotesEn = listOf(
                        "📐 Advanced Math Formula Engine: Fractions, roots, 160+ symbols.",
                        "🖼️ Interactive Image Resizer: Corner resize handle & aspect ratio toggle.",
                        "🕸️ 2D Knowledge Graph: Fixed link persistence & bi-directional sync.",
                        "⚡ Dark Theme & Launch: Launch and transition white flash completely eliminated."
                    ),
                    playUpdateInfo = appUpdateInfo
                )
                onUpdateFound(updateData)
            } else {
                Log.d(TAG, "Google Play reports up to date: availability=$availability")
                if (isManual) {
                    onUpToDate?.invoke()
                }
            }
        }.addOnFailureListener { error ->
            Log.w(TAG, "Failed to query Google Play in-app update: ${error.message}")
            if (isManual) {
                onUpToDate?.invoke()
            }
        }
    }

    /**
     * Starts in-app update flow or opens Google Play store as fallback.
     */
    fun launchUpdate(
        activity: Activity,
        updateData: AppUpdateData,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    ) {
        val playInfo = updateData.playUpdateInfo
        if (playInfo != null && updateLauncher != null) {
            try {
                val appUpdateManager = getAppUpdateManager(activity)
                val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                appUpdateManager.startUpdateFlowForResult(playInfo, updateLauncher, options)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed startUpdateFlowForResult: ${e.message}")
            }
        }

        // Direct fallback: Open Google Play Store
        openPlayStore(activity)
    }

    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * Provides release notes and feature highlights for the current or new version.
     */
    fun getReleaseHighlights(context: Context): AppUpdateData {
        val curName = getCurrentVersionName(context)
        val curCode = getCurrentVersionCode(context)
        return AppUpdateData(
            currentVersionName = curName,
            currentVersionCode = curCode,
            availableVersionCode = curCode,
            availableVersionName = "v$curName",
            releaseNotesTr = listOf(
                "📐 Gelişmiş Matematik Editörü: Kesirli, köklü ifadeler ve 160+ matematik sembolü.",
                "🌓 3 Seçenekli Tema: Sistem Varsayılanı, Açık ve Koyu tema desteği.",
                "🖼️ Görsel Boyutlandırma: Windows tarzı köşe tutamacı ve en-boy oran kilidi.",
                "🕸️ 2D Bilgi Ağı: Sayfa bağlantıları ve çift yönlü senkronizasyon.",
                "⚡ Açılış & Geçiş: Açılış ekranı renk geçişi ve kontrast iyileştirmeleri."
            ),
            releaseNotesEn = listOf(
                "📐 Advanced Math Editor: Fractions, roots, and 160+ formula symbols.",
                "🌓 3-Mode Theme: System Default, Light, and Dark mode support.",
                "🖼️ Image Resizer: Windows-style corner handle and aspect ratio toggle.",
                "🕸️ 2D Knowledge Graph: Note linking and bi-directional sync.",
                "⚡ Launch & Transition: Splash screen smooth transition and contrast optimizations."
            )
        )
    }
}
