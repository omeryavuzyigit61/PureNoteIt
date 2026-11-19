package com.notes.purenoteit.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun generateSecureRandomBytes(byteCount: Int): ByteArray {
    if (byteCount <= 0) return ByteArray(0)
    val out = UByteArray(byteCount)
    val status = out.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, byteCount.convert(), pinned.addressOf(0))
    }
    check(status == errSecSuccess) { "SecRandomCopyBytes failed with status $status" }
    return out.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256Bytes(input: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { outPinned ->
        if (input.isEmpty()) {
            CC_SHA256(null, 0u, outPinned.addressOf(0))
        } else {
            input.asUByteArray().usePinned { inPinned ->
                CC_SHA256(inPinned.addressOf(0), input.size.convert(), outPinned.addressOf(0))
            }
        }
    }
    return digest.toByteArray()
}

internal actual fun constantTimeEquals(a: String, b: String): Boolean =
    constantTimeEqualsBytes(a.encodeToByteArray(), b.encodeToByteArray())

internal actual fun constantTimeEqualsBytes(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
    }
    return diff == 0
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pbkdf2HmacSha256(
    password: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyBitLength: Int
): ByteArray {
    require(iterations > 0) { "PBKDF2 iterations must be positive" }
    require(keyBitLength > 0 && keyBitLength % 8 == 0) { "PBKDF2 keyBitLength must be a positive multiple of 8" }
    val keyByteLength = keyBitLength / 8
    val passwordBytes = password.concatToString().encodeToByteArray()
    val derived = UByteArray(keyByteLength)
    return try {
        val status = derived.usePinned { outPinned ->
            if (passwordBytes.isEmpty()) {
                if (salt.isEmpty()) {
                    CCKeyDerivationPBKDF(
                        kCCPBKDF2,
                        "",
                        0u,
                        null,
                        0u,
                        kCCPRFHmacAlgSHA256,
                        iterations.toUInt(),
                        outPinned.addressOf(0),
                        keyByteLength.convert()
                    )
                } else {
                    salt.asUByteArray().usePinned { saltPinned ->
                        CCKeyDerivationPBKDF(
                            kCCPBKDF2,
                            "",
                            0u,
                            saltPinned.addressOf(0),
                            salt.size.convert(),
                            kCCPRFHmacAlgSHA256,
                            iterations.toUInt(),
                            outPinned.addressOf(0),
                            keyByteLength.convert()
                        )
                    }
                }
            } else {
                passwordBytes.usePinned { passPinned ->
                    if (salt.isEmpty()) {
                        CCKeyDerivationPBKDF(
                            kCCPBKDF2,
                            passPinned.addressOf(0),
                            passwordBytes.size.convert(),
                            null,
                            0u,
                            kCCPRFHmacAlgSHA256,
                            iterations.toUInt(),
                            outPinned.addressOf(0),
                            keyByteLength.convert()
                        )
                    } else {
                        salt.asUByteArray().usePinned { saltPinned ->
                            CCKeyDerivationPBKDF(
                                kCCPBKDF2,
                                passPinned.addressOf(0),
                                passwordBytes.size.convert(),
                                saltPinned.addressOf(0),
                                salt.size.convert(),
                                kCCPRFHmacAlgSHA256,
                                iterations.toUInt(),
                                outPinned.addressOf(0),
                                keyByteLength.convert()
                            )
                        }
                    }
                }
            }
        }
        check(status == kCCSuccess) { "CCKeyDerivationPBKDF failed with status $status" }
        derived.toByteArray()
    } finally {
        passwordBytes.fill(0)
    }
}
