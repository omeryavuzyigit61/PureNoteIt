package com.notes.purenoteit.di

import android.content.Context
import com.notes.purenoteit.BuildConfig
import com.notes.purenoteit.SharedCryptoFactory
import com.notes.purenoteit.SharedSyncFactory
import com.notes.purenoteit.crypto.VaultCrypto
import com.notes.purenoteit.data.security.AndroidOAuthPkceManager
import com.notes.purenoteit.data.security.AndroidVaultSecuritySyncBridge
import com.notes.purenoteit.data.security.VaultSecuritySyncBridge
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.RemoteSyncDataSource
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.SyncTokenProvider
import com.notes.purenoteit.sync.VaultSettingsSyncCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Provides `:shared` KMP crypto, OAuth PKCE, Google Drive transport, and [SyncEngine]
 * instances into `:app`'s Hilt graph while preserving `internal` encapsulation in `:shared`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideVaultCrypto(): VaultCrypto = SharedCryptoFactory.createVaultCrypto()

    @Provides
    @Singleton
    fun provideOAuthPkceProtocol(clock: AppClock): OAuthPkceProtocol =
        SharedSyncFactory.createOAuthPkceProtocol(clock = clock)

    @Provides
    @Singleton
    fun provideOAuthClientConfigProvider(): OAuthClientConfigProvider =
        OAuthClientConfigProvider {
            val clientId = BuildConfig.GOOGLE_ANDROID_CLIENT_ID.trim()
            if (clientId.isEmpty()) {
                null
            } else {
                OAuthPkceConfig(
                    clientId = clientId,
                    redirectUri = AndroidOAuthPkceManager.ANDROID_REDIRECT_URI
                )
            }
        }

    @Provides
    @Singleton
    fun provideSyncTokenProvider(
        tokenStorage: SecureTokenStorage,
        pkceProtocol: OAuthPkceProtocol,
        configProvider: OAuthClientConfigProvider,
        clock: AppClock
    ): SyncTokenProvider = SharedSyncFactory.createOAuthSyncTokenProvider(
        tokenStorage = tokenStorage,
        pkceProtocol = pkceProtocol,
        configProvider = configProvider,
        clock = clock
    )

    @Provides
    @Singleton
    fun provideRemoteSyncDataSource(
        tokenProvider: SyncTokenProvider,
        clock: AppClock
    ): RemoteSyncDataSource = SharedSyncFactory.createGoogleDriveDataSource(
        tokenProvider = tokenProvider,
        clock = clock
    )

    @Provides
    @Singleton
    fun provideSyncEngine(
        localStore: LocalSyncStore,
        remoteDataSource: RemoteSyncDataSource,
        clock: AppClock,
        vaultCrypto: VaultCrypto
    ): SyncEngine = SharedSyncFactory.createSyncEngine(
        localStore = localStore,
        remoteDataSource = remoteDataSource,
        clock = clock,
        vaultCrypto = vaultCrypto
    )

    @Provides
    @Singleton
    fun provideAutoSyncKeyCoordinator(
        remoteDataSource: RemoteSyncDataSource,
        tokenStorage: SecureTokenStorage,
        clock: AppClock
    ): AutoSyncKeyCoordinator = SharedSyncFactory.createAutoSyncKeyCoordinator(
        remoteDataSource = remoteDataSource,
        tokenStorage = tokenStorage,
        clock = clock
    )

    @Provides
    @Singleton
    fun provideVaultSettingsSyncCoordinator(
        remoteDataSource: RemoteSyncDataSource,
        vaultCrypto: VaultCrypto
    ): VaultSettingsSyncCoordinator = SharedSyncFactory.createVaultSettingsSyncCoordinator(
        remoteDataSource = remoteDataSource,
        vaultCrypto = vaultCrypto
    )

    @Provides
    @Singleton
    fun provideVaultSecuritySyncBridge(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): VaultSecuritySyncBridge = AndroidVaultSecuritySyncBridge(
        context = context,
        ioDispatcher = ioDispatcher
    )
}
