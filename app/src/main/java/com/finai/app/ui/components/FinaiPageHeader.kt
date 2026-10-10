package com.finai.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.finai.app.R
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * Cabeçalho global, playbook "Cabeçalho 4a" (`uploads/design-header/finai-header-playbook.pdf`).
 *
 * Um componente só para as quatro abas (muda o título) e para as telas internas (seta de
 * voltar no lugar do símbolo, ações opcionais). Fica **por cima** do conteúdo: a lista
 * rola por baixo dele, começando em [PageHeaderSpec.contentTop].
 *
 * Dois estados, decididos pela rolagem da lista da tela aberta:
 * - no topo: fundo transparente, sem linha, título grande visível no conteúdo;
 * - rolado (> 8 dp): vidro (#09090B 72%, desfoque 20 dp, saturação 160%), linha branca 5%
 *   e o título compacto no centro. O vidro liga e desliga em 300 ms.
 *
 * O título grande ([PageTitle]) é o primeiro item da lista e o compacto mora aqui; os dois
 * leem o mesmo progresso [PageHeaderMotion.progress], que acompanha o dedo sem duração
 * própria. Progresso e opacidade são lidos só na fase de desenho (`graphicsLayer`), então
 * rolar não recompõe nada; só cruzar os 8 dp recompõe o cabeçalho, uma vez.
 */
object PageHeaderSpec {
    val Height = 56.dp
    val StartPadding = 18.dp
    val EndPadding = 14.dp
    val ActionGap = 6.dp
    val LogoSize = 30.dp
    val LogoRadius = 10.dp
    val ButtonVisual = 40.dp
    val ButtonTouch = 48.dp
    val IconSize = 21.dp
    val AssistantIconSize = 18.dp
    val AssistantGlow = 18.dp
    val DotSize = 8.dp
    val DotRing = 2.dp
    /** Folga mínima entre o título compacto e o que está ao lado dele. */
    val TitleClearance = 8.dp
    /** Espaço entre a linha do cabeçalho e o início do conteúdo. */
    val ContentGap = 6.dp

    val GlassBlur = 20.dp
    const val GlassSaturation = 1.6f
    const val GlassAlpha = 0.72f
    const val FallbackAlpha = 0.96f
    const val GlassFadeMillis = 300

    val BigTitleSize = 32.sp
    val BigTitleLineHeight = 38.sp
    val BigTitleTracking = (-0.04).em
    val CompactTitleSize = 16.sp

    /** Onde o conteúdo começa: status bar + 56 dp + 6 dp. */
    val contentTop: Dp
        @Composable get() = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + Height + ContentGap
}

/**
 * Contas do playbook, em dp, sem Compose: testadas em `PageHeaderMotionTest`.
 * `p = clamp((rolagem − 10) / 30, 0, 1)`; o vidro liga acima de 8 dp.
 */
object PageHeaderMotion {
    const val GLASS_AFTER_DP = 8f
    const val TITLE_START_DP = 10f
    const val TITLE_RANGE_DP = 30f
    const val BIG_TITLE_SHIFT_DP = -8f
    const val COMPACT_TITLE_SHIFT_DP = 8f

    fun progress(scrollDp: Float): Float = ((scrollDp - TITLE_START_DP) / TITLE_RANGE_DP).coerceIn(0f, 1f)

    fun isScrolled(scrollDp: Float): Boolean = scrollDp > GLASS_AFTER_DP

    fun bigTitleAlpha(p: Float) = 1f - p
    fun bigTitleShiftDp(p: Float) = BIG_TITLE_SHIFT_DP * p
    fun compactTitleAlpha(p: Float) = p
    fun compactTitleShiftDp(p: Float) = COMPACT_TITLE_SHIFT_DP * (1f - p)
}

/**
 * Quanto a lista rolou, em px, para o cabeçalho. Só o primeiro item (o título grande) tem
 * posição contínua; passou dele, a transição já terminou e vale "muito rolado".
 */
internal fun LazyListState.headerScrollPx(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/** Progresso p da transição dos títulos, lido como estado (use só em desenho/layout). */
@Composable
fun rememberHeaderProgress(listState: LazyListState?): State<Float> {
    val density = LocalDensity.current.density
    return remember(listState, density) {
        derivedStateOf { listState?.let { PageHeaderMotion.progress(it.headerScrollPx() / density) } ?: 0f }
    }
}

/** O que fica à esquerda: o símbolo nas abas, a seta de voltar nas telas internas. */
@Immutable
sealed interface HeaderLeading {
    data object Logo : HeaderLeading
    data class Back(val onBack: () -> Unit) : HeaderLeading
}

/** Ações da direita. `null` em [FinaiPageHeader] esconde as três. */
@Immutable
data class HeaderActions(
    val unreadCount: Int,
    val onOpenNotifications: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAssistant: () -> Unit,
    /** Centro do sino em px da janela: o painel de avisos aponta a seta para ele. */
    val onBellCenterX: (Float) -> Unit = {},
)

private object HeaderColors {
    val glass get() = if (FinaiColors.isDark) Color(0xFF09090B) else FinaiColors.Background
    val divider get() = if (FinaiColors.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.06f)
    val icon get() = if (FinaiColors.isDark) Color(0xFFD4D4D8) else FinaiColors.TextBody
    val iconPressed get() = if (FinaiColors.isDark) Color(0xFFFAFAFA) else FinaiColors.TextPrimary
    val pressedFill get() = if (FinaiColors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val dot = Color(0xFFF43F5E)
    val assistantLight = Color(0xFF34D399)
    val assistantDark = Color(0xFF047857)
    val assistantGlow = Color(0xFF10B981).copy(alpha = 0.35f)
}

/**
 * O cabeçalho. [listState] é a lista da tela aberta (null = tela sem lista ligada, fica no
 * estado do topo). [backdrop] é a fonte do vidro, a mesma da barra de baixo; sem ela, ou
 * com [solid], o fundo rolado é a cor sólida de fallback. [solid] também deixa o cabeçalho
 * sempre no estado rolado, para telas internas sem título grande.
 */
@Composable
fun FinaiPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: HeaderLeading = HeaderLeading.Logo,
    actions: HeaderActions? = null,
    listState: LazyListState? = null,
    backdrop: GlassBackdrop? = null,
    solid: Boolean = false,
    subtitle: String? = null,
) {
    val density = LocalDensity.current.density
    val reducedMotion = rememberReducedMotion()
    val progress = rememberHeaderProgress(listState)
    val scrolled by remember(listState, density, solid) {
        derivedStateOf { solid || (listState != null && PageHeaderMotion.isScrolled(listState.headerScrollPx() / density)) }
    }
    val glassAlpha = animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = if (reducedMotion || solid) snap() else tween(PageHeaderSpec.GlassFadeMillis),
        label = "headerGlass",
    )
    // Só registra a superfície na fonte enquanto o vidro aparece: no topo, nenhum quadro
    // de rolagem paga pelo desfoque do cabeçalho.
    val glassVisible by remember { derivedStateOf { glassAlpha.value > 0f } }
    val useBlur = backdrop != null && backdrop.isSupported && !solid
    val surface = rememberGlassSurface(if (useBlur && glassVisible) backdrop else null)
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth()) {
        // Fundo: vidro + linha inferior, cobrindo também a barra de status.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = glassAlpha.value }
                // Toque no vidro não atravessa para o card que rola por baixo dele.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .then(if (useBlur && glassVisible) Modifier.glassPosition(surface) else Modifier)
                .drawBehind {
                    if (glassAlpha.value <= 0f) return@drawBehind
                    val base = HeaderColors.glass
                    if (useBlur && backdrop != null) {
                        drawGlassRect(
                            backdrop = backdrop,
                            surface = surface,
                            blurRadius = PageHeaderSpec.GlassBlur,
                            saturation = PageHeaderSpec.GlassSaturation,
                            tint = base.copy(alpha = PageHeaderSpec.GlassAlpha),
                            fallbackTint = base.copy(alpha = PageHeaderSpec.FallbackAlpha),
                        )
                    } else {
                        drawRect(base.copy(alpha = PageHeaderSpec.FallbackAlpha))
                    }
                    val line = 1.dp.toPx()
                    drawRect(HeaderColors.divider, topLeft = Offset(0f, size.height - line), size = size.copy(height = line))
                },
        )

        HeaderRow(
            modifier = Modifier.statusBarsPadding().height(PageHeaderSpec.Height),
            leading = {
                when (leading) {
                    HeaderLeading.Logo -> Image(
                        painter = painterResource(R.drawable.finai_mark),
                        // Decorativo: o TalkBack lê o título da tela, não a marca.
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(PageHeaderSpec.LogoSize)
                            .clip(RoundedCornerShape(PageHeaderSpec.LogoRadius)),
                    )
                    is HeaderLeading.Back -> HeaderIconButton(onClick = leading.onBack, description = "Voltar") { tint ->
                        HeaderGlyph(HeaderGlyphs.Back, tint)
                    }
                }
            },
            leadingIsButton = leading is HeaderLeading.Back,
            title = {
                val compactModifier = Modifier
                    .graphicsLayer {
                        val p = if (solid) 1f else progress.value
                        alpha = PageHeaderMotion.compactTitleAlpha(p)
                        translationY = if (reducedMotion) 0f else PageHeaderMotion.compactTitleShiftDp(p) * density
                    }
                    .then(
                        // Escondido, o título compacto não existe para o TalkBack (o grande já
                        // é lido); visível, tocar volta a lista ao topo.
                        if (!scrolled) Modifier.clearAndSetSemantics {}
                        else if (listState != null && !solid) Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "Voltar ao topo",
                            ) {
                                scope.launch {
                                    if (reducedMotion) listState.scrollToItem(0) else listState.animateScrollToTop()
                                }
                            }
                        else Modifier,
                    )
                Column(compactModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        fontSize = PageHeaderSpec.CompactTitleSize,
                        fontWeight = FontWeight.Bold,
                        color = FinaiColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle, fontSize = 12.sp, color = FinaiColors.TextMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            actions = actions?.let { a ->
                {
                    HeaderIconButton(
                        onClick = a.onOpenNotifications,
                        description = when (a.unreadCount) {
                            0 -> "Notificações"
                            1 -> "Notificações, 1 não lida"
                            else -> "Notificações, ${a.unreadCount} não lidas"
                        },
                        modifier = Modifier.onGloballyPositioned { a.onBellCenterX(it.boundsInRoot().center.x) },
                    ) { tint ->
                        Box {
                            HeaderGlyph(HeaderGlyphs.Bell, tint)
                            if (a.unreadCount > 0) NotificationDot(Modifier.align(Alignment.TopEnd))
                        }
                    }
                    HeaderIconButton(onClick = a.onOpenSettings, description = "Configurações") { tint ->
                        HeaderGlyph(HeaderGlyphs.Settings, tint)
                    }
                    AssistantOrb(onClick = a.onOpenAssistant)
                }
            },
        )
    }
}

