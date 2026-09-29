package com.finai.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Design tokens extracted from the Claude Design prototype
 * (`project/FinAI Mobile.dc.html`) — see planning.md §5.
 *
 * Keep this file as the single source of truth for raw color values;
 * everything else (Theme.kt, component defaults) should reference these
 * instead of hard-coding hex again.
 *
 * Tema escuro (29/09/2026): cada token tem um valor claro e um escuro, e o getter devolve o do
 * tema ativo ([isDark]). [isDark] é estado do Compose, então quem lê um token durante a
 * composição (ou num `drawBehind`) é redesenhado quando o usuário troca o tema em
 * Configurações → Aparência — sem precisar trocar as centenas de `FinaiColors.X` do app.
 * Quem guarda uma cor fora da composição (enum, modelo montado no ViewModel) guarda a do tema
 * claro e a adapta na hora de desenhar com [themedText]/[themedFill].
 */
object FinaiColors {
    /** Escrito só por [FinaiTheme]; lido por todos os tokens abaixo. */
    var isDark by mutableStateOf(false)
        internal set

    private fun pick(light: Long, dark: Long) = Color(if (isDark) dark else light)

    // Surfaces
    val Background get() = pick(0xFFFAFAFA, 0xFF0E0E10)
    val Surface get() = pick(0xFFFFFFFF, 0xFF18181B)
    val SurfaceSunken get() = pick(0xFFFAFAFA, 0xFF131316)
    val SurfaceMuted get() = pick(0xFFF4F4F5, 0xFF26262A)

    // Dark surfaces (hero card, bottom nav island, chat "me" bubble). No escuro ficam um degrau
    // acima de Surface, senão o cartão preto some no fundo.
    val Ink get() = pick(0xFF18181B, 0xFF2A2A30)
    val InkBorder get() = pick(0xFF27272A, 0xFF3A3A42)

    /**
     * Destaque neutro com texto por cima: aba/dia/chip selecionado e botão principal. Preto no
     * claro; no escuro vira quase branco com texto escuro — o [Ink] escuro some ao lado das
     * superfícies escuras e deixaria o selecionado igual ao resto.
     */
    val InkStrong get() = pick(0xFF18181B, 0xFFF4F4F5)
    val OnInkStrong get() = pick(0xFFFFFFFF, 0xFF18181B)

    // Borders
    val BorderHairline get() = pick(0x0F000000, 0x17FFFFFF) // rgba(0,0,0,.06) / rgba(255,255,255,.09)
    val BorderSubtle get() = pick(0xFFE4E4E7, 0xFF34343A)
    val BorderFaint get() = pick(0xFFF4F4F5, 0xFF26262A)

    // Text — todo texto útil passa de 4,5:1 (WCAG AA) sobre Surface, Background e SurfaceMuted,
    // nos dois temas.
    val TextPrimary get() = pick(0xFF18181B, 0xFFF4F4F5)
    val TextBody get() = pick(0xFF27272A, 0xFFE4E4E7)
    /** Texto forte de selo/chip neutro (#3F3F46 no claro). */
    val TextStrong get() = pick(0xFF3F3F46, 0xFFD4D4D8)
    val TextSecondary get() = pick(0xFF52525B, 0xFFC4C4CC)
    val TextTertiary get() = pick(0xFF63636B, 0xFFA8A8B0) // 5,4:1 sobre #F4F4F5; 6,4:1 sobre #26262A
    val TextMuted get() = pick(0xFF6B6B73, 0xFF9A9AA3) // 4,8:1 sobre #F4F4F5; 5,4:1 sobre #26262A
    /** Valor vazio / tecla desabilitada — decorativo, não precisa de 4,5:1. */
    val TextDisabled get() = pick(0xFFD4D4D8, 0xFF52525B)
    val TextOnDarkFull = Color(0xFFFFFFFF)
    val TextOnDarkMuted = Color(0xB3FFFFFF) // rgba(255,255,255,.7-ish)
    val TextOnDarkFaint = Color(0x8AFFFFFF)

    // Brand — emerald
    val Emerald = Color(0xFF10B981)
    /**
     * Verde de texto: 5,5:1 sobre branco no claro; no escuro vira o verde claro (#34D399).
     * [Emerald] (2,5:1) fica só como acento sem texto em cima — barras de progresso, anel dos
     * dias. Fundo de botão com texto branco é [EmeraldButton], não este.
     */
    val EmeraldDark get() = pick(0xFF047857, 0xFF34D399)
    /** Fundo de botão/pílula com texto branco: o mesmo verde escuro nos dois temas. */
    val EmeraldButton = Color(0xFF047857)
    val EmeraldDeep get() = pick(0xFF065F46, 0xFF6EE7B7)
    val EmeraldSoftBg get() = pick(0xFFECFDF5, 0xFF0F2A22)
    val EmeraldSoftBorder get() = pick(0xFFD1FAE5, 0xFF134034)
    val EmeraldSoftStroke get() = pick(0xFFA7F3D0, 0xFF1F5A47)

    // Rose / danger
    val Rose = Color(0xFFF43F5E)
    val RoseDark get() = pick(0xFFBE123C, 0xFFFB7185) // texto de problema: 5,7:1 sobre #F4F4F5
    /** Fundo de botão/selo com texto branco: o mesmo vermelho nos dois temas. */
    val RoseButton = Color(0xFFBE123C)
    val RoseSoftBg get() = pick(0xFFFFF1F2, 0xFF3A1A20)
    val RoseSoftStroke get() = pick(0xFFFDA4AF, 0xFF9F2A41)
    val RoseDeep get() = pick(0xFF881337, 0xFFFDA4AF)
    val RoseDeepest get() = pick(0xFF4C0519, 0xFFFFE4E6)

