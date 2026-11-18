package com.notes.purenoteit.security

internal expect fun generateSecureRandomBytes(byteCount: Int): ByteArray

internal expect fun sha256Bytes(input: ByteArray): ByteArray

internal expect fun constantTimeEquals(a: String, b: String): Boolean

internal expect fun constantTimeEqualsBytes(a: ByteArray, b: ByteArray): Boolean

internal expect fun pbkdf2HmacSha256(
    password: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyBitLength: Int = 256
): ByteArray
