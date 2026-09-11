package com.finai.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens extracted from the Claude Design prototype
 * (`project/FinAI Mobile.dc.html`) — see planning.md §5.
 *
 * Keep this file as the single source of truth for raw color values;
 * everything else (Theme.kt, component defaults) should reference these
 * instead of hard-coding hex again.
 */
object FinaiColors {
    // Surfaces
    val Background = Color(0xFFFAFAFA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSunken = Color(0xFFFAFAFA)
    val SurfaceMuted = Color(0xFFF4F4F5)

    // Dark surfaces (hero card, bottom nav island, chat "me" bubble)
    val Ink = Color(0xFF18181B)
    val InkBorder = Color(0xFF27272A)

    // Borders
    val BorderHairline = Color(0x0F000000) // rgba(0,0,0,.06)
    val BorderSubtle = Color(0xFFE4E4E7)
    val BorderFaint = Color(0xFFF4F4F5)

    // Text
    val TextPrimary = Color(0xFF18181B)
    val TextBody = Color(0xFF27272A)
    val TextSecondary = Color(0xFF52525B)
    val TextTertiary = Color(0xFF71717A)
    val TextMuted = Color(0xFFA1A1AA)
    val TextOnDarkFull = Color(0xFFFFFFFF)
    val TextOnDarkMuted = Color(0xB3FFFFFF) // rgba(255,255,255,.7-ish)
    val TextOnDarkFaint = Color(0x8AFFFFFF)

    // Brand — emerald
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF059669)
    val EmeraldDeep = Color(0xFF065F46)
    val EmeraldSoftBg = Color(0xFFECFDF5)
    val EmeraldSoftBorder = Color(0xFFD1FAE5)

    // Rose / danger
    val Rose = Color(0xFFF43F5E)
    val RoseDark = Color(0xFFE11D48)
    val RoseSoftBg = Color(0xFFFFF1F2)
    val RoseDeep = Color(0xFF881337)
    val RoseDeepest = Color(0xFF4C0519)

    // Amber / warning
    val Amber = Color(0xFFF59E0B)
    val AmberDark = Color(0xFFB45309)
    val AmberSoftBg = Color(0xFFFFFBEB)
    val AmberDeep = Color(0xFF78350F)

    // Indigo — negotiation / behavioral coach accents
    val Indigo = Color(0xFF4F46E5)
    val IndigoLight = Color(0xFF6366F1)
    val IndigoSoftBg = Color(0xFFEEF2FF)
    val IndigoSoftBorder = Color(0xFFE0E7FF)
    val IndigoDeepStart = Color(0xFF1E1B4B)
    val IndigoDeepEnd = Color(0xFF312E81)

    // Violet (import icon accent in prototype's negotiation card)
    val VioletAccent = Color(0xFF7C3AED)
}
