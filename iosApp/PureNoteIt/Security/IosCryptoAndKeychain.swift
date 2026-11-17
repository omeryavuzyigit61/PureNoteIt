import AuthenticationServices
import CommonCrypto
import CryptoKit
import Foundation
import LocalAuthentication
import PureNoteShared
import Security
import UIKit

/// Native Apple CryptoKit (AES-256-GCM) + CommonCrypto (PBKDF2-HMAC-SHA256) bridge
/// backing `:shared`'s portable `ENC_P3_PWD` / `PVM3` cryptographic format on iOS.
final class AppleCryptoBridge: NSObject, IosNativeCryptoBridge {
    func derivePbkdf2HmacSha256(
        passphrase: String,
        salt: KotlinByteArray,
        iterations: Int32,
        keyByteLength: Int32
    ) -> KotlinByteArray? {
        guard keyByteLength > 0, iterations > 0 else { return nil }
        let saltData = salt.toData()
        let passwordData = Data(passphrase.utf8)
        var derived = [UInt8](repeating: 0, count: Int(keyByteLength))
        defer { derived.resetBytes(in: 0..<derived.count) }

        let status = passwordData.withUnsafeBytes { passPtr in
            saltData.withUnsafeBytes { saltPtr in
                let passBase = passPtr.bindMemory(to: Int8.self).baseAddress
                let saltBase = saltPtr.bindMemory(to: UInt8.self).baseAddress
                return CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passBase,
                    passwordData.count,
                    saltBase,
                    saltData.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    UInt32(iterations),
                    &derived,
                    derived.count
                )
            }
        }

        guard status == kCCSuccess else { return nil }
        return Data(derived).toKotlinByteArray()
    }

    func aesGcmEncrypt(
        plainBytes: KotlinByteArray,
        key: KotlinByteArray,
        iv: KotlinByteArray
    ) -> KotlinByteArray? {
        let keyData = key.toData()
        let ivData = iv.toData()
        let plainData = plainBytes.toData()

        do {
            let symKey = SymmetricKey(data: keyData)
            let nonce = try AES.GCM.Nonce(data: ivData)
            let sealed = try AES.GCM.seal(plainData, using: symKey, nonce: nonce)
            var out = Data()
            out.reserveCapacity(sealed.ciphertext.count + sealed.tag.count)
            out.append(sealed.ciphertext)
            out.append(sealed.tag)
            return out.toKotlinByteArray()
        } catch {
            return nil
        }
    }

    func aesGcmDecrypt(
        cipherAndTagBytes: KotlinByteArray,
        key: KotlinByteArray,
        iv: KotlinByteArray
    ) -> KotlinByteArray? {
        let combined = cipherAndTagBytes.toData()
        let tagLength = 16
        guard combined.count > tagLength else { return nil }

        let ciphertext = combined.prefix(combined.count - tagLength)
        let tag = combined.suffix(tagLength)
        let keyData = key.toData()
        let ivData = iv.toData()

        do {
            let symKey = SymmetricKey(data: keyData)
            let nonce = try AES.GCM.Nonce(data: ivData)
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            let plain = try AES.GCM.open(box, using: symKey)
            return plain.toKotlinByteArray()
        } catch {
            return nil
        }
    }
}

enum IosKeychainHelper {
    private static let service = "com.purenoteit.ios"

    @discardableResult
    static func saveString(_ value: String, account: String) -> Bool {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(attributes as CFDictionary, nil) == errSecSuccess
    }

