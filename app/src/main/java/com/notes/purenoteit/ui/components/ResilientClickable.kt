package com.notes.purenoteit.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Resilient Combined Clickable Modifier
 * HorizontalPager veya ScrollableNode'lar sayfa geçişinde ilk ACTION_DOWN olayını tüketse dahi
 * (consumed = true), requireUnconsumed = false sayesinde dokunmayı kaçırmaz ve ilk tıklamada
 * aksiyonu güvenli bir şekilde çalıştırır.
 */
@Composable
fun Modifier.resilientCombinedClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    if (!enabled) return this

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (currentOnLongClick != null) {
                // AwaitPointerEventScope.withTimeoutOrNull: zaman aşımında istisna fırlatmaz, null döner.
                // (Önceki sürümde TimeoutCancellationException yakalanıyordu; scope'un kendi
                // PointerEventTimeoutCancellationException'ı farklı tür olduğundan uzun basma hiç tetiklenmiyordu.)
                var finished = false
                var wasReleased = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    val up = waitForUpOrCancellation()
                    wasReleased = up != null
                    finished = true
                }
                if (finished) {
                    if (wasReleased) currentOnClick()
                } else {
                    currentOnLongClick?.invoke()
                    waitForUpOrCancellation()
                }
            } else {
                val up = waitForUpOrCancellation()
                if (up != null) {
                    currentOnClick()
                }
            }
        }
    }
}

/**
 * Resilient Clickable Modifier (Tek tıklamalı bileşenler için)
 */
@Composable
fun Modifier.resilientClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    if (!enabled) return this

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()
            if (up != null) {
                currentOnClick()
            }
        }
    }
}
