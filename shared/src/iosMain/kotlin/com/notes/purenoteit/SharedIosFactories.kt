package com.notes.purenoteit

import com.notes.purenoteit.crypto.IosNativeCryptoBridge
import com.notes.purenoteit.crypto.IosVaultCryptoImpl
import com.notes.purenoteit.crypto.VaultCrypto
import com.notes.purenoteit.data.storage.IosMediaPathStorage
import com.notes.purenoteit.domain.model.AppError
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.domain.usecase.StaticSyntaxChecker
import com.notes.purenoteit.domain.usecase.StaticSyntaxReport
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.OAuthSyncTokenProvider
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.GoogleDriveSyncDataSourceImpl
import com.notes.purenoteit.sync.LocalSyncSnapshot
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.RemoteSyncDataSource
import com.notes.purenoteit.sync.ResolvedRemoteBatch
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.SyncTokenProvider
import com.notes.purenoteit.sync.currentPlatformDisplayName
import com.notes.purenoteit.sync.model.SyncCursorState
import com.notes.purenoteit.sync.sharedRandomUuid
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

data class IosPkceSessionResult(
    val success: Boolean,
    val state: String = "",
    val codeVerifier: String = "",
    val redirectUri: String = "",
    val authorizationUrl: String = "",
    val errorMessage: String? = null
)

data class IosOpResult(
    val success: Boolean,
    val errorMessage: String? = null
)

