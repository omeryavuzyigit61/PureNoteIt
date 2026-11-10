package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.DataError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.net.ssl.SSLException

internal actual fun sharedRandomUuid(): String = UUID.randomUUID().toString()

internal actual fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

internal actual fun parseIso8601ToMillis(isoTimestamp: String?): Long {
    if (isoTimestamp.isNullOrBlank()) return 0L
    val normalized = isoTimestamp.trim()
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX"
    )
    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(normalized)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {
        }
    }
    return 0L
}

internal actual fun classifyNetworkThrowable(throwable: Throwable): DataError.Network {
    var cur: Throwable? = throwable
    while (cur != null) {
        when (cur) {
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException,
            is java.net.SocketTimeoutException -> return DataError.Network.TIMEOUT
            is SSLException -> return DataError.Network.TLS_ERROR
            is UnknownHostException -> return DataError.Network.NO_CONNECTION
            is SerializationException -> return DataError.Network.SERIALIZATION
            is IOException -> return DataError.Network.NO_CONNECTION
        }
        cur = cur.cause
    }
    return DataError.Network.UNKNOWN
}
