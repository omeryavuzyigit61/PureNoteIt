package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AppError
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.security.SyncedVaultSecurityDocument
import com.notes.purenoteit.security.VaultPinSyncProtocol

/**
 * Coordinates end-to-end encrypted (`ENC_P3_PWD`) bidirectional synchronization of the
 * Vault Note PIN verifier, wrapped note key, and trash retention policy via Drive `appDataFolder`.
 */
class VaultSettingsSyncCoordinator(
    private val remoteDataSource: RemoteSyncDataSource,
    private val pinProtocol: VaultPinSyncProtocol
) {
    suspend fun synchronize(
        localDocument: SyncedVaultSecurityDocument?,
        syncPassphrase: CharArray
    ): AppResult<SyncedVaultSecurityDocument?, AppError> {
        val remoteDoc: SyncedVaultSecurityDocument? = when (val readRes = remoteDataSource.readAppDataConfigFile(REMOTE_SETTINGS_FILE)) {
            is AppResult.Failure -> return readRes
            is AppResult.Success -> {
                val raw = readRes.data
                if (raw.isNullOrBlank()) {
                    null
                } else {
                    when (val dec = pinProtocol.decryptDocumentFromDrive(raw, syncPassphrase)) {
                        is AppResult.Failure -> return dec
                        is AppResult.Success -> dec.data
                    }
                }
            }
        }

        val merged = pinProtocol.mergeDocuments(localDocument, remoteDoc) ?: return AppResult.Success(null)

        val remoteNeedsUpdate = remoteDoc == null ||
            merged.pinUpdatedAt > remoteDoc.pinUpdatedAt ||
            merged.settingsUpdatedAt > remoteDoc.settingsUpdatedAt ||
            merged.hasPin != remoteDoc.hasPin ||
            merged.trashRetentionDays != remoteDoc.trashRetentionDays ||
            merged.pinHashB64 != remoteDoc.pinHashB64 ||
            merged.wrappedVaultKeyEnvelope != remoteDoc.wrappedVaultKeyEnvelope

        if (remoteNeedsUpdate) {
            val encrypted = when (val enc = pinProtocol.encryptDocumentForDrive(merged, syncPassphrase)) {
                is AppResult.Failure -> return enc
                is AppResult.Success -> enc.data
            }
            when (val up = remoteDataSource.upsertAppDataConfigFile(REMOTE_SETTINGS_FILE, encrypted)) {
                is AppResult.Failure -> return up
                is AppResult.Success -> Unit
            }
        }

        return AppResult.Success(merged)
    }

    companion object {
        const val REMOTE_SETTINGS_FILE = "purenote_vault_settings_v1.json"
    }
}