/**
 * Título grande do conteúdo: primeiro item da lista, rola junto. Some e sobe 8 dp
 * conforme o compacto aparece no cabeçalho. [trailing] fica à direita (ex.: "+ Novo").
 */
@Composable
fun PageTitle(
    title: String,
    listState: LazyListState?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val reducedMotion = rememberReducedMotion()
    val progress = rememberHeaderProgress(listState)
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = progress.value
                alpha = PageHeaderMotion.bigTitleAlpha(p)
                translationY = if (reducedMotion) 0f else PageHeaderMotion.bigTitleShiftDp(p) * density
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = PageHeaderSpec.BigTitleSize,
                lineHeight = PageHeaderSpec.BigTitleLineHeight,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = PageHeaderSpec.BigTitleTracking,
                color = FinaiColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle, fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

/** Volta a lista ao topo suavemente; de longe, salta para perto antes de animar. */
private suspend fun LazyListState.animateScrollToTop() {
    if (firstVisibleItemIndex > 12) scrollToItem(12)
    if (firstVisibleItemIndex > 0) animateScrollToItem(0) else animateScrollBy(-firstVisibleItemScrollOffset.toFloat())
}

/**
 * Linha de 56 dp: [leading] à esquerda, [actions] à direita e o título centralizado na
 * largura **total**, não no espaço livre. Quando o centro da tela empurraria o título para
 * cima dos botões (360 dp com as três ações, título longo), ele desliza só o necessário
 * para dentro do espaço livre; se nem assim couber, corta com reticências. Sempre com 8 dp
 * de folga do símbolo e das ações.
 *
 * Ações: botões de 48 dp de toque com visual de 40 dp, colocados de 46 em 46 dp para o
 * espaço visível entre eles ser os 6 dp do playbook (os toques se sobrepõem 2 dp).
 */
@Composable
private fun HeaderRow(
    modifier: Modifier,
    leading: @Composable () -> Unit,
    leadingIsButton: Boolean,
    title: @Composable () -> Unit,
    actions: (@Composable () -> Unit)?,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { leading() }
            Box { title() }
            Box { actions?.let { HeaderActionsRow(it) } }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val inset = ((PageHeaderSpec.ButtonTouch - PageHeaderSpec.ButtonVisual) / 2).roundToPx()
        val leadingPlaceable = measurables[0].measure(loose)
        val actionsPlaceable = measurables[2].measure(loose)
        val leadingX = PageHeaderSpec.StartPadding.roundToPx() - if (leadingIsButton) inset else 0
        val leadingEnd = leadingX + leadingPlaceable.width - if (leadingIsButton) inset else 0
        val actionsX = width - (PageHeaderSpec.EndPadding.roundToPx() - inset) - actionsPlaceable.width
        val actionsStart = if (actionsPlaceable.width > 0) actionsX + inset else width
        val clearance = PageHeaderSpec.TitleClearance.roundToPx()
        val freeStart = leadingEnd + clearance
        val freeEnd = actionsStart - clearance
        val titlePlaceable = measurables[1].measure(loose.copy(maxWidth = (freeEnd - freeStart).coerceAtLeast(0)))
        // Centro da tela sempre que cabe; senão desliza para dentro do espaço livre.
        val titleX = ((width - titlePlaceable.width) / 2)
            .coerceAtMost(freeEnd - titlePlaceable.width)
            .coerceAtLeast(freeStart)
        layout(width, height) {
            leadingPlaceable.placeRelative(leadingX, (height - leadingPlaceable.height) / 2)
            titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
            actionsPlaceable.placeRelative(actionsX, (height - actionsPlaceable.height) / 2)
        }
    }
}

