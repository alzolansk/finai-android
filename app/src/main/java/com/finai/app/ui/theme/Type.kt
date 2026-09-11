package com.finai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The prototype uses Inter throughout. We render with the platform default
 * sans-serif (Roboto) for now to avoid bundling font files in Phase 0 — sizes,
 * weights and letter-spacing are matched so layouts read the same. Swapping in
 * a real Inter FontFamily later (a .ttf under res/font) is a drop-in change here only.
 */
private val FinaiFontFamily = FontFamily.Default

val FinaiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        letterSpacing = (-0.04).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.03).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.03).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.02).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = (-0.02).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = (-0.01).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = (-0.01).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.5.sp,
        letterSpacing = 0.07.sp,
    ),
)
