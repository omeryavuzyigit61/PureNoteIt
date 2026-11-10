package com.notes.purenoteit.security

internal expect fun generateSecureRandomBytes(byteCount: Int): ByteArray

internal expect fun sha256Bytes(input: ByteArray): ByteArray

internal expect fun constantTimeEquals(a: String, b: String): Boolean
