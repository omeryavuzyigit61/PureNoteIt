package com.notes.purenoteit.ui.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resmi Jetpack Compose Tip Güvenli (@Serializable) Navigasyon Rotaları.
 * String tabanlı kırılgan rota şablonları yerine derleme zamanı tip güvenliği sağlar;
 * hassas veri veya parola kesinlikle rota argümanı olarak taşınmaz.
 */
sealed interface AppRoute {

    @Serializable
    @SerialName("home")
    data object Home : AppRoute

    @Serializable
    @SerialName("note_detail")
    data class NoteDetail(
        @SerialName("noteId") val noteId: Int = -1,
        @SerialName("initialDate") val initialDate: Long = -1L,
        @SerialName("autoVoice") val autoVoice: Boolean = false,
        @SerialName("autoOcr") val autoOcr: Boolean = false,
        @SerialName("initialStatus") val initialStatus: String = "TODO",
        @SerialName("originScope") val originScope: String = "none"
    ) : AppRoute

    @Serializable
    @SerialName("category_settings")
    data object CategorySettings : AppRoute

    @Serializable
    @SerialName("password_settings")
    data object PasswordSettings : AppRoute
}
