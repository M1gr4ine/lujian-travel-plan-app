@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.lujian.travelplan.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class PlanSharedTransitionScopes(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

object PlanNoteTransitionPolicy {
    fun useSharedBounds(fromRoute: String?, reduceMotion: Boolean): Boolean =
        fromRoute == "library" && !reduceMotion
}

private val PlanSharedBoundsEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)

@Composable
fun Modifier.planSharedBounds(
    planId: Long,
    scopes: PlanSharedTransitionScopes?,
    enabled: Boolean,
): Modifier {
    if (!enabled || scopes == null) return this
    return with(scopes.shared) {
        this@planSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "plan-note-$planId"),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = { _, _ -> tween(durationMillis = 280, easing = PlanSharedBoundsEasing) },
            enter = fadeIn(tween(durationMillis = 180, delayMillis = 55, easing = PlanSharedBoundsEasing)),
            exit = fadeOut(tween(durationMillis = 120, easing = PlanSharedBoundsEasing)),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}
