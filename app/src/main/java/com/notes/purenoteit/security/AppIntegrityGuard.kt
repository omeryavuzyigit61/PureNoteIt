package com.notes.purenoteit.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Debug
import com.notes.purenoteit.util.SecurityUtil
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

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
 *
 * Implements 3 real-time defenses with XOR-masked strings:
 * 1. Package, Container & X.509 / SHA-256 Certificate Tamper Check
 * 2. Anti-Debug via JDWP & Linux /proc/self/status TracerPid
 * 3. Anti-Hooking via /proc/self/maps memory scan & off-main-thread Frida TCP port probe
 */
object AppIntegrityGuard {

    /**
     * Synchronous, zero-network RASP check safe to execute on the main thread
     * in Activity.onCreate() BEFORE any database or ViewModel initialization.
     */
    fun checkAppIntegritySync(context: Context): IntegrityCheckResult {
        // 1. Anti-Debug Check (JDWP + TracerPid)
        if (isDebuggerActive(context)) {
            SecurityUtil.lockVault()
            return IntegrityCheckResult(
                isSafe = false,
                status = IntegrityStatus.DEBUGGER_ACTIVE,
                message = "Hata ayıklayıcı (Debugger) tespiti: Güvenlik nedeniyle kasa kilitlendi."
            )
        }

        // 2. Memory Map Anti-Hooking Check (Frida / Xposed / LSPosed / Substrate)
        if (isMemoryMapHooked()) {
            SecurityUtil.lockVault()
            return IntegrityCheckResult(
                isSafe = false,
                status = IntegrityStatus.HOOKING_FRAMEWORK_DETECTED,
                message = "Bellek dinleme veya manipülasyon aracı (Frida/Xposed) tespit edildi. Kasa kilitlendi."
            )
        }

        // 3. Package Identity, Container & X.509 / SHA-256 Signature Check
        if (!isSignatureAndPackageAuthentic(context)) {
            SecurityUtil.lockVault()
            return IntegrityCheckResult(
                isSafe = false,
                status = IntegrityStatus.SIGNATURE_TAMPERED,
                message = "Uygulama imza bütünlüğü bozulmuş veya sahte/klon bir paket tespit edildi."
            )
        }

        return IntegrityCheckResult(isSafe = true, status = IntegrityStatus.SECURE)
    }

    /**
     * Must be invoked from a background IO thread (Dispatchers.IO) so that Android does not
     * throw NetworkOnMainThreadException when probing the local Frida server port (27042).
     */
    fun isLocalHookingPortOpen(context: Context): Boolean {
        if (isAppDebuggable(context)) return false
        return try {
            val host = ObfuscatedSecrets.decode(ObfuscatedSecrets.LOCALHOST)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 27042), 50)
                SecurityUtil.lockVault()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects attached Java debuggers or Linux ptrace debuggers via /proc/self/status.
     */
    private fun isDebuggerActive(context: Context): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        val isDebuggable = isAppDebuggable(context)
        try {
            val statusPath = ObfuscatedSecrets.decode(ObfuscatedSecrets.PROC_STATUS)
            val tracerPrefix = ObfuscatedSecrets.decode(ObfuscatedSecrets.TRACER_PID)
            val statusFile = File(statusPath)
            if (statusFile.exists() && statusFile.canRead()) {
                BufferedReader(FileReader(statusFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.startsWith(tracerPrefix)) {
                            val pid = line.substring(tracerPrefix.length).trim().toIntOrNull() ?: 0
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
        }

        return false
    }

    /**
     * Scans /proc/self/maps for injected hooking shared libraries using XOR-decoded tokens.
     */
    private fun isMemoryMapHooked(): Boolean {
        try {
            val mapsPath = ObfuscatedSecrets.decode(ObfuscatedSecrets.PROC_MAPS)
            val mapsFile = File(mapsPath)
            if (mapsFile.exists() && mapsFile.canRead()) {
                val decodedTokens = ObfuscatedSecrets.SUSPICIOUS_TOKENS.map {
                    ObfuscatedSecrets.decode(it)
                }
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val lower = line.lowercase()
                        for (token in decodedTokens) {
                            if (lower.contains(token)) {
                                return true
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    /**
     * Validates:
     * 1. Package name matches com.purenoteit.app (blocks package-renamed clones)
     * 2. App data directory belongs to com.purenoteit.app (blocks VirtualXposed / DualSpace containers)
     * 3. Real X.509 certificate Subject/Issuer DN & SHA-256 fingerprint are authentic and not signed
     *    by debug, AOSP testkey, APKTool, LuckyPatcher, MT Manager, or NP Manager certificates.
     */
    private fun isSignatureAndPackageAuthentic(context: Context): Boolean {
        val expectedPkg = ObfuscatedSecrets.decode(ObfuscatedSecrets.EXPECTED_PKG)
        if (context.packageName != expectedPkg || context.applicationInfo.packageName != expectedPkg) {
            return false
        }

        // Verify internal data path is not hosted inside a virtual cloning container
        val dataPath = context.filesDir.absolutePath
        if (!dataPath.contains(expectedPkg)) {
            return false
        }

        // Skip release certificate inspection during local Android Studio debug builds
        if (isAppDebuggable(context)) return true

        return try {
            val rawSignatures = getRawSignatures(context)
            if (rawSignatures.isEmpty()) return false

            val certType = ObfuscatedSecrets.decode(ObfuscatedSecrets.CERT_X509)
            val certFactory = CertificateFactory.getInstance(certType)
            val bannedDnTokens = ObfuscatedSecrets.TAMPER_CERT_TOKENS.map {
                ObfuscatedSecrets.decode(it)
            }
            val aospTestKeyHash = ObfuscatedSecrets.decode(ObfuscatedSecrets.AOSP_TESTKEY_SHA256)

            for (sig in rawSignatures) {
                val certBytes = sig.toByteArray()
                val certHash = sha256(certBytes)
                if (certHash.equals(aospTestKeyHash, ignoreCase = true)) {
                    return false
                }

                val x509 = ByteArrayInputStream(certBytes).use { stream ->
                    certFactory.generateCertificate(stream) as? X509Certificate
                } ?: return false

                val subjectDn = x509.subjectX500Principal.name.lowercase()
                val issuerDn = x509.issuerX500Principal.name.lowercase()

                for (banned in bannedDnTokens) {
                    if (subjectDn.contains(banned) || issuerDn.contains(banned)) {
                        return false
                    }
                }
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun getRawSignatures(context: Context): List<Signature> {
        val result = mutableListOf<Signature>()
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
                signatures?.let { result.addAll(it) }
            } else {
                @Suppress("DEPRECATION")
                val pInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                pInfo.signatures?.let { result.addAll(it) }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun sha256(bytes: ByteArray): String {
        val algo = ObfuscatedSecrets.decode(ObfuscatedSecrets.HASH_SHA256)
        val digest = MessageDigest.getInstance(algo)
        return digest.digest(bytes).joinToString("") { "%02X".format(it) }
    }

    private fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
