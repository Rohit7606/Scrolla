package com.scrolla.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object ScrollaMotion {
    /**
     * Feedback: Confirms a localized user action (e.g., hover, press, focus).
     * Near-instant. Must feel perfectly responsive without drawing attention to itself.
     */
    fun <T> feedback(): AnimationSpec<T> = tween(durationMillis = 100)

    /**
     * Continuity: Explains a structural change (e.g., a card expanding, list reordering).
     * Smooth and deliberate. No bouncy overshoot.
     */
    fun <T> continuity(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /**
     * Attention: Introduces a significant interruption (e.g., dialogs, modal sheets).
     * Decelerated. Enters with authority and settles into place to command focus.
     */
    fun <T> attention(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
