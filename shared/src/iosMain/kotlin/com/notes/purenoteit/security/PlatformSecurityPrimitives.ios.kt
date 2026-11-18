package com.notes.purenoteit.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
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

internal actual fun constantTimeEquals(a: String, b: String): Boolean {
    val aBytes = a.encodeToByteArray()
    val bBytes = b.encodeToByteArray()
    if (aBytes.size != bBytes.size) return false
    var diff = 0
    for (i in aBytes.indices) {
        diff = diff or (aBytes[i].toInt() xor bBytes[i].toInt())
    }
    return diff == 0
}
