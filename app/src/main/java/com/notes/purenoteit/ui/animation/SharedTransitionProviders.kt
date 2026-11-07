package com.notes.purenoteit.ui.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Coordinates [SharedTransitionScope] overlay and bounds interpolation with [androidx.compose.foundation.pager.HorizontalPager].
 * When the user swipes the pager before a return transition settles, shared bounds immediately release
 * their root overlay and snap to their clipped page slot so cards never bleed or overlap across pages.
 */
@Stable
class PagerTransitionCoordinator {
    var isPagerMoving by mutableStateOf(false)
        private set

    var wasInterruptedDuringTransition by mutableStateOf(false)
        private set

    val suppressOverlay: Boolean
        get() = isPagerMoving || wasInterruptedDuringTransition

    fun updatePagerMovement(moving: Boolean, isTransitionActive: Boolean) {
        if (isPagerMoving != moving) {
            isPagerMoving = moving
        }
        if (moving && isTransitionActive) {
            if (!wasInterruptedDuringTransition) {
                wasInterruptedDuringTransition = true
            }
        } else if (!isTransitionActive && !moving && wasInterruptedDuringTransition) {
            wasInterruptedDuringTransition = false
        }
    }

    fun reset() {
        isPagerMoving = false
        wasInterruptedDuringTransition = false
    }
}

/**
 * CompositionLocal providing [SharedTransitionScope] to the Compose hierarchy.
 * Allows decoupling shared bounds animations without prop-drilling across all UI screens.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * CompositionLocal providing [AnimatedVisibilityScope] from the active navigation destination.
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * CompositionLocal providing [PagerTransitionCoordinator] across HomeScreen and NoteDetailScreen.
 */
val LocalPagerTransitionCoordinator = compositionLocalOf { PagerTransitionCoordinator() }
