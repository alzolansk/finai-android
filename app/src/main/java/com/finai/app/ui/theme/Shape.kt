package com.finai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii lifted straight from the prototype's inline `border-radius` values. */
object FinaiRadius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 14.dp
    val lg = 16.dp
    val xl = 18.dp
    val xxl = 20.dp
    val xxxl = 22.dp
    val huge = 24.dp
    val sheet = 28.dp
    val pill = 999.dp
}

val FinaiShapes = Shapes(
    extraSmall = RoundedCornerShape(FinaiRadius.xs),
    small = RoundedCornerShape(FinaiRadius.sm),
    medium = RoundedCornerShape(FinaiRadius.lg),
    large = RoundedCornerShape(FinaiRadius.xxl),
    extraLarge = RoundedCornerShape(FinaiRadius.huge),
)