    // Amber / warning
    val Amber = Color(0xFFF59E0B)
    val AmberDark get() = pick(0xFFB45309, 0xFFFBBF24)
    val AmberSoftBg get() = pick(0xFFFFFBEB, 0xFF33280F)
    val AmberDeep get() = pick(0xFF78350F, 0xFFFDE68A)

    // Indigo — negotiation / behavioral coach accents
    val Indigo get() = pick(0xFF4F46E5, 0xFFA5B4FC)
    val IndigoLight get() = pick(0xFF6366F1, 0xFF818CF8)
    val IndigoSoftBg get() = pick(0xFFEEF2FF, 0xFF1E1F3A)
    val IndigoSoftBorder get() = pick(0xFFE0E7FF, 0xFF2B2E5A)
    val IndigoDeepStart = Color(0xFF1E1B4B)
    val IndigoDeepEnd = Color(0xFF312E81)

    // Violet (import icon accent in prototype's negotiation card)
    val VioletAccent get() = pick(0xFF7C3AED, 0xFFC4B5FD)
}

/**
 * Pares de cor que existem só no tema claro — guardados em enum ou em modelo montado fora da
 * composição (`data/model/FinaiModels.kt`, `domain/UiMappers.kt`, ícones de categoria). No
 * escuro, cada tom escuro de texto vira o claro do mesmo matiz e cada fundo pastel vira um
 * fundo escuro do mesmo matiz. Tom sem correspondência é clareado/escurecido por mistura.
 */
private val DarkText: Map<Color, Color> = mapOf(
    Color(0xFF047857) to Color(0xFF34D399),
    Color(0xFF065F46) to Color(0xFF6EE7B7),
    Color(0xFFB45309) to Color(0xFFFBBF24),
    Color(0xFF92400E) to Color(0xFFFCD34D),
    Color(0xFF78350F) to Color(0xFFFDE68A),
    Color(0xFFBE123C) to Color(0xFFFB7185),
    Color(0xFF881337) to Color(0xFFFDA4AF),
    Color(0xFF18181B) to Color(0xFFF4F4F5),
    Color(0xFF27272A) to Color(0xFFE4E4E7),
    Color(0xFF3F3F46) to Color(0xFFD4D4D8),
    Color(0xFF52525B) to Color(0xFFC4C4CC),
    Color(0xFF63636B) to Color(0xFFA8A8B0),
    Color(0xFF6B6B73) to Color(0xFF9A9AA3),
    Color(0xFF4F46E5) to Color(0xFFA5B4FC),
    Color(0xFF4338CA) to Color(0xFFA5B4FC),
    Color(0xFF7C3AED) to Color(0xFFC4B5FD),
    Color(0xFFA16207) to Color(0xFFFDE047),
    Color(0xFF1D4ED8) to Color(0xFF93C5FD),
    Color(0xFFC2410C) to Color(0xFFFDBA74),
    Color(0xFF0F766E) to Color(0xFF5EEAD4),
    Color(0xFF0E7490) to Color(0xFF67E8F9),
    Color(0xFF4D7C0F) to Color(0xFFBEF264),
    Color(0xFFBE185D) to Color(0xFFF9A8D4),
    Color(0xFF334155) to Color(0xFFCBD5E1),
    Color(0xFF0284C7) to Color(0xFF7DD3FC),
    Color(0xFFD97706) to Color(0xFFFCD34D),
)

private val DarkFill: Map<Color, Color> = mapOf(
    Color(0xFFFFFFFF) to Color(0xFF18181B),
    Color(0xFFFAFAFA) to Color(0xFF131316),
    Color(0xFFF4F4F5) to Color(0xFF26262A),
    Color(0xFFE4E4E7) to Color(0xFF34343A),
    Color(0xFFECFDF5) to Color(0xFF0F2A22),
    Color(0xFFD1FAE5) to Color(0xFF134034),
    Color(0xFFA7F3D0) to Color(0xFF1F5A47),
    Color(0xFFFFF1F2) to Color(0xFF3A1A20),
    Color(0xFFFFE4E6) to Color(0xFF4A1D26),
    Color(0xFFFDA4AF) to Color(0xFF9F2A41),
    Color(0xFFFFFBEB) to Color(0xFF33280F),
    Color(0xFFFEF3C7) to Color(0xFF3D2F0C),
    Color(0xFFEEF2FF) to Color(0xFF1E1F3A),
    Color(0xFFE0E7FF) to Color(0xFF2B2E5A),
    Color(0xFFF5F3FF) to Color(0xFF261E3A),
    Color(0xFFFEFCE8) to Color(0xFF2E2A10),
    Color(0xFFEFF6FF) to Color(0xFF172338),
    Color(0xFFFFF7ED) to Color(0xFF33200F),
    Color(0xFFF0FDFA) to Color(0xFF0F2A27),
    Color(0xFFECFEFF) to Color(0xFF0E2A30),
    Color(0xFFF7FEE7) to Color(0xFF1F2A10),
    Color(0xFFFDF2F8) to Color(0xFF34172A),
    Color(0xFFF1F5F9) to Color(0xFF1E242C),
    Color(0xFFE0F2FE) to Color(0xFF0C2A3D),
)

/** Cor de texto/ícone pensada para fundo claro, adaptada ao tema ativo. */
val Color.themedText: Color
    get() = if (!FinaiColors.isDark) this else DarkText[this] ?: lerp(this, Color.White, 0.55f)

/** Cor de fundo pastel pensada para o tema claro, adaptada ao tema ativo. */
val Color.themedFill: Color
    get() = if (!FinaiColors.isDark) this else DarkFill[this] ?: lerp(this, Color(0xFF18181B), 0.82f)