@Composable
private fun HeaderActionsRow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val step = (PageHeaderSpec.ButtonVisual + PageHeaderSpec.ActionGap).roundToPx()
        val touch = PageHeaderSpec.ButtonTouch.roundToPx()
        val width = if (placeables.isEmpty()) 0 else step * (placeables.size - 1) + touch
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(i * step + (touch - p.width) / 2, (height - p.height) / 2) }
        }
    }
}

/**
 * Botão de ícone: sem fundo em repouso; pressionado, círculo branco 8% e ícone #FAFAFA.
 * Área de toque de 48 dp, visual de 40 dp.
 */
@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable (tint: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(PageHeaderSpec.ButtonTouch)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(PageHeaderSpec.ButtonVisual)
                .clip(CircleShape)
                .background(if (pressed) HeaderColors.pressedFill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            content(if (pressed) HeaderColors.iconPressed else HeaderColors.icon)
        }
    }
}

/** Ponto rosa de 8 dp com anel de 2 dp na cor do fundo. Sem número. */
@Composable
private fun NotificationDot(modifier: Modifier = Modifier) {
    val ring = FinaiColors.Background
    Box(
        modifier
            .padding(top = 1.dp, end = 1.dp)
            .size(PageHeaderSpec.DotSize + PageHeaderSpec.DotRing * 2)
            .drawBehind {
                drawCircle(ring)
                drawCircle(HeaderColors.dot, radius = PageHeaderSpec.DotSize.toPx() / 2f)
            },
    )
}

