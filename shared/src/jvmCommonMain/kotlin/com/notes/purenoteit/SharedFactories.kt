package com.notes.purenoteit

import com.notes.purenoteit.crypto.JvmVaultCryptoImpl
import com.notes.purenoteit.crypto.VaultCrypto
import com.notes.purenoteit.data.storage.EncryptedMediaPathStorage
import com.notes.purenoteit.data.storage.LocalMediaPathStorage
import com.notes.purenoteit.domain.storage.EncryptedMediaStore
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.OAuthSyncTokenProvider
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.security.VaultPinSyncProtocol
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.GoogleDriveSyncDataSourceImpl
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.RemoteSyncDataSource
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.SyncTokenProvider
import com.notes.purenoteit.sync.VaultSettingsSyncCoordinator
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Public factories exposing `:shared` capabilities via clean interfaces while keeping
 * concrete storage, crypto, and Drive transport implementations strictly `internal`.
 */
object SharedStorageFactory {
    fun createMediaPathResolver(
        rootFilesDir: File,
        cacheDir: File? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MediaPathResolver = LocalMediaPathStorage(
        rootFilesDir = rootFilesDir,
        cacheDir = cacheDir,
        ioDispatcher = ioDispatcher
    )

    /**
     * Media storage whose files under `media/` are AES-256-GCM encrypted at rest. Decrypted copies for UI/players
     * are created on demand inside [cacheDir]; [keyProvider] must return the 32-byte media key.
     */
    fun createEncryptedMediaStore(
        rootFilesDir: File,
        cacheDir: File,
        keyProvider: () -> ByteArray?,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): EncryptedMediaStore = EncryptedMediaPathStorage(
        delegate = LocalMediaPathStorage(
            rootFilesDir = rootFilesDir,
            cacheDir = cacheDir,
            ioDispatcher = ioDispatcher
        ),
        rootFilesDir = rootFilesDir,
        cacheDir = cacheDir,
        keyProvider = keyProvider,
        ioDispatcher = ioDispatcher
    )
}

object SharedCryptoFactory {
    fun createVaultCrypto(): VaultCrypto = JvmVaultCryptoImpl()
}

object SharedSyncFactory {
    fun createGoogleDriveDataSource(
        tokenProvider: SyncTokenProvider,
        clock: AppClock,
        httpEngine: HttpClientEngine = CIO.create(),
        delayFn: suspend (Long) -> Unit = { ms -> kotlinx.coroutines.delay(ms) }
    ): RemoteSyncDataSource = GoogleDriveSyncDataSourceImpl(
        httpEngine = httpEngine,
        tokenProvider = tokenProvider,
        clock = clock,
        delayFn = delayFn
    )

    fun createOAuthPkceProtocol(
        clock: AppClock,
        httpEngine: HttpClientEngine = CIO.create(),
        requireHttpsEndpoints: Boolean = true
    ): OAuthPkceProtocol = OAuthPkceProtocol(
        httpEngine = httpEngine,
        clock = clock,
        requireHttpsEndpoints = requireHttpsEndpoints
    )

    fun createOAuthSyncTokenProvider(
        tokenStorage: SecureTokenStorage,
        pkceProtocol: OAuthPkceProtocol,
        configProvider: OAuthClientConfigProvider,
        clock: AppClock
    ): SyncTokenProvider = OAuthSyncTokenProvider(
        tokenStorage = tokenStorage,
        pkceProtocol = pkceProtocol,
        configProvider = configProvider,
        clock = clock
    )

    fun createSyncEngine(
        localStore: LocalSyncStore,
        remoteDataSource: RemoteSyncDataSource,
        clock: AppClock,
        vaultCrypto: VaultCrypto = SharedCryptoFactory.createVaultCrypto()
    ): SyncEngine = SyncEngine(
        localStore = localStore,
        remoteDataSource = remoteDataSource,
        vaultCrypto = vaultCrypto,
        conflictResolver = ConflictResolver(clock = clock),
        clock = clock
    )

    fun createAutoSyncKeyCoordinator(
        remoteDataSource: RemoteSyncDataSource,
        tokenStorage: SecureTokenStorage,
        clock: AppClock
    ): AutoSyncKeyCoordinator = AutoSyncKeyCoordinator(
        remoteDataSource = remoteDataSource,
        tokenStorage = tokenStorage,
        clock = clock
    )

    fun createVaultPinSyncProtocol(
        vaultCrypto: VaultCrypto = SharedCryptoFactory.createVaultCrypto()
    ): VaultPinSyncProtocol = VaultPinSyncProtocol(vaultCrypto = vaultCrypto)

    fun createVaultSettingsSyncCoordinator(
        remoteDataSource: RemoteSyncDataSource,
        vaultCrypto: VaultCrypto = SharedCryptoFactory.createVaultCrypto()
    ): VaultSettingsSyncCoordinator = VaultSettingsSyncCoordinator(
        remoteDataSource = remoteDataSource,
        pinProtocol = VaultPinSyncProtocol(vaultCrypto = vaultCrypto)
    )
}