    static func readString(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    @discardableResult
    static func deleteString(account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}

private struct CodableOAuthTokenBundle: Codable {
    let accessToken: String
    let refreshToken: String?
    let expiresAtEpochMillis: Int64
    let tokenType: String
    let scope: String
}

/// iOS Apple Keychain implementation of `:shared`'s `SecureTokenStorage`.
final class IosSecureTokenStorage: NSObject, SecureTokenStorage {
    private static let accountOAuthBundle = "oauth_drive_bundle_v1"
    private static let accountSyncPassphrase = "e2ee_sync_passphrase_v1"
    private static let accountIosClientId = "ios_oauth_client_id_v1"

    func saveCustomClientId(_ clientId: String) -> Bool {
        let clean = clientId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return false }
        return IosKeychainHelper.saveString(clean, account: Self.accountIosClientId)
    }

    func getCustomClientId() -> String? {
        IosKeychainHelper.readString(account: Self.accountIosClientId)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func saveTokenBundle(bundle: OAuthTokenBundle) async throws -> any AppResult {
        let dto = CodableOAuthTokenBundle(
            accessToken: bundle.accessToken,
            refreshToken: bundle.refreshToken,
            expiresAtEpochMillis: bundle.expiresAtEpochMillis,
            tokenType: bundle.tokenType,
            scope: bundle.scope
        )
        guard let data = try? JSONEncoder().encode(dto),
              let json = String(data: data, encoding: .utf8),
              IosKeychainHelper.saveString(json, account: Self.accountOAuthBundle) else {
            return SharedIosSyncBridge.shared.makeFailureSecurityResult(error: .keystoreUnavailable)
        }
        return SharedIosSyncBridge.shared.makeSuccessUnitSecurityResult()
    }

    func getTokenBundle() async throws -> any AppResult {
        guard let raw = IosKeychainHelper.readString(account: Self.accountOAuthBundle),
              !raw.isEmpty else {
            return SharedIosSyncBridge.shared.makeSuccessTokenBundleResult(bundle: nil)
        }
        guard let data = raw.data(using: .utf8),
              let dto = try? JSONDecoder().decode(CodableOAuthTokenBundle.self, from: data) else {
            IosKeychainHelper.deleteString(account: Self.accountOAuthBundle)
            return SharedIosSyncBridge.shared.makeFailureTokenBundleResult(error: .decryptionFailed)
        }
        let bundle = OAuthTokenBundle(
            accessToken: dto.accessToken,
            refreshToken: dto.refreshToken,
            expiresAtEpochMillis: dto.expiresAtEpochMillis,
            tokenType: dto.tokenType,
            scope: dto.scope
        )
        return SharedIosSyncBridge.shared.makeSuccessTokenBundleResult(bundle: bundle)
    }

    func saveSyncPassphrase(passphrase: KotlinCharArray) async throws -> any AppResult {
        let str = passphrase.toSwiftString()
        guard !str.isEmpty else {
            return SharedIosSyncBridge.shared.makeFailureSecurityResult(error: .keyRequired)
        }
        guard IosKeychainHelper.saveString(str, account: Self.accountSyncPassphrase) else {
            return SharedIosSyncBridge.shared.makeFailureSecurityResult(error: .keystoreUnavailable)
        }
        return SharedIosSyncBridge.shared.makeSuccessUnitSecurityResult()
    }

    func getSyncPassphrase() async throws -> any AppResult {
        let raw = IosKeychainHelper.readString(account: Self.accountSyncPassphrase)
        return SharedIosSyncBridge.shared.makeSuccessCharArrayResult(passphrase: raw)
    }

    func clearAll() async throws -> any AppResult {
        IosKeychainHelper.deleteString(account: Self.accountOAuthBundle)
        IosKeychainHelper.deleteString(account: Self.accountSyncPassphrase)
        return SharedIosSyncBridge.shared.makeSuccessUnitSecurityResult()
    }
}

/// RFC 8252 iOS `ASWebAuthenticationSession` OAuth 2.0 PKCE orchestrator wired to `:shared`.
@MainActor
final class IosOAuthPkceManager: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var activeSession: ASWebAuthenticationSession?

    static func computeGoogleRedirectUri(clientId: String) -> (scheme: String, redirectUri: String)? {
        let clean = clientId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return nil }
        let suffix = ".apps.googleusercontent.com"
        let prefix: String
        if clean.hasSuffix(suffix) {
            prefix = String(clean.dropLast(suffix.count))
        } else {
            prefix = clean
        }
        let scheme = "com.googleusercontent.apps.\(prefix)"
        return (scheme, "\(scheme):/oauth2redirect")
    }

