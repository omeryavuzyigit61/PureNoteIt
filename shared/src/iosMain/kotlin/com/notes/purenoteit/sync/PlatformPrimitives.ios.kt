package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.sha256Bytes
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import platform.Foundation.NSISO8601DateFormatWithFractionalSeconds
import platform.Foundation.NSISO8601DateFormatWithInternetDateTime
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

internal actual fun sharedRandomUuid(): String = NSUUID().UUIDString().lowercase()

internal actual fun sha256Hex(bytes: ByteArray): String {
    val digest = sha256Bytes(bytes)
    val hexChars = "0123456789abcdef"
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xFF
        sb.append(hexChars[v ushr 4])
        sb.append(hexChars[v and 0x0F])
    }
    return sb.toString()
}

internal actual fun parseIso8601ToMillis(isoTimestamp: String?): Long {
    if (isoTimestamp.isNullOrBlank()) return 0L
    val trimmed = isoTimestamp.trim()
    val withFractional = NSISO8601DateFormatter().apply {
        formatOptions = NSISO8601DateFormatWithInternetDateTime or NSISO8601DateFormatWithFractionalSeconds
    }
    val withoutFractional = NSISO8601DateFormatter().apply {
        formatOptions = NSISO8601DateFormatWithInternetDateTime
    }
    val date = withFractional.dateFromString(trimmed) ?: withoutFractional.dateFromString(trimmed)
    return if (date != null) (date.timeIntervalSince1970 * 1000.0).toLong() else 0L
}

internal actual fun classifyNetworkThrowable(throwable: Throwable): DataError.Network {
    var cur: Throwable? = throwable
    while (cur != null) {
        when (cur) {
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException -> return DataError.Network.TIMEOUT
            is SerializationException -> return DataError.Network.SERIALIZATION
        }
        val msg = cur.message.orEmpty().lowercase()
        when {
            msg.contains("timed out") || msg.contains("timeout") -> return DataError.Network.TIMEOUT
            msg.contains("ssl") || msg.contains("tls") || msg.contains("certificate") -> return DataError.Network.TLS_ERROR
            msg.contains("offline") || msg.contains("could not connect") || msg.contains("network") ->
                return DataError.Network.NO_CONNECTION
        }
        cur = cur.cause
    }
    return DataError.Network.UNKNOWN
}
