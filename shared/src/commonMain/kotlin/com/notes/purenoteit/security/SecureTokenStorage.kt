package com.notes.purenoteit.security

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthTokenBundle(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at_ms") val expiresAtEpochMillis: Long,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("scope") val scope: String = ""
)

/**
 * Hardware/OS-backed encrypted storage contract for OAuth tokens and the cross-device sync passphrase.
 * Android implementation uses Keystore + Google Tink AEAD + Jetpack DataStore (`noBackupFilesDir`);
 * Desktop implementation uses OS Keychain (`java-keyring`: Windows DPAPI Credential Manager / macOS Keychain).
 */
interface SecureTokenStorage {
    suspend fun saveTokenBundle(bundle: OAuthTokenBundle): AppResult<Unit, DataError.Security>

    suspend fun getTokenBundle(): AppResult<OAuthTokenBundle?, DataError.Security>

    suspend fun saveSyncPassphrase(passphrase: CharArray): AppResult<Unit, DataError.Security>

    suspend fun getSyncPassphrase(): AppResult<CharArray?, DataError.Security>

    suspend fun clearAll(): AppResult<Unit, DataError.Security>
}
