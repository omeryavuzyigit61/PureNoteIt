package com.notes.purenoteit.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            currentOnClick()
                        }
                    }
                } catch (e: TimeoutCancellationException) {
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
