package com.example.roboticgit.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Material Design 3 Motion specifications
 * https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 */

// Material Design 3 Emphasized Easing (using CubicBezierEasing for performance)
val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

// Duration constants
const val MotionDurationShort = 200
const val MotionDurationMedium = 400
const val MotionDurationLong = 500

// Pre-cached animation specs for reuse
val ContainerTransformSpec = tween<Float>(MotionDurationLong, easing = EmphasizedEasing)

// Pre-cached transition objects to avoid creation on each navigation
object ScreenTransitions {
    // Forward navigation: slide in from right with fade
    val enter: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth / 4 },
        animationSpec = tween(MotionDurationMedium, easing = EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(MotionDurationShort, easing = EmphasizedEasing)
    )

    // Forward navigation: fade out current screen
    val exit: ExitTransition = fadeOut(
        animationSpec = tween(MotionDurationShort / 2, easing = EmphasizedEasing)
    )

    // Back navigation: fade in previous screen
    val popEnter: EnterTransition = fadeIn(
        animationSpec = tween(MotionDurationShort, delayMillis = MotionDurationShort / 2, easing = EmphasizedEasing)
    )

    // Back navigation: slide out to right with fade
    val popExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth / 4 },
        animationSpec = tween(MotionDurationMedium, easing = EmphasizedEasing)
    ) + fadeOut(
        animationSpec = tween(MotionDurationShort, easing = EmphasizedEasing)
    )

    // Crossfade for horizontal tab navigation
    val fadeEnter: EnterTransition = fadeIn(
        tween(MotionDurationMedium - 100, easing = EmphasizedEasing)
    )

    val fadeExit: ExitTransition = fadeOut(
        tween(MotionDurationShort, easing = EmphasizedEasing)
    )
}

/**
 * Material Design 3 Lateral Transition Pattern
 * Used for peer-to-peer navigation (tabs, chips, etc.)
 * https://m3.material.io/styles/motion/transitions/transition-patterns#3d3d5310-bce9-4930-98ad-8b74c4c50573
 */
object LateralTransition {
    private const val Duration = 300
    private const val SlideOffset = 30 // percentage of width

    /**
     * Creates enter transition for lateral navigation
     * @param forward true if moving to higher index, false if moving to lower index
     */
    fun enter(forward: Boolean): EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth ->
            if (forward) fullWidth * SlideOffset / 100 else -fullWidth * SlideOffset / 100
        },
        animationSpec = tween(Duration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(
        animationSpec = tween(Duration / 2, delayMillis = Duration / 6, easing = EmphasizedDecelerateEasing)
    )

    /**
     * Creates exit transition for lateral navigation
     * @param forward true if moving to higher index, false if moving to lower index
     */
    fun exit(forward: Boolean): ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth ->
            if (forward) -fullWidth * SlideOffset / 100 else fullWidth * SlideOffset / 100
        },
        animationSpec = tween(Duration, easing = EmphasizedEasing)
    ) + fadeOut(
        animationSpec = tween(Duration / 3, easing = EmphasizedEasing)
    )
}
