package com.notes.purenoteit.desktop

import com.notes.purenoteit.SharedCryptoFactory
import com.notes.purenoteit.SharedDesktopAuthFactory
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.SharedSyncFactory
import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepository
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepositoryImpl
import com.notes.purenoteit.desktop.data.runner.DesktopCodeRunnerImpl
import com.notes.purenoteit.desktop.data.security.DesktopVaultSecurityManager
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.desktop.data.sync.DesktopLocalSyncStoreImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncServer
import com.notes.purenoteit.desktop.ui.viewmodel.DesktopHomeStateHolder
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.OAuthClientConfigProvider
import kotlinx.coroutines.Dispatchers

/**
 * Pure Constructor-Injection composition root for the Desktop target.
 * Wires `:shared` KMP engines with SQLCipher v4 (`sqlite-jdbc-crypt`), AES-256-GCM encrypted
 * local media, OS Keychain (`java-keyring`), LAN Ktor WebSocket P2P transport, and sandboxed code runner.
 */
internal class DesktopAppContainer(
    paths: DesktopAppPaths = DesktopAppPaths.forCurrentOs(),
    clock: AppClock = AppClock { System.currentTimeMillis() }
) : AutoCloseable {

    private val keyringVault = SharedDesktopAuthFactory.createOsKeyringVault(Dispatchers.IO)

    lateinit var vaultSecurityManager: DesktopVaultSecurityManager
        private set

    private val databaseManager = DesktopSqliteDatabaseManager(
        dbFile = paths.databaseFile,
        clock = clock,
        ioDispatcher = Dispatchers.IO,
        dbKeyProvider = {
            if (::vaultSecurityManager.isInitialized) vaultSecurityManager.currentDbKeyCopy() else null
        }
    )

    private val rawMediaStorage = SharedStorageFactory.createMediaPathResolver(
        rootFilesDir = paths.mediaFilesDir,
        cacheDir = paths.cacheDir,
        ioDispatcher = Dispatchers.IO
    )

    private val encryptedMediaStorage = DesktopEncryptedMediaStorage(
        delegate = rawMediaStorage,
        mediaKeyProvider = {
            if (::vaultSecurityManager.isInitialized) vaultSecurityManager.currentMediaKeyCopy() else null
        },
        rootFilesDir = paths.mediaFilesDir,
        ioDispatcher = Dispatchers.IO
    )

    init {
        vaultSecurityManager = DesktopVaultSecurityManager(
            securityMetadataFile = paths.vaultSecurityFile,
            keyringVault = keyringVault,
            dbManagerProvider = { databaseManager },
            encryptedMediaStorageProvider = { encryptedMediaStorage },
            ioDispatcher = Dispatchers.IO
        )
    }

    val mediaPathResolver: MediaPathResolver = encryptedMediaStorage

    private val noteRepository: DesktopNoteRepository = DesktopNoteRepositoryImpl(
        db = databaseManager,
        clock = clock
    )

    private val localSyncStore = DesktopLocalSyncStoreImpl(
        db = databaseManager,
        mediaPathResolver = mediaPathResolver,
        syncCursorFile = paths.syncCursorFile,
        ioDispatcher = Dispatchers.IO
    )

    private val vaultCrypto = SharedCryptoFactory.createVaultCrypto()
    private val tokenStorage = SharedDesktopAuthFactory.createSecureTokenStorage(Dispatchers.IO)
    private val pkceProtocol = SharedSyncFactory.createOAuthPkceProtocol(clock = clock)
    private val pkceManager = SharedDesktopAuthFactory.createLoopbackOAuthManager(
        pkceProtocol = pkceProtocol,
        tokenStorage = tokenStorage,
        ioDispatcher = Dispatchers.IO
    )

    private val clientConfigProvider = OAuthClientConfigProvider {
        BuildDesktopOAuthConfig.createPkceConfig()
    }

    private val syncTokenProvider = SharedSyncFactory.createOAuthSyncTokenProvider(
        tokenStorage = tokenStorage,
        pkceProtocol = pkceProtocol,
        configProvider = clientConfigProvider,
        clock = clock
    )

    private val driveDataSource = SharedSyncFactory.createGoogleDriveDataSource(
        tokenProvider = syncTokenProvider,
        clock = clock
    )

    private val driveSyncEngine = SharedSyncFactory.createSyncEngine(
        localStore = localSyncStore,
        remoteDataSource = driveDataSource,
        clock = clock,
        vaultCrypto = vaultCrypto
    )

    private val autoSyncKeyCoordinator = SharedSyncFactory.createAutoSyncKeyCoordinator(
        remoteDataSource = driveDataSource,
        tokenStorage = tokenStorage,
        clock = clock
    )

    private val codeExecutor = DesktopCodeRunnerImpl(
        ioDispatcher = Dispatchers.IO,
        clock = clock
    )

    private val lanSyncServer = LanWebSocketSyncServer()

    val stateHolder = DesktopHomeStateHolder(
        noteRepository = noteRepository,
        mediaPathResolver = mediaPathResolver,
        codeExecutor = codeExecutor,
        tokenStorage = tokenStorage,
        pkceManager = pkceManager,
        driveSyncEngine = driveSyncEngine,
        autoSyncKeyCoordinator = autoSyncKeyCoordinator,
        localSyncStore = localSyncStore,
        vaultCrypto = vaultCrypto,
        lanSyncServer = lanSyncServer,
        oauthClientConfigProvider = clientConfigProvider,
        vaultSecurityManager = vaultSecurityManager,
        clock = clock,
        mainDispatcher = Dispatchers.Main
    )

    override fun close() {
        stateHolder.close()
        lanSyncServer.close()
        databaseManager.close()
    }
}
