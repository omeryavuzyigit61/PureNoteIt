package com.notes.purenoteit.domain.time

fun interface AppClock {
    fun nowMillis(): Long
}
