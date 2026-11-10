package com.notes.purenoteit

import com.notes.purenoteit.security.DesktopOAuthPkceManager
import com.notes.purenoteit.security.DesktopOsKeyringVault
import com.notes.purenoteit.security.DesktopOsKeyringVaultImpl
import com.notes.purenoteit.security.DesktopSecureTokenStorageImpl
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.SecureTokenStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Public factory exposing `:shared` desktop OS keychain and loopback OAuth PKCE components
 * while keeping implementations cleanly encapsulated (`internal`).
 */
object SharedDesktopAuthFactory {
    fun createSecureTokenStorage(
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): SecureTokenStorage = DesktopSecureTokenStorageImpl(ioDispatcher = ioDispatcher)

    fun createOsKeyringVault(
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): DesktopOsKeyringVault = DesktopOsKeyringVaultImpl(ioDispatcher = ioDispatcher)

    fun createLoopbackOAuthManager(
        pkceProtocol: OAuthPkceProtocol,
        tokenStorage: SecureTokenStorage,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): DesktopOAuthPkceManager = DesktopOAuthPkceManager(
        pkceProtocol = pkceProtocol,
        tokenStorage = tokenStorage,
        ioDispatcher = ioDispatcher
    )
}
