package com.finai.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Single source of truth for motion timing (durations/easing) across the
 * app — every overlay, dialog, list and tab switch reuses these instead of
 * inventing its own feel. Kept short and quiet: this is a polish pass, not a
 * redesign (nenhuma regra de negócio muda, só como as telas entram/saem).
 */
object FinaiMotion {
    /** Small, frequent state changes: color/scale on a press, a badge count. */
    const val Quick = 140
    /** Default for entering/exiting overlays, sheets and dialogs. */
    const val Standard = 220
    /** Reserved for the rare, larger transition (a full-screen entry flow). */
    const val Slow = 320
    val Easing = FastOutSlowInEasing
}

/**
 * Mirrors the system "Remove animations" developer option
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`). Compose's animation
 * APIs don't read that setting on their own, so every [finaiTween] collapses
 * to an instant [snap] when it's on, instead of a timed animation — the same
 * thing the rest of the OS already does for the user who turned it on.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }
}

/** [tween] using the app-wide duration/easing, or [snap] under reduced motion. */
@Composable
fun <T> finaiTween(durationMillis: Int = FinaiMotion.Standard): FiniteAnimationSpec<T> {
    val reduced = rememberReducedMotion()
    return if (reduced) snap() else tween(durationMillis, easing = FinaiMotion.Easing)
}
