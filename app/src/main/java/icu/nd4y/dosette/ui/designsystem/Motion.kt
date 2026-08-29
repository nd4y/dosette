@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package icu.nd4y.dosette.ui.designsystem

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset

/**
 * Bridges the theme's Expressive motion tokens to feature screens so the
 * ExperimentalMaterial3ExpressiveApi opt-in stays inside designsystem.
 */
@Composable
fun <T> spatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

@Composable
fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

@Composable
fun <T> effectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

/**
 * Directional slide+fade for ordered containers (tabs, wizard steps,
 * calendar months): content slides towards the navigation direction.
 * Resolved outside of transitionSpec lambdas because motion tokens are
 * composable reads.
 */
@Immutable
class DirectionalMotion internal constructor(
    private val offset: FiniteAnimationSpec<IntOffset>,
    private val fade: FiniteAnimationSpec<Float>,
) {
    fun transform(forward: Boolean): ContentTransform {
        val direction = if (forward) 1 else -1
        return (
            slideInHorizontally(offset) { fullWidth -> fullWidth / SLIDE_FRACTION * direction } +
                fadeIn(fade)
        ) togetherWith
            (
                slideOutHorizontally(offset) { fullWidth -> -fullWidth / SLIDE_FRACTION * direction } +
                    fadeOut(fade)
            )
    }

    private companion object {
        const val SLIDE_FRACTION = 4
    }
}

@Composable
fun rememberDirectionalMotion(): DirectionalMotion {
    val offset = fastSpatialSpec<IntOffset>()
    val fade = effectsSpec<Float>()
    return remember(offset, fade) { DirectionalMotion(offset, fade) }
}
