package com.notes.purenoteit.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

internal actual fun constantTimeEqualsBytes(a: ByteArray, b: ByteArray): Boolean =
    MessageDigest.isEqual(a, b)

internal actual fun pbkdf2HmacSha256(
    password: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyBitLength: Int
): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password, salt, iterations, keyBitLength)
    return try {
        factory.generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}
