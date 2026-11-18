package com.notes.purenoteit.sync

enum class PureNotePlatform {
    ANDROID,
    DESKTOP_JVM,
    IOS
}

expect fun currentPlatformTarget(): PureNotePlatform

expect fun currentPlatformDisplayName(): String
