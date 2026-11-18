package com.notes.purenoteit.data.security

import android.content.Context
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.security.SyncedVaultSecurityDocument
import com.notes.purenoteit.util.Constants
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.SharedPrefencesUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface VaultSecuritySyncBridge {
    suspend fun loadLocalSecurityDoc(): SyncedVaultSecurityDocument?
    suspend fun applySyncedSecurityDoc(doc: SyncedVaultSecurityDocument)
}

object NoOpVaultSecuritySyncBridge : VaultSecuritySyncBridge {
    override suspend fun loadLocalSecurityDoc(): SyncedVaultSecurityDocument? = null
    override suspend fun applySyncedSecurityDoc(doc: SyncedVaultSecurityDocument) = Unit
}

@Singleton
internal class AndroidVaultSecuritySyncBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : VaultSecuritySyncBridge {

    override suspend fun loadLocalSecurityDoc(): SyncedVaultSecurityDocument? = withContext(ioDispatcher) {
        val prefs = SharedPrefencesUtil(context)
        val retentionDays = prefs.get(Constants.KEY_TRASH_RETENTION_DAYS, Constants.DEFAULT_TRASH_RETENTION_DAYS)
        val settingsUpdatedAt = context.getSharedPreferences(Constants.SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
            .getLong(Constants.KEY_VAULT_SETTINGS_UPDATED_AT, 0L)
        val pinDoc = SecurityUtil.exportSyncedPinDocument(context)
        if (pinDoc != null) {
            pinDoc.copy(
                trashRetentionDays = retentionDays,
                settingsUpdatedAt = settingsUpdatedAt
            )
        } else if (settingsUpdatedAt > 0L || retentionDays != Constants.DEFAULT_TRASH_RETENTION_DAYS) {
            SyncedVaultSecurityDocument(
                trashRetentionDays = retentionDays,
                settingsUpdatedAt = settingsUpdatedAt
            )
        } else {
            null
        }
    }

    override suspend fun applySyncedSecurityDoc(doc: SyncedVaultSecurityDocument) = withContext(ioDispatcher) {
        SecurityUtil.applySyncedPinDocument(context, doc)
        val sp = context.getSharedPreferences(Constants.SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        val localSettingsUpdatedAt = sp.getLong(Constants.KEY_VAULT_SETTINGS_UPDATED_AT, 0L)
        if (doc.settingsUpdatedAt > localSettingsUpdatedAt) {
            sp.edit()
                .putInt(Constants.KEY_TRASH_RETENTION_DAYS, doc.trashRetentionDays.coerceIn(1, 365))
                .putLong(Constants.KEY_VAULT_SETTINGS_UPDATED_AT, doc.settingsUpdatedAt)
                .apply()
        }
    }
}
