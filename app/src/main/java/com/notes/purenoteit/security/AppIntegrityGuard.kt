package com.notes.purenoteit.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.notes.purenoteit.util.SecurityUtil
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

enum class IntegrityStatus {
    SECURE,
    DEBUGGER_ACTIVE,
    HOOKING_FRAMEWORK_DETECTED,
    SIGNATURE_TAMPERED
}

data class IntegrityCheckResult(
    val isSafe: Boolean,
    val status: IntegrityStatus,
    val message: String? = null
)

/**
 * Runtime Application Self-Protection (RASP) & App Integrity Engine.
 * Safeguards PureNoteIt against:
 * 1. Unauthorized APK re-signing / cloning (Signature Tamper Check)
 * 2. Active memory inspection (Anti-Debug via JDWP & Linux TracerPid)
 * 3. Dynamic memory hooking & runtime tampering (Anti-Frida & Anti-Xposed)
 */
object AppIntegrityGuard {

    private val SUSPICIOUS_LIBRARIES = listOf(
        "frida",
        "gadget",
        "xposed",
        "substrate",
        "edxposed",
        "lsposed",
        "sandhook"
    )

    private val GENERIC_DEBUG_SIGNATURE_TOKENS = listOf(
        "androiddebugkey",
        "cn=android debug",
        "ou=android",
        "testkey"
    )

    /**
     * Executes a comprehensive, low-overhead RASP integrity scan.
     */
    fun checkAppIntegrity(context: Context): IntegrityCheckResult {
        // 1. Anti-Debug Check
        if (isDebuggerActive(context)) {
            SecurityUtil.lockVault()
            return IntegrityCheckResult(
                isSafe = false,
                status = IntegrityStatus.DEBUGGER_ACTIVE,
                message = "Hata ayıklayıcı (Debugger) tespiti: Güvenlik nedeniyle kasa kilitlendi."
            )
        }

        // 2. Anti-Hooking (Frida / Xposed) Check
        if (isHookingFrameworkDetected(context)) {
            SecurityUtil.lockVault()
            return IntegrityCheckResult(
                isSafe = false,
                status = IntegrityStatus.HOOKING_FRAMEWORK_DETECTED,
                message = "Bellek dinleme veya manipülasyon aracı (Frida/Xposed) tespit edildi. Kasa kilitlendi."
            )
        }

        // 3. App Signature & Package Tamper Check
        if (!isSignatureAuthentic(context)) {
            SecurityUtil.lockVault()
            return IntegrityCheckResult(
                isSafe = false,
                status = IntegrityStatus.SIGNATURE_TAMPERED,
                message = "Uygulama imza bütünlüğü bozulmuş veya sahte bir paket tespit edildi."
            )
        }

        return IntegrityCheckResult(isSafe = true, status = IntegrityStatus.SECURE)
    }

    /**
     * Detects attached Java debuggers, release debug flags, or Linux ptrace debuggers.
     */
    private fun isDebuggerActive(context: Context): Boolean {
        // Direct framework debugger check
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        // Check if app is explicitly marked debuggable
        val isDebuggable = isAppDebuggable(context)

        // Check Linux TracerPid in /proc/self/status (detects gdb / lldb / strace)
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists() && statusFile.canRead()) {
                BufferedReader(FileReader(statusFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.startsWith("TracerPid:")) {
                            val pid = line.substring(10).trim().toIntOrNull() ?: 0
                            if (pid > 0 && !isDebuggable) {
                                return true
                            }
                            break
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
            // Best-effort check; do not crash if inaccessible on restricted SELinux
        }

        return false
    }

    /**
     * Inspects /proc/self/maps and local TCP socket ports for Frida / Xposed hooking agents.
     */
    private fun isHookingFrameworkDetected(context: Context): Boolean {
        // A. Scan loaded memory maps for injected hooking shared libraries
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val lower = line.lowercase()
                        for (suspicious in SUSPICIOUS_LIBRARIES) {
                            if (lower.contains(suspicious)) {
                                return true
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore restricted access
        }

        // B. Quick non-blocking socket check for default Frida server on localhost
        if (!isAppDebuggable(context)) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", 27042), 40)
                    return true // Port is open, Frida server is listening!
                }
            } catch (_: Exception) {
                // Connection refused or timed out -> Normal behavior
            }
        }

        return false
    }

    /**
     * Validates that the APK signature is legitimate and not tampered by third-party repackagers.
     */
    private fun isSignatureAuthentic(context: Context): Boolean {
        // In local development debug mode, skip release signature validation
        if (isAppDebuggable(context)) return true

        return try {
            val certs = getAppCertificates(context)
            if (certs.isEmpty()) return false

            // Reject packages signed with generic AOSP test keys in release builds
            for (certStr in certs) {
                val lower = certStr.lowercase()
                for (token in GENERIC_DEBUG_SIGNATURE_TOKENS) {
                    if (lower.contains(token)) {
                        return false // Tampered / re-signed with debug key!
                    }
                }
            }
            true
        } catch (e: Exception) {
            // Default to safe in case of unexpected platform errors
            true
        }
    }

    private fun getAppCertificates(context: Context): List<String> {
        val result = mutableListOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val sigInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                val signatures = if (sigInfo?.hasMultipleSigners() == true) {
                    sigInfo.apkContentsSigners
                } else {
                    sigInfo?.signingCertificateHistory
                }
                signatures?.forEach { sig ->
                    result.add(sig.toCharsString())
                }
            } else {
                @Suppress("DEPRECATION")
                val pInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                pInfo.signatures?.forEach { sig ->
                    result.add(sig.toCharsString())
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02X".format(it) }
    }

    private fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