data class IosDriveSyncResult(
    val success: Boolean,
    val pulledCategories: Int = 0,
    val pulledNotes: Int = 0,
    val pulledLinks: Int = 0,
    val pulledStreamItems: Int = 0,
    val pulledRules: Int = 0,
    val pushedEntities: Int = 0,
    val conflictCopiesCreated: Int = 0,
    val mediaUploaded: Int = 0,
    val mediaDownloaded: Int = 0,
    val syncedAt: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Clean Swift-friendly entrypoints for the iOS host (`PureNoteShared.framework`).
 */
object SharedIosCryptoFactory {
    fun createVaultCrypto(bridge: IosNativeCryptoBridge): VaultCrypto = IosVaultCryptoImpl(bridge)

    fun encryptText(crypto: VaultCrypto, plainText: String, passphrase: String): String? {
        val chars = passphrase.toCharArray()
        return try {
            when (val res = crypto.encryptWithPassphrase(plainText, chars)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    fun decryptText(crypto: VaultCrypto, encryptedText: String, passphrase: String): String? {
        val chars = passphrase.toCharArray()
        return try {
            when (val res = crypto.decryptEnvelope(encryptedText, passphrase = chars, legacyFallbackSecret = null)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    fun encryptMedia(crypto: VaultCrypto, plainBytes: ByteArray, passphrase: String): ByteArray? {
        val chars = passphrase.toCharArray()
        return try {
            when (val res = crypto.encryptMediaBytes(plainBytes, chars)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    fun decryptMedia(crypto: VaultCrypto, encryptedBytes: ByteArray, passphrase: String): ByteArray? {
        val chars = passphrase.toCharArray()
        return try {
            when (val res = crypto.decryptMediaBytesStrict(encryptedBytes, chars)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
        } finally {
            chars.fill('\u0000')
        }
    }
}

object SharedIosStorageFactory {
    fun createMediaPathResolver(
        rootFilesPath: String,
        cachePath: String? = null
    ): MediaPathResolver = IosMediaPathStorage(
        rootFilesPath = rootFilesPath,
        cachePath = cachePath
    )
}

object SharedIosSyncFactory {
    fun createGoogleDriveDataSource(
        tokenProvider: SyncTokenProvider,
        clock: AppClock,
        httpEngine: HttpClientEngine = Darwin.create()
    ): RemoteSyncDataSource = GoogleDriveSyncDataSourceImpl(
        httpEngine = httpEngine,
        tokenProvider = tokenProvider,
        clock = clock
    )

    fun createOAuthPkceProtocol(
        clock: AppClock,
        httpEngine: HttpClientEngine = Darwin.create(),
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
        vaultCrypto: VaultCrypto
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
}

object SharedIosSyncBridge {
    fun createPkceSession(clientId: String, redirectUri: String): IosPkceSessionResult {
        val cleanId = clientId.trim()
        val cleanRedirect = redirectUri.trim()
        if (cleanId.isEmpty() || cleanRedirect.isEmpty()) {
            return IosPkceSessionResult(
                success = false,
                errorMessage = "Google iOS OAuth Client ID yapılandırılmamış."
            )
        }
        val config = OAuthPkceConfig(clientId = cleanId, redirectUri = cleanRedirect)
        val protocol = SharedIosSyncFactory.createOAuthPkceProtocol(clock = AppClock { 0L })
        return when (val res = protocol.createPkceSession(config, redirectUriOverride = cleanRedirect)) {
            is AppResult.Success -> IosPkceSessionResult(
                success = true,
                state = res.data.state,
                codeVerifier = res.data.codeVerifier,
                redirectUri = res.data.redirectUri,
                authorizationUrl = res.data.authorizationUrl
            )
            is AppResult.Failure -> IosPkceSessionResult(
                success = false,
                errorMessage = res.error.toUserMessage()
            )
        }
    }

    suspend fun completePkceCallback(
        clientId: String,
        callbackUrl: String,
        expectedState: String,
        expectedPath: String,
        codeVerifier: String,
        redirectUri: String,
        tokenStorage: SecureTokenStorage,
        clock: AppClock
    ): IosOpResult {
        val config = OAuthPkceConfig(clientId = clientId.trim(), redirectUri = redirectUri.trim())
        val protocol = SharedIosSyncFactory.createOAuthPkceProtocol(clock = clock)
        val codeRes = protocol.parseAndValidateCallback(
            callbackUri = callbackUrl,
            expectedState = expectedState,
            expectedPath = expectedPath
        )
        val code = when (codeRes) {
            is AppResult.Failure -> return IosOpResult(false, codeRes.error.toUserMessage())
            is AppResult.Success -> codeRes.data
        }
        val tokenRes = protocol.exchangeAuthorizationCode(
            config = config,
            code = code,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri
        )
        val bundle = when (tokenRes) {
            is AppResult.Failure -> return IosOpResult(false, tokenRes.error.toUserMessage())
            is AppResult.Success -> tokenRes.data
        }
        return when (val saveRes = tokenStorage.saveTokenBundle(bundle)) {
            is AppResult.Failure -> IosOpResult(false, saveRes.error.toUserMessage())
            is AppResult.Success -> IosOpResult(true)
        }
    }

    suspend fun runGoogleDriveSync(
        localStore: LocalSyncStore,
        tokenStorage: SecureTokenStorage,
        vaultCrypto: VaultCrypto,
        clientId: String,
        redirectUri: String,
        clock: AppClock,
        customPassphrase: String? = null
    ): IosDriveSyncResult {
        val cleanId = clientId.trim()
        val cleanRedirect = redirectUri.trim()
        if (cleanId.isEmpty()) {
            return IosDriveSyncResult(false, errorMessage = "Google iOS OAuth Client ID eksik.")
        }
        val configProvider = OAuthClientConfigProvider {
            OAuthPkceConfig(clientId = cleanId, redirectUri = cleanRedirect)
        }
        val pkceProtocol = SharedIosSyncFactory.createOAuthPkceProtocol(clock = clock)
        val tokenProvider = SharedIosSyncFactory.createOAuthSyncTokenProvider(
            tokenStorage = tokenStorage,
            pkceProtocol = pkceProtocol,
            configProvider = configProvider,
            clock = clock
        )
        val driveDataSource = SharedIosSyncFactory.createGoogleDriveDataSource(
            tokenProvider = tokenProvider,
            clock = clock
        )
        val syncEngine = SharedIosSyncFactory.createSyncEngine(
            localStore = localStore,
            remoteDataSource = driveDataSource,
            clock = clock,
            vaultCrypto = vaultCrypto
        )

        val customChars = customPassphrase?.trim()?.takeIf { it.isNotEmpty() }?.toCharArray()
        val passChars = if (customChars != null) {
            tokenStorage.saveSyncPassphrase(customChars)
            customChars
        } else {
            val coordinator = SharedIosSyncFactory.createAutoSyncKeyCoordinator(
                remoteDataSource = driveDataSource,
                tokenStorage = tokenStorage,
                clock = clock
            )
            when (val passRes = coordinator.resolveOrBootstrapPassphrase()) {
                is AppResult.Failure -> return IosDriveSyncResult(false, errorMessage = passRes.error.toUserMessage())
                is AppResult.Success -> passRes.data
            }
        }

        return try {
            when (val syncRes = syncEngine.synchronize(passChars)) {
                is AppResult.Failure -> IosDriveSyncResult(false, errorMessage = syncRes.error.toUserMessage())
                is AppResult.Success -> {
                    val s = syncRes.data
                    IosDriveSyncResult(
                        success = true,
                        pulledCategories = s.pulledCategories,
                        pulledNotes = s.pulledNotes,
                        pulledLinks = s.pulledLinks,
                        pulledStreamItems = s.pulledStreamItems,
                        pulledRules = s.pulledRules,
                        pushedEntities = s.pushedEntities,
                        conflictCopiesCreated = s.conflictCopiesCreated,
                        mediaUploaded = s.mediaUploaded,
                        mediaDownloaded = s.mediaDownloaded,
                        syncedAt = s.syncedAt
                    )
                }
            }
        } finally {
            passChars.fill('\u0000')
        }
    }

    suspend fun hasTokenBundle(tokenStorage: SecureTokenStorage): Boolean =
        (tokenStorage.getTokenBundle() as? AppResult.Success)?.data != null

    suspend fun hasSyncPassphrase(tokenStorage: SecureTokenStorage): Boolean {
        val res = tokenStorage.getSyncPassphrase() as? AppResult.Success ?: return false
        val chars = res.data ?: return false
        val notEmpty = chars.isNotEmpty()
        chars.fill('\u0000')
        return notEmpty
    }

    suspend fun saveSyncPassphraseString(tokenStorage: SecureTokenStorage, passphrase: String): Boolean {
        val chars = passphrase.toCharArray()
        return try {
            tokenStorage.saveSyncPassphrase(chars) is AppResult.Success
        } finally {
            chars.fill('\u0000')
        }
    }

    suspend fun clearAllTokens(tokenStorage: SecureTokenStorage): Boolean =
        tokenStorage.clearAll() is AppResult.Success

    fun makeCategory(
        id: Int,
        name: String,
        isPassword: Boolean?,
        syncUuid: String,
        updatedAt: Long,
        deletedAt: Long?,
        syncVersion: Long
    ): Category = Category(
        id = id,
        name = name,
        isPassword = isPassword,
        syncUuid = syncUuid,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncVersion = syncVersion
    )

    fun makeNote(
        id: Int,
        title: String?,
        description: String?,
        date: Long,
        isPassword: Boolean?,
        images: List<String>?,
        categoryId: Int?,
        sounds: List<String>?,
        isPinned: Boolean,
        icon: String?,
        isEncrypted: Boolean,
        totalTasks: Int,
        completedTasks: Int,
        status: String,
        reminderTime: Long?,
        reminderType: String?,
        reminderSound: String?,
        isReminderDone: Boolean,
        richBlocksJson: String?,
        syncUuid: String,
        categorySyncUuid: String?,
        updatedAt: Long,
        deletedAt: Long?,
        syncVersion: Long
    ): Note = Note(
        id = id,
        title = title,
        description = description,
        date = date,
        isPassword = isPassword,
        images = images,
        categoryId = categoryId,
        sounds = sounds,
        isPinned = isPinned,
        icon = icon,
        isEncrypted = isEncrypted,
        totalTasks = totalTasks,
        completedTasks = completedTasks,
        status = status,
        reminderTime = reminderTime,
        reminderType = reminderType,
        reminderSound = reminderSound,
        isReminderDone = isReminderDone,
        richBlocksJson = richBlocksJson,
        syncUuid = syncUuid,
        categorySyncUuid = categorySyncUuid,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncVersion = syncVersion
    )

    fun makeNoteLink(
        id: Int,
        sourceNoteId: Int,
        targetNoteId: Int,
        linkText: String,
        createdAt: Long,
        syncUuid: String,
        sourceSyncUuid: String,
        targetSyncUuid: String,
        updatedAt: Long,
        deletedAt: Long?,
        syncVersion: Long
    ): NoteLink = NoteLink(
        id = id,
        sourceNoteId = sourceNoteId,
        targetNoteId = targetNoteId,
        linkText = linkText,
        createdAt = createdAt,
        syncUuid = syncUuid,
        sourceSyncUuid = sourceSyncUuid,
        targetSyncUuid = targetSyncUuid,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncVersion = syncVersion
    )

    fun makeStreamItem(
        id: Int,
        content: String,
        type: String,
        audioPath: String?,
        imagePath: String?,
        timestamp: Long,
        isProcessed: Boolean,
        mergedNoteId: Int?,
        syncUuid: String,
        mergedNoteSyncUuid: String?,
        updatedAt: Long,
        deletedAt: Long?,
        syncVersion: Long
    ): StreamItem = StreamItem(
        id = id,
        content = content,
        type = type,
        audioPath = audioPath,
        imagePath = imagePath,
        timestamp = timestamp,
        isProcessed = isProcessed,
        mergedNoteId = mergedNoteId,
        syncUuid = syncUuid,
        mergedNoteSyncUuid = mergedNoteSyncUuid,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncVersion = syncVersion
    )

    fun makeAutomationRule(
        id: Int,
        title: String,
        triggerType: String,
        triggerConfig: String?,
        actionType: String,
        actionConfig: String?,
        isEnabled: Boolean,
        createdAt: Long,
        syncUuid: String,
        updatedAt: Long,
        deletedAt: Long?,
        syncVersion: Long
    ): AutomationRule = AutomationRule(
        id = id,
        title = title,
        triggerType = triggerType,
        triggerConfig = triggerConfig,
        actionType = actionType,
        actionConfig = actionConfig,
        isEnabled = isEnabled,
        createdAt = createdAt,
        syncUuid = syncUuid,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncVersion = syncVersion
    )

    fun makeLocalSyncSnapshot(
        categories: List<Category>,
        notes: List<Note>,
        noteLinks: List<NoteLink>,
        streamItems: List<StreamItem>,
        automationRules: List<AutomationRule>
    ): LocalSyncSnapshot = LocalSyncSnapshot(
        categories = categories,
        notes = notes,
        noteLinks = noteLinks,
        streamItems = streamItems,
        automationRules = automationRules
    )

    fun makeSyncCursorState(
        remoteCursor: String?,
        lastPushedAt: Long,
        lastSyncedAt: Long,
        deviceId: String,
        uploadedMediaKeys: List<String>
    ): SyncCursorState = SyncCursorState(
        remoteCursor = remoteCursor,
        lastPushedAt = lastPushedAt,
        lastSyncedAt = lastSyncedAt,
        deviceId = deviceId,
        uploadedMediaKeys = uploadedMediaKeys.toSet()
    )

    fun makeSuccessSnapshotResult(snapshot: LocalSyncSnapshot): AppResult<LocalSyncSnapshot, DataError.Local> =
        AppResult.Success(snapshot)

    fun makeSuccessUnitLocalResult(): AppResult<Unit, DataError.Local> =
        AppResult.Success(Unit)

    fun makeFailureUnitLocalResult(error: DataError.Local): AppResult<Unit, DataError.Local> =
        AppResult.Failure(error)

    fun makeSuccessTokenBundleResult(bundle: OAuthTokenBundle?): AppResult<OAuthTokenBundle?, DataError.Security> =
        AppResult.Success(bundle)

    fun makeSuccessCharArrayResult(passphrase: String?): AppResult<CharArray?, DataError.Security> =
        AppResult.Success(passphrase?.toCharArray())

    fun makeSuccessUnitSecurityResult(): AppResult<Unit, DataError.Security> =
        AppResult.Success(Unit)

    fun makeFailureSecurityResult(error: DataError.Security): AppResult<Unit, DataError.Security> =
        AppResult.Failure(error)

    fun makeFailureTokenBundleResult(error: DataError.Security): AppResult<OAuthTokenBundle?, DataError.Security> =
        AppResult.Failure(error)

    fun makeFailureCharArrayResult(error: DataError.Security): AppResult<CharArray?, DataError.Security> =
        AppResult.Failure(error)

    private fun AppError.toUserMessage(): String = when (this) {
        DataError.Local.DISK_FULL -> "Disk alanı yetersiz olduğu için kayıt yapılamadı."
        DataError.Local.NOT_FOUND -> "İstenen kayıt bulunamadı."
        DataError.Local.VALIDATION_FAILED -> "Lütfen geçerli bilgiler girin."
        DataError.Local.UNKNOWN -> "Yerel veritabanı hatası oluştu."
        DataError.Security.UNAUTHORIZED -> "Yetkilendirme doğrulanamadı."
        DataError.Security.DECRYPTION_FAILED -> "Şifre çözme başarısız: parola hatalı veya veri bozulmuş."
        DataError.Security.KEYSTORE_UNAVAILABLE -> "iOS Keychain erişilemedi."
        DataError.Security.KEY_REQUIRED -> "Devam etmek için şifreleme parolası gereklidir."
        DataError.Security.INTEGRITY_MISMATCH -> "Medya bütünlük doğrulaması başarısız oldu."
        DataError.Network.TIMEOUT -> "Bağlantı veya yetkilendirme zaman aşımına uğradı."
        DataError.Network.NO_CONNECTION -> "İnternet bağlantısı kurulamadı."
        DataError.Network.SERIALIZATION -> "Sunucudan gelen veri çözümlenemedi."
        DataError.Network.UNAUTHORIZED -> "Oturum süresi doldu veya izin reddedildi. Tekrar bağlanın."
        DataError.Network.RATE_LIMITED -> "Çok fazla istek gönderildi. Bir süre sonra tekrar deneyin."
        DataError.Network.QUOTA_EXCEEDED -> "Google Drive uygulama alanı kotası dolu."
        DataError.Network.SERVER -> "Google Drive geçici bir sunucu hatası bildirdi."
        DataError.Network.TLS_ERROR -> "Güvenli TLS doğrulaması başarısız oldu."
        DataError.Network.UNKNOWN -> "Beklenmeyen ağ hatası oluştu."
        is CodeExecutionError -> "Kod çalıştırma hatası."
    }
}

object SharedIosEditorBridge {
    private val hashtagRegex = Regex("#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)")
    private val wikiLinkRegex = Regex("\\[\\[([^\\]]+)]]")

    fun platformName(): String = currentPlatformDisplayName()

    fun newUuid(): String = sharedRandomUuid()

    fun parseBlocks(raw: String?): List<NoteBlock> = BlockParser.parse(raw)

    fun serializeBlocks(blocks: List<NoteBlock>): String = BlockParser.blocksToJson(blocks)

    fun createBlock(
        type: BlockType,
        content: String,
        isChecked: Boolean = false,
        calloutIcon: String = "💡",
        mediaPath: String? = null,
        isExpanded: Boolean = true,
        tableData: List<List<String>> = emptyList(),
        toggleBody: String = "",
        codeLanguage: String = "kotlin",
        subPageNoteId: Int? = null,
        subPageSyncUuid: String? = null
    ): NoteBlock = NoteBlock(
        id = sharedRandomUuid(),
        type = type,
        content = content,
        isChecked = isChecked,
        calloutIcon = calloutIcon,
        mediaPath = mediaPath,
        isExpanded = isExpanded,
        tableData = tableData,
        toggleBody = toggleBody,
        codeLanguage = codeLanguage,
        subPageNoteId = subPageNoteId,
        subPageSyncUuid = subPageSyncUuid
    )

    fun analyzeCodeSyntax(code: String, language: String): StaticSyntaxReport =
        StaticSyntaxChecker.analyze(code, language)

    fun extractHashtags(text: String): List<String> =
        hashtagRegex.findAll(text).map { it.groupValues[1] }.distinct().toList()

    fun extractWikiLinks(text: String): List<String> =
        wikiLinkRegex.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()
}
