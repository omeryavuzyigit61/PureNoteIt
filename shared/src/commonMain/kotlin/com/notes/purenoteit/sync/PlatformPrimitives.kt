package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.DataError

internal expect fun sharedRandomUuid(): String

internal expect fun sha256Hex(bytes: ByteArray): String

internal expect fun parseIso8601ToMillis(isoTimestamp: String?): Long

internal expect fun classifyNetworkThrowable(throwable: Throwable): DataError.Network
