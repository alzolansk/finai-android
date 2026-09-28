package com.finai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finai.app.R

/**
 * Inter (OFL, `assets/licenses/Inter-OFL.txt`) em todas as telas. Até a Fase 7 só o
 * lançamento usava Inter e o resto caía no Roboto do sistema; agora a família mora aqui e o
 * `MaterialTheme` a aplica a todo `Text` (ele herda `LocalTextStyle`, que é o [FinaiTypography]
 * `bodyLarge`), inclusive aos que só trocam `fontSize`.
 */
val FinaiFontFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
    Font(R.font.inter_800, FontWeight.ExtraBold),
)

/**
 * Escala curta (planning.md §9 Fase 7, item 4): valor principal 32, título 19, subtítulo 15,
 * corpo 14, texto de apoio 13, legenda 12,5 e rótulo/navegação 12. Nenhum texto útil abaixo de
 * 12 sp — os antigos rótulos de 9,5–10 sp em caixa alta passaram para [labelSmall].
 */
val FinaiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.04).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.03).sp,
    ),
    /** Título de tela/seção principal. */
    headlineSmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.03).sp,
    ),
    /** Subtítulo: cabeçalho de seção e de cartão. */
    titleLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.02).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.02).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.01).sp,
    ),
    /** Corpo. */
    bodyLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** Texto de apoio. */
    bodyMedium = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    /** Legenda. */
    bodySmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    ),
    /** Botão. */
    labelLarge = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = (-0.01).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
    ),
    /** Rótulo em caixa alta e navegação — o menor tamanho do app. */
    labelSmall = TextStyle(
        fontFamily = FinaiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.04.sp,
    ),
)