    func authorize(
        clientId: String,
        tokenStorage: IosSecureTokenStorage,
        clock: AppClock
    ) async -> (Bool, String?) {
        guard let redirect = Self.computeGoogleRedirectUri(clientId: clientId) else {
            return (false, "Geçerli bir Google iOS OAuth Client ID girin.")
        }

        let pkce = SharedIosSyncBridge.shared.createPkceSession(
            clientId: clientId,
            redirectUri: redirect.redirectUri
        )
        guard pkce.success, let authURL = URL(string: pkce.authorizationUrl) else {
            return (false, pkce.errorMessage ?? "OAuth oturumu başlatılamadı.")
        }

        let callbackResult: Result<URL, Error> = await withCheckedContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: redirect.scheme
            ) { url, error in
                if let url {
                    continuation.resume(returning: .success(url))
                } else {
                    continuation.resume(returning: .failure(error ?? NSError(domain: "PureNoteOAuth", code: -1)))
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.activeSession = session
            if !session.start() {
                continuation.resume(returning: .failure(NSError(domain: "PureNoteOAuth", code: -2)))
            }
        }
        self.activeSession = nil

        switch callbackResult {
        case .failure(let err):
            let nsErr = err as NSError
            if nsErr.code == ASWebAuthenticationSessionError.canceledLogin.rawValue {
                return (false, "Google Drive girişi iptal edildi.")
            }
            return (false, "Google yetkilendirmesi tamamlanamadı: \(err.localizedDescription)")
        case .success(let callbackUrl):
            do {
                let res = try await SharedIosSyncBridge.shared.completePkceCallback(
                    clientId: clientId,
                    callbackUrl: callbackUrl.absoluteString,
                    expectedState: pkce.state,
                    expectedPath: "/oauth2redirect",
                    codeVerifier: pkce.codeVerifier,
                    redirectUri: pkce.redirectUri,
                    tokenStorage: tokenStorage,
                    clock: clock
                )
                return (res.success, res.errorMessage)
            } catch {
                return (false, error.localizedDescription)
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}

/// Manages 6-digit Master PIN (PBKDF2-HMAC-SHA256 in Apple Keychain), brute-force cooldown,
/// Face ID / Touch ID (`LAContext`), and auto-lock timeout parity with Android `SecurityUtil`.
@MainActor
final class IosVaultSecurityManager: ObservableObject {
    @Published var isPinConfigured: Bool = false
    @Published var isVaultUnlocked: Bool = false
    @Published var isBiometricEnabled: Bool = false
    @Published var cooldownSeconds: Int = 0
    @Published var autoLockMinutes: Int = 5

    private static let keyPinHash = "vault_pin_hash_v2"
    private static let keyPinSalt = "vault_pin_salt_v2"
    private static let keyFailedAttempts = "vault_failed_attempts_v1"
    private static let keyLockoutUntilMs = "vault_lockout_until_ms_v1"
    private static let keyBiometricEnabled = "purenote_biometric_enabled"
    private static let keyAutoLockMinutes = "purenote_auto_lock_minutes"

    private let cryptoBridge = AppleCryptoBridge()
    private var cooldownTimer: Timer?
    private var lastActivityDate = Date()

    init() {
        refreshState()
    }

    func refreshState() {
        isPinConfigured = IosKeychainHelper.readString(account: Self.keyPinHash) != nil
        isBiometricEnabled = UserDefaults.standard.bool(forKey: Self.keyBiometricEnabled)
        let savedAutoLock = UserDefaults.standard.object(forKey: Self.keyAutoLockMinutes) as? Int
        autoLockMinutes = savedAutoLock ?? 5
        if !isPinConfigured {
            isVaultUnlocked = false
        }
        updateCooldownRemaining()
    }

    func recordActivity() {
        lastActivityDate = Date()
    }

    func checkAutoLockIfNeeded() {
        guard isPinConfigured, isVaultUnlocked, autoLockMinutes > 0 else { return }
        if Date().timeIntervalSince(lastActivityDate) >= Double(autoLockMinutes * 60) {
            lockVault()
        }
    }

    func lockVault() {
        isVaultUnlocked = false
    }

    func setAutoLockMinutes(_ minutes: Int) {
        autoLockMinutes = minutes
        UserDefaults.standard.set(minutes, forKey: Self.keyAutoLockMinutes)
        recordActivity()
    }

    func setBiometricEnabled(_ enabled: Bool) {
        isBiometricEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: Self.keyBiometricEnabled)
    }

    func setOrUpdatePin(currentPin: String?, newPin: String) -> (Bool, String?) {
        guard newPin.count == 6, newPin.allSatisfy({ $0.isNumber }) else {
            return (false, "PIN tam olarak 6 rakamdan oluşmalıdır.")
        }
        if isPinConfigured {
            guard let cur = currentPin, verifyPinInternal(cur) else {
                return (false, "Mevcut PIN hatalı.")
            }
        }
        var saltBytes = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, saltBytes.count, &saltBytes) == errSecSuccess else {
            return (false, "Rastgele tuz üretilemedi.")
        }
        let saltData = Data(saltBytes)
        guard let hashHex = derivePinHashHex(pin: newPin, salt: saltData) else {
            return (false, "PIN şifrelenemedi.")
        }
        IosKeychainHelper.saveString(saltData.base64EncodedString(), account: Self.keyPinSalt)
        IosKeychainHelper.saveString(hashHex, account: Self.keyPinHash)
        IosKeychainHelper.saveString("0", account: Self.keyFailedAttempts)
        IosKeychainHelper.saveString("0", account: Self.keyLockoutUntilMs)
        isPinConfigured = true
        isVaultUnlocked = true
        recordActivity()
        return (true, nil)
    }

    func removePin(currentPin: String) -> (Bool, String?) {
        guard verifyPinInternal(currentPin) else {
            return (false, "Mevcut PIN hatalı.")
        }
        IosKeychainHelper.deleteString(account: Self.keyPinHash)
        IosKeychainHelper.deleteString(account: Self.keyPinSalt)
        IosKeychainHelper.deleteString(account: Self.keyFailedAttempts)
        IosKeychainHelper.deleteString(account: Self.keyLockoutUntilMs)
        isPinConfigured = false
        isVaultUnlocked = false
        setBiometricEnabled(false)
        return (true, nil)
    }

    func unlockWithPin(_ pin: String) -> (Bool, String?) {
        updateCooldownRemaining()
        if cooldownSeconds > 0 {
            return (false, "Çok fazla hatalı deneme. \(cooldownSeconds) sn bekleyin.")
        }
        if verifyPinInternal(pin) {
            IosKeychainHelper.saveString("0", account: Self.keyFailedAttempts)
            IosKeychainHelper.saveString("0", account: Self.keyLockoutUntilMs)
            isVaultUnlocked = true
            recordActivity()
            return (true, nil)
        }
        let attempts = (Int(IosKeychainHelper.readString(account: Self.keyFailedAttempts) ?? "0") ?? 0) + 1
        IosKeychainHelper.saveString("\(attempts)", account: Self.keyFailedAttempts)
        if attempts >= 5 {
            let lockSeconds: Int
            switch attempts {
            case 5...6: lockSeconds = 30
            case 7...9: lockSeconds = 60
            case 10...14: lockSeconds = 300
            default: lockSeconds = 900
            }
            let untilMs = Int64(Date().timeIntervalSince1970 * 1000) + Int64(lockSeconds * 1000)
            IosKeychainHelper.saveString("\(untilMs)", account: Self.keyLockoutUntilMs)
            updateCooldownRemaining()
            return (false, "Hatalı PIN. Güvenlik nedeniyle \(lockSeconds) sn beklenecek.")
        }
        return (false, "PIN hatalı (\(attempts)/5).")
    }

    func canUseBiometrics() -> Bool {
        let context = LAContext()
        var err: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &err)
    }

