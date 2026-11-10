package com.notes.purenoteit.security

import java.security.MessageDigest
import java.security.SecureRandom

private val threadSafeRandom = SecureRandom()

internal actual fun generateSecureRandomBytes(byteCount: Int): ByteArray {
    val bytes = ByteArray(byteCount)
    threadSafeRandom.nextBytes(bytes)
    return bytes
}

internal actual fun sha256Bytes(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)

internal actual fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
