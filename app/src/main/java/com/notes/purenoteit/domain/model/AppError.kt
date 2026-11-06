package com.notes.purenoteit.domain.model

sealed interface AppError

sealed interface DataError : AppError {
    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        VALIDATION_FAILED,
        UNKNOWN
    }
    enum class Security : DataError {
        UNAUTHORIZED,
        DECRYPTION_FAILED,
        KEYSTORE_UNAVAILABLE
    }
}