/**
 * Assistente: orbe de 40 dp com radial #34D399 → #047857 e brilho verde 35% de 18 dp em
 * volta. Pressionado encolhe para 0,92 e volta com mola (~200 ms).
 */
@Composable
private fun AssistantOrb(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed, reducedMotion) {
        val target = if (pressed) 0.92f else 1f
        if (reducedMotion) scale.snapTo(target)
        else scale.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 900f))
    }
    Box(
        modifier = Modifier
            .size(PageHeaderSpec.ButtonTouch)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Assistente" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(PageHeaderSpec.ButtonVisual)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .drawWithCache {
                    val r = size.minDimension / 2f
                    val glowR = r + PageHeaderSpec.AssistantGlow.toPx()
                    val glow = Brush.radialGradient(
                        0f to HeaderColors.assistantGlow,
                        (r / glowR) to HeaderColors.assistantGlow,
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height / 2f), radius = glowR,
                    )
                    // Luz no alto à esquerda, como no protótipo.
                    val orb = Brush.radialGradient(
                        listOf(HeaderColors.assistantLight, HeaderColors.assistantDark),
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = r * 1.6f,
                    )
                    onDrawBehind {
                        drawCircle(glow, radius = glowR)
                        drawCircle(orb, radius = r)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            HeaderGlyph(HeaderGlyphs.Sparkle, Color.White, size = PageHeaderSpec.AssistantIconSize, filled = true)
        }
    }
}

/** Traços do cabeçalho, no grid 24 com traço 2 (bell/settings: desenho do Lucide, ISC). */
private object HeaderGlyphs {
    val Bell = listOf(
        "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0",
    )
    val Settings = listOf(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
    )
    val Back = listOf("M12 19l-7-7 7-7", "M19 12H5")
    /** Estrela de quatro pontas do assistente, preenchida. */
    val Sparkle = listOf(
        "M12 3C12.6 8.4 15.6 11.4 21 12C15.6 12.6 12.6 15.6 12 21C11.4 15.6 8.4 12.6 3 12C8.4 11.4 11.4 8.4 12 3z",
    )
}

@Composable
private fun HeaderGlyph(
    paths: List<String>,
    tint: Color,
    size: Dp = PageHeaderSpec.IconSize,
    filled: Boolean = false,
) {
    val parsed: List<Path> = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val s = this.size.width / 24f
                scale(s, s, pivot = Offset.Zero) {
                    val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    for (p in parsed) {
                        if (filled) drawPath(p, tint) else drawPath(p, tint, style = stroke)
                    }
                }
            },
    )
}
