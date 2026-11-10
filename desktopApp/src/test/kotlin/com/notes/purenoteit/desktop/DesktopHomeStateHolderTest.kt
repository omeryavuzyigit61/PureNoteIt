package com.notes.purenoteit.desktop

import app.cash.turbine.test
import com.notes.purenoteit.SharedCryptoFactory
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.SharedSyncFactory
import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepositoryImpl
import com.notes.purenoteit.desktop.data.runner.DesktopCodeRunnerImpl
import com.notes.purenoteit.desktop.data.security.DesktopVaultSecurityManager
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.desktop.data.sync.DesktopLocalSyncStoreImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncServer
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEffect
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEvent
import com.notes.purenoteit.desktop.ui.contract.DesktopViewMode
import com.notes.purenoteit.desktop.ui.contract.SplitViewMode
import com.notes.purenoteit.desktop.ui.viewmodel.DesktopHomeStateHolder
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.security.DesktopOAuthPkceManager
import com.notes.purenoteit.security.DesktopOsKeyringVault
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.SyncTokenProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopHomeStateHolderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var clockMs = 1_760_200_000_000L
    private val clock = AppClock { clockMs }

    @Test
    fun `MVI durum yonetimi not alt sayfa tombstone kasa parolasi kilitleme ve acma calismalidir`() =
        runBlocking {
            val root = tempFolder.newFolder("mvi_test")
            val keyring = FakeOsKeyringVault()
            lateinit var securityMgr: DesktopVaultSecurityManager
            val db = DesktopSqliteDatabaseManager(
                dbFile = File(root, "mvi.db"),
                clock = clock,
                ioDispatcher = Dispatchers.IO,
                dbKeyProvider = { securityMgr.currentDbKeyCopy() }
            )
            val rawMedia = SharedStorageFactory.createMediaPathResolver(File(root, "files"), File(root, "cache"))
            val encMedia = DesktopEncryptedMediaStorage(
                delegate = rawMedia,
                mediaKeyProvider = { securityMgr.currentMediaKeyCopy() },
                rootFilesDir = File(root, "files")
            )
            securityMgr = DesktopVaultSecurityManager(
                securityMetadataFile = File(root, "vault_security.json"),
                keyringVault = keyring,
                dbManagerProvider = { db },
                encryptedMediaStorageProvider = { encMedia }
            )

            val repo = DesktopNoteRepositoryImpl(db, clock)
            val syncStore = DesktopLocalSyncStoreImpl(db, encMedia, File(root, "cursor.json"))
            val crypto = SharedCryptoFactory.createVaultCrypto()
            val mockEngine = MockEngine { respond("{}", HttpStatusCode.OK) }
            val pkceProtocol = OAuthPkceProtocol(mockEngine, clock)
            val tokenStorage = FakeTokenStorage()
            val pkceManager = DesktopOAuthPkceManager(pkceProtocol, tokenStorage)
            val remoteDs = SharedSyncFactory.createGoogleDriveDataSource(
                tokenProvider = object : SyncTokenProvider {
                    override suspend fun getAccessToken() = AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    override suspend fun refreshAccessToken(expiredToken: String?) =
                        AppResult.Failure(DataError.Network.UNAUTHORIZED)
                },
                clock = clock,
                httpEngine = mockEngine
            )
            val syncEngine = SharedSyncFactory.createSyncEngine(syncStore, remoteDs, clock, crypto)
            val codeRunner = DesktopCodeRunnerImpl(Dispatchers.IO, clock, processTimeoutMs = 3_000L)
            val lanServer = LanWebSocketSyncServer()

            val stateHolder = DesktopHomeStateHolder(
                noteRepository = repo,
                mediaPathResolver = encMedia,
                codeExecutor = codeRunner,
                tokenStorage = tokenStorage,
                pkceManager = pkceManager,
                driveSyncEngine = syncEngine,
                localSyncStore = syncStore,
                vaultCrypto = crypto,
                lanSyncServer = lanServer,
                oauthClientConfigProvider = OAuthClientConfigProvider {
                    OAuthPkceConfig(clientId = "desktop-test", redirectUri = "http://127.0.0.1/oauth2redirect")
                },
                vaultSecurityManager = securityMgr,
                clock = clock,
                mainDispatcher = Dispatchers.Default
            )

            try {
                stateHolder.effects.test {
                    waitUntil { !stateHolder.uiState.value.isVaultLocked && stateHolder.uiState.value.categories.size == 3 }
                    assertEquals(SplitViewMode.NONE, stateHolder.uiState.value.splitViewMode)

                    stateHolder.onEvent(DesktopUiEvent.CreateNewNote)
                    waitUntil { stateHolder.uiState.value.allActiveNotes.size == 1 }

                    val createdUuid = stateHolder.uiState.value.selectedNoteSyncUuid!!
                    stateHolder.onEvent(DesktopUiEvent.UpdateTitle("Masaüstü Mimari"))
                    stateHolder.onEvent(DesktopUiEvent.AddBlock(BlockType.CODE))
                    stateHolder.onEvent(DesktopUiEvent.SaveCurrentNote)
                    assertTrue((awaitItem() as DesktopUiEffect.ShowMessage).message.contains("kaydedildi"))

                    stateHolder.onEvent(DesktopUiEvent.CreateSubPage("Faz 4 Kontrolü"))
                    assertTrue((awaitItem() as DesktopUiEffect.ShowMessage).message.contains("alt sayfası"))
                    waitUntil { stateHolder.uiState.value.allActiveNotes.size == 2 }
                    assertEquals(SplitViewMode.SUB_PAGE, stateHolder.uiState.value.splitViewMode)

                    stateHolder.onEvent(
                        DesktopUiEvent.RunCodeBlock("sql-1", "sql", "SELECT 40 + 2 AS answer;")
                    )
                    waitUntil { stateHolder.uiState.value.codeRunStates["sql-1"]?.output != null }
                    val sqlOut = stateHolder.uiState.value.codeRunStates["sql-1"]?.output as CodeExecutionOutput.SqlTable
                    assertEquals(listOf(listOf("42")), sqlOut.rows)

                    stateHolder.onEvent(DesktopUiEvent.RunCodeBlock("bash-1", "bash", "echo test"))
                    waitUntil { stateHolder.uiState.value.codeRunStates["bash-1"]?.awaitingBashApproval == true }

                    stateHolder.onEvent(DesktopUiEvent.SoftDeleteNote(createdUuid))
                    assertTrue((awaitItem() as DesktopUiEffect.ShowMessage).message.contains("çöp kutusuna"))
                    waitUntil { stateHolder.uiState.value.trashedNotes.any { it.syncUuid == createdUuid } }

                    stateHolder.onEvent(DesktopUiEvent.RestoreNote(createdUuid))
                    assertTrue((awaitItem() as DesktopUiEffect.ShowMessage).message.contains("geri yüklendi"))
                    waitUntil { stateHolder.uiState.value.allActiveNotes.any { it.syncUuid == createdUuid } }
                    assertFalse(stateHolder.uiState.value.trashedNotes.any { it.syncUuid == createdUuid })

                    // Open note -> NavigateHome brings user cleanly back to Home cards grid
                    stateHolder.onEvent(DesktopUiEvent.SelectNote(createdUuid))
                    waitUntil { stateHolder.uiState.value.selectedNoteSyncUuid == createdUuid }
                    stateHolder.onEvent(DesktopUiEvent.NavigateHome)
                    waitUntil { stateHolder.uiState.value.selectedNoteSyncUuid == null }
                    assertEquals(2, stateHolder.uiState.value.allActiveNotes.size)

                    // Starter Template, Home View Mode, Note Status & Category Dialog Lifecycle
                    stateHolder.onEvent(DesktopUiEvent.SetHomeViewMode(DesktopViewMode.BOARD))
                    assertEquals(DesktopViewMode.BOARD, stateHolder.uiState.value.homeViewMode)

                    stateHolder.onEvent(DesktopUiEvent.SetCreateCategoryDialogVisible(true))
                    assertTrue(stateHolder.uiState.value.showCreateCategoryDialog)
                    stateHolder.onEvent(DesktopUiEvent.CreateCategory("Projeler"))
                    assertTrue((awaitItem() as DesktopUiEffect.ShowMessage).message.contains("Projeler"))
                    waitUntil {
                        !stateHolder.uiState.value.showCreateCategoryDialog &&
                            stateHolder.uiState.value.categories.any { it.name == "Projeler" }
                    }
                    val customCatUuid = stateHolder.uiState.value.categories.first { it.name == "Projeler" }.syncUuid
                    stateHolder.onEvent(DesktopUiEvent.DeleteCategory(customCatUuid))
                    waitUntil { stateHolder.uiState.value.categories.none { it.syncUuid == customCatUuid } }

                    stateHolder.onEvent(
                        DesktopUiEvent.CreateNoteFromTemplate(
                            title = "Günlük Plan",
                            icon = "✅",
                            blocks = listOf(NoteBlock(type = BlockType.TODO, content = "Sabah rutini", isChecked = false))
                        )
                    )
                    waitUntil { stateHolder.uiState.value.allActiveNotes.size == 3 }
                    val templateNoteUuid = stateHolder.uiState.value.selectedNoteSyncUuid!!
                    assertEquals(1, stateHolder.uiState.value.allActiveNotes.first { it.syncUuid == templateNoteUuid }.totalTasks)

                    stateHolder.onEvent(DesktopUiEvent.UpdateNoteStatus(templateNoteUuid, "IN_PROGRESS"))
                    waitUntil {
                        stateHolder.uiState.value.allActiveNotes.first { it.syncUuid == templateNoteUuid }.status == "IN_PROGRESS"
                    }
                    stateHolder.onEvent(DesktopUiEvent.NavigateHome)
                    waitUntil { stateHolder.uiState.value.selectedNoteSyncUuid == null }

                    // Enable Master Password, lock vault -> UI clears notes from RAM, then unlock again
                    stateHolder.onEvent(DesktopUiEvent.EnableMasterPassword("MasterKasa#2026".toCharArray(), 5))
                    assertTrue((awaitItem() as DesktopUiEffect.ShowMessage).message.contains("Ana Parola kilidi"))
                    waitUntil { stateHolder.uiState.value.isMasterLockEnabled }

                    stateHolder.onEvent(DesktopUiEvent.LockVaultNow)
                    waitUntil { stateHolder.uiState.value.isVaultLocked }
                    assertTrue(stateHolder.uiState.value.allActiveNotes.isEmpty())
                    assertEquals("", stateHolder.uiState.value.editorTitle)

                    // Wrong password leaves locked with error
                    stateHolder.onEvent(DesktopUiEvent.UnlockVault("WrongPass!".toCharArray()))
                    waitUntil { stateHolder.uiState.value.vaultLockError != null }
                    assertTrue(stateHolder.uiState.value.isVaultLocked)

                    // Correct password unlocks & restores note visibility
                    stateHolder.onEvent(DesktopUiEvent.UnlockVault("MasterKasa#2026".toCharArray()))
                    waitUntil { !stateHolder.uiState.value.isVaultLocked && stateHolder.uiState.value.allActiveNotes.size == 3 }

                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                stateHolder.close()
                lanServer.close()
                db.close()
            }
        }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!predicate() && System.currentTimeMillis() - start < timeoutMs) {
            delay(20L)
        }
        assertTrue("Beklenen state kosulu zamaninda olusmadi", predicate())
    }

    private class FakeOsKeyringVault : DesktopOsKeyringVault {
        private val map = mutableMapOf<String, String>()
        override suspend fun saveSecret(account: String, secretChars: CharArray): AppResult<Unit, DataError.Security> {
            map[account] = String(secretChars)
            return AppResult.Success(Unit)
        }

        override suspend fun readSecret(account: String): AppResult<CharArray?, DataError.Security> =
            AppResult.Success(map[account]?.toCharArray())

        override suspend fun deleteSecret(account: String): AppResult<Unit, DataError.Security> {
            map.remove(account)
            return AppResult.Success(Unit)
        }
    }

    private class FakeTokenStorage : SecureTokenStorage {
        override suspend fun saveTokenBundle(bundle: OAuthTokenBundle) = AppResult.Success(Unit)
        override suspend fun getTokenBundle() = AppResult.Success<OAuthTokenBundle?>(null)
        override suspend fun saveSyncPassphrase(passphrase: CharArray) = AppResult.Success(Unit)
        override suspend fun getSyncPassphrase() = AppResult.Success<CharArray?>(null)
        override suspend fun clearAll() = AppResult.Success(Unit)
    }
}