    func unlockWithBiometrics(reason: String) async -> (Bool, String?) {
        let context = LAContext()
        context.localizedCancelTitle = "PIN Kullan"
        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError) else {
            return (false, authError?.localizedDescription ?? "Biyometrik doğrulama kullanılamıyor.")
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
            if ok {
                isVaultUnlocked = true
                recordActivity()
                return (true, nil)
            }
            return (false, "Biyometrik doğrulama başarısız.")
        } catch {
            return (false, error.localizedDescription)
        }
    }

    private func verifyPinInternal(_ pin: String) -> Bool {
        guard let saltB64 = IosKeychainHelper.readString(account: Self.keyPinSalt),
              let saltData = Data(base64Encoded: saltB64),
              let expectedHash = IosKeychainHelper.readString(account: Self.keyPinHash),
              let actualHash = derivePinHashHex(pin: pin, salt: saltData) else {
            return false
        }
        return constantTimeEqual(expectedHash, actualHash)
    }

    private func derivePinHashHex(pin: String, salt: Data) -> String? {
        guard let derived = cryptoBridge.derivePbkdf2HmacSha256(
            passphrase: pin,
            salt: salt.toKotlinByteArray(),
            iterations: 60_000,
            keyByteLength: 32
        ) else {
            return nil
        }
        return derived.toData().map { String(format: "%02x", $0) }.joined()
    }

    private func constantTimeEqual(_ a: String, _ b: String) -> Bool {
        let ab = Array(a.utf8)
        let bb = Array(b.utf8)
        guard ab.count == bb.count else { return false }
        var diff: UInt8 = 0
        for i in 0..<ab.count {
            diff |= ab[i] ^ bb[i]
        }
        return diff == 0
    }

    private func updateCooldownRemaining() {
        let untilMs = Int64(IosKeychainHelper.readString(account: Self.keyLockoutUntilMs) ?? "0") ?? 0
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let rem = max(0, Int((untilMs - nowMs + 999) / 1000))
        cooldownSeconds = rem
        if rem > 0, cooldownTimer == nil {
            cooldownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
                Task { @MainActor in
                    self?.updateCooldownRemaining()
                }
            }
        } else if rem == 0 {
            cooldownTimer?.invalidate()
            cooldownTimer = nil
        }
    }
}

extension KotlinByteArray {
    func toData() -> Data {
        let count = Int(size)
        if count <= 0 { return Data() }
        var bytes = [UInt8](repeating: 0, count: count)
        for idx in 0..<count {
            bytes[idx] = UInt8(bitPattern: get(index: Int32(idx)))
        }
        return Data(bytes)
    }
}

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let kba = KotlinByteArray(size: Int32(count))
        for (idx, byte) in enumerated() {
            kba.set(index: Int32(idx), value: Int8(bitPattern: byte))
        }
        return kba
    }
}

extension KotlinCharArray {
    func toSwiftString() -> String {
        let count = Int(size)
        if count <= 0 { return "" }
        var scalars: [UnicodeScalar] = []
        scalars.reserveCapacity(count)
        for idx in 0..<count {
            let codeUnit = get(index: Int32(idx))
            if let scalar = UnicodeScalar(codeUnit) {
                scalars.append(scalar)
            }
        }
        return String(String.UnicodeScalarView(scalars))
    }
}
