package com.notes.purenoteit

import android.content.Context
import app.cash.turbine.test
import com.notes.purenoteit.data.security.AndroidOAuthPkceManager
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.model.SyncSummary
import com.notes.purenoteit.ui.mvi.CloudSyncUiEffect
import com.notes.purenoteit.ui.mvi.CloudSyncUiEvent
import com.notes.purenoteit.viewmodel.CloudSyncViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var oauthPkceManager: AndroidOAuthPkceManager
    private lateinit var configProvider: OAuthClientConfigProvider
    private lateinit var tokenStorage: SecureTokenStorage
    private lateinit var syncEngine: SyncEngine
    private lateinit var autoSyncKeyCoordinator: AutoSyncKeyCoordinator
    private lateinit var activityContext: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        oauthPkceManager = mockk(relaxed = true)
        configProvider = mockk()
        tokenStorage = mockk(relaxed = true)
        syncEngine = mockk(relaxed = true)
        autoSyncKeyCoordinator = mockk()
        activityContext = mockk(relaxed = true)

        coEvery { tokenStorage.getTokenBundle() } returns AppResult.Success(null)
        coEvery { tokenStorage.getSyncPassphrase() } returns AppResult.Success(null)
        every { configProvider.getConfig() } returns OAuthPkceConfig(
            clientId = "test-client.apps.googleusercontent.com",
            redirectUri = "com.purenoteit.app:/oauth2redirect"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init refreshes stored drive session and zeroes peeked passphrase`() = runTest(testDispatcher) {
        val storedPass = "master-pass".toCharArray()
        coEvery { tokenStorage.getTokenBundle() } returns AppResult.Success(
            OAuthTokenBundle(accessToken = "tok", expiresAtEpochMillis = 9_999_999L)
        )
        coEvery { tokenStorage.getSyncPassphrase() } returns AppResult.Success(storedPass)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDriveConnected)
        assertTrue(viewModel.uiState.value.hasStoredPassphrase)
        assertTrue(storedPass.all { it == '\u0000' })
    }

    @Test
    fun `connect drive with missing oauth config emits unconfigured error without custom tab`() = runTest(testDispatcher) {
        every { configProvider.getConfig() } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(CloudSyncUiEvent.ConnectGoogleDrive(activityContext))
            val effect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_client_unconfigured, effect.messageRes)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { oauthPkceManager.authorizeWithCustomTab(any(), any()) }
    }

    @Test
    fun `connect drive authorizes custom tab bootstraps auto sync key syncs and zeroes key`() = runTest(testDispatcher) {
        val bootstrappedKey = "auto-generated-base64-key".toCharArray()
        coEvery { oauthPkceManager.authorizeWithCustomTab(any(), any()) } returns AppResult.Success(
            OAuthTokenBundle(accessToken = "access-1", expiresAtEpochMillis = 9_999_999L)
        )
        coEvery { autoSyncKeyCoordinator.resolveOrBootstrapPassphrase() } returns AppResult.Success(bootstrappedKey)
        coEvery { syncEngine.synchronize(any()) } returns AppResult.Success(
            SyncSummary(pushedEntities = 3, pulledNotes = 2)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(CloudSyncUiEvent.ConnectGoogleDrive(activityContext))
            advanceUntilIdle()

            val connectedEffect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_connected_toast, connectedEffect.messageRes)

            val summaryEffect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_completed_summary, summaryEffect.messageRes)
            assertEquals(3, summaryEffect.arg1)
            assertEquals(2, summaryEffect.arg2)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(bootstrappedKey.all { it == '\u0000' })
        assertTrue(viewModel.uiState.value.isDriveConnected)
        assertTrue(viewModel.uiState.value.hasStoredPassphrase)
        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals(3, viewModel.uiState.value.lastSyncPushed)
        assertEquals(2, viewModel.uiState.value.lastSyncPulled)
    }

    @Test
    fun `trigger sync now resolves auto key runs syncEngine and zeroes passphrase`() = runTest(testDispatcher) {
        val autoKey = "vault-key-42".toCharArray()
        coEvery { autoSyncKeyCoordinator.resolveOrBootstrapPassphrase() } returns AppResult.Success(autoKey)
        coEvery { syncEngine.synchronize(any()) } returns AppResult.Success(
            SyncSummary(pushedEntities = 1, pulledNotes = 4)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(CloudSyncUiEvent.TriggerSyncNow)
            advanceUntilIdle()

            val summaryEffect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_completed_summary, summaryEffect.messageRes)
            assertEquals(1, summaryEffect.arg1)
            assertEquals(4, summaryEffect.arg2)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(autoKey.all { it == '\u0000' })
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    @Test
    fun `trigger sync now maps network error when auto key bootstrap fails`() = runTest(testDispatcher) {
        coEvery { autoSyncKeyCoordinator.resolveOrBootstrapPassphrase() } returns AppResult.Failure(
            DataError.Network.NO_CONNECTION
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(CloudSyncUiEvent.TriggerSyncNow)
            advanceUntilIdle()

            val errorEffect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_error_network, errorEffect.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trigger sync now maps decryption failure to user-friendly string resource`() = runTest(testDispatcher) {
        val autoKey = "wrong-pass".toCharArray()
        coEvery { autoSyncKeyCoordinator.resolveOrBootstrapPassphrase() } returns AppResult.Success(autoKey)
        coEvery { syncEngine.synchronize(any()) } returns AppResult.Failure(DataError.Security.DECRYPTION_FAILED)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(CloudSyncUiEvent.TriggerSyncNow)
            advanceUntilIdle()

            val errorEffect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_error_decrypt, errorEffect.messageRes)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(autoKey.all { it == '\u0000' })
    }

    @Test
    fun `disconnect clears token storage resets state and emits disconnected toast`() = runTest(testDispatcher) {
        coEvery { tokenStorage.getTokenBundle() } returns AppResult.Success(
            OAuthTokenBundle(accessToken = "tok", expiresAtEpochMillis = 9_999_999L)
        )
        coEvery { tokenStorage.clearAll() } returns AppResult.Success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDriveConnected)

        viewModel.effects.test {
            viewModel.onEvent(CloudSyncUiEvent.DisconnectGoogleDrive)
            advanceUntilIdle()

            val effect = awaitItem() as CloudSyncUiEffect.ShowMessage
            assertEquals(R.string.cloud_sync_disconnected_toast, effect.messageRes)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { tokenStorage.clearAll() }
        assertFalse(viewModel.uiState.value.isDriveConnected)
        assertFalse(viewModel.uiState.value.hasStoredPassphrase)
        assertNull(viewModel.uiState.value.lastSyncPushed)
        assertNull(viewModel.uiState.value.lastSyncPulled)
    }

    private fun createViewModel(): CloudSyncViewModel = CloudSyncViewModel(
        oauthPkceManager = oauthPkceManager,
        configProvider = configProvider,
        tokenStorage = tokenStorage,
        syncEngine = syncEngine,
        autoSyncKeyCoordinator = autoSyncKeyCoordinator
    )
}
