package com.finai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.finai.app.navigation.FinaiDestination
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Medidas, curvas e durações da barra de navegação 3a
 * (`uploads/design-nav/finai-nav-playbook.pdf`). Mudar aqui, não nas telas.
 */
object FinaiNavBarSpec {
    val SideMargin = 12.dp
    val BottomGap = 16.dp
    val ExpandedHeight = 64.dp
    val CollapsedHeight = 52.dp
    const val CollapsedScale = 0.94f
    val HorizontalPadding = 6.dp
    val Gap = 4.dp
    val PlusWidth = 56.dp
    /** Botão +: altura − 14 dp. */
    val PlusInset = 7.dp
    /** Indicador: altura − 10 dp. */
    val IndicatorInset = 5.dp
    val BottomScrimHeight = 130.dp
    /** Rodapé das listas que rolam por baixo da barra (somar o inset de navegação). */
    val ContentBottomPadding = 140.dp

    const val IndicatorSlideDamping = 0.6f
    const val IndicatorSlideStiffness = 380f
    const val StretchMillis = 280
    /** O esticar dura 280 ms, mas começa a voltar aos 240 ms; assenta junto com a mola (~550 ms). */
    const val StretchHoldMillis = 240L
    const val StretchReleaseMillis = 310
    const val StretchScaleX = 1.22f
    const val StretchScaleY = 0.86f
    const val ColorMillis = 250
    const val CollapseMillis = 400
    const val LabelSizeMillis = 350
    const val LabelFadeMillis = 250
    const val PressMillis = 200
    const val PressedScale = 0.92f

    /** "suave" (.2, .8, .2, 1) do playbook. */
    val Smooth = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    /** "mola" (.34, 1.45, .55, 1): passa um pouco do alvo e volta. */
    val Springy = CubicBezierEasing(0.34f, 1.45f, 0.55f, 1f)

    val Inactive = Color(0xFFA1A1AA)
    val Active = Color(0xFFFAFAFA)
    val IndicatorFill = Color.White.copy(alpha = 0.13f)
    val IndicatorHighlight = Color.White.copy(alpha = 0.22f)
}

private class NavTabSpec(val destination: FinaiDestination, val label: String, val icon: ImageVector)

private val NavTabs = listOf(
    NavTabSpec(FinaiDestination.Home, "Início", Icons.Filled.Home),
    NavTabSpec(FinaiDestination.Agenda, "Agenda", Icons.Outlined.CalendarToday),
    NavTabSpec(FinaiDestination.Goals, "Objetivos", Icons.Outlined.Adjust),
    NavTabSpec(FinaiDestination.Debts, "Dívidas", Icons.Outlined.CreditCard),
)

/**
 * Barra flutuante de vidro (playbook 3a): Início / Agenda / (+) / Objetivos / Dívidas.
 * Limites e Importar continuam sendo abertas de dentro das telas. Nenhuma aba fica
 * marcada nelas, e o indicador some até voltar para uma aba.
 *
 * Desenhe com [Modifier.align] na base da tela; a barra já soma o inset da navegação do
 * sistema e os 16 dp do playbook. [backdrop] é o mesmo passado para [glassSource] no
 * conteúdo das telas.
 *
 * Desempenho: altura, escala e posição do indicador são lidas só nas fases de layout e
 * desenho (lambdas), então as animações de recolher e de troca de aba não recompõem a
 * barra a cada quadro.
 */
@Composable
fun FinaiBottomNav(
    current: FinaiDestination,
    addOpen: Boolean,
    collapsed: Boolean,
    backdrop: GlassBackdrop,
    onSelect: (FinaiDestination) -> Unit,
    onReselect: () -> Unit,
    onToggleAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val progress = animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.CollapseMillis, easing = FinaiNavBarSpec.Smooth),
        label = "navCollapse",
    )
    val selectedIndex = NavTabs.indexOfFirst { it.destination == current }
    val surface = rememberGlassSurface(backdrop)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FinaiNavBarSpec.SideMargin)
            .padding(bottom = FinaiNavBarSpec.BottomGap + navBottom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val fullWidth = maxWidth
        val density = LocalDensity.current
        val fullWidthPx = with(density) { fullWidth.roundToPx() }

        Box(
            modifier = Modifier
                .glassPosition(surface)
                .drawBehind { drawGlass(backdrop, surface, GlassMaterial.Nav) }
                // O overshoot da mola nas abas das pontas não pode vazar para fora da pílula.
                // A sombra fica de fora do recorte (desenhada antes).
                .clip(RoundedCornerShape(percent = 50))
                .layout { measurable, _ ->
                    // A barra encolhe de verdade (não só por graphicsLayer) para o vidro
                    // ser desenhado no tamanho real e alinhado com o conteúdo de trás.
                    val p = progress.value
                    val scale = lerp(1f, FinaiNavBarSpec.CollapsedScale, p)
                    val innerHeight = lerp(FinaiNavBarSpec.ExpandedHeight, FinaiNavBarSpec.CollapsedHeight, p).roundToPx()
                    val inner = measurable.measure(Constraints.fixed(fullWidthPx, innerHeight))
                    val barWidth = (fullWidthPx * scale).roundToInt()
                    val barHeight = (innerHeight * scale).roundToInt()
                    layout(barWidth, barHeight) {
                        inner.placeWithLayer((barWidth - fullWidthPx) / 2, (barHeight - innerHeight) / 2) {
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                },
        ) {
            NavBarContent(
                progress = progress,
                fullWidthPx = fullWidthPx,
                selectedIndex = selectedIndex,
                expanded = !collapsed,
                addOpen = addOpen,
                reduced = reduced,
                onTab = { index ->
                    if (index == selectedIndex) onReselect() else onSelect(NavTabs[index].destination)
                },
                onToggleAdd = onToggleAdd,
            )
        }
    }
}

/**
 * Faixa de proteção na base (130 dp, cor do fundo a 85% → 0) para o vidro não brigar
 * com o conteúdo. Vai *dentro* da área marcada com [glassSource], como no protótipo:
 * o vidro desfoca o degradê junto com o conteúdo.
 */
@Composable
fun NavBarBottomScrim(visible: Boolean, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.CollapseMillis, easing = FinaiNavBarSpec.Smooth),
        label = "navScrim",
    )
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val background = FinaiColors.Background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FinaiNavBarSpec.BottomScrimHeight + navBottom)
            .graphicsLayer { this.alpha = alpha.value }
            .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background.copy(alpha = 0.85f)))),
    )
}

@Composable
private fun NavBarContent(
    progress: State<Float>,
    fullWidthPx: Int,
    selectedIndex: Int,
    expanded: Boolean,
    addOpen: Boolean,
    reduced: Boolean,
    onTab: (Int) -> Unit,
    onToggleAdd: () -> Unit,
) {
    val density = LocalDensity.current
    val padPx = with(density) { FinaiNavBarSpec.HorizontalPadding.toPx() }
    val gapPx = with(density) { FinaiNavBarSpec.Gap.toPx() }
    val plusPx = with(density) { FinaiNavBarSpec.PlusWidth.toPx() }
    // Aba = (L − 12 − 56 − 16) ÷ 4.
    val tabPx = ((fullWidthPx - padPx * 2 - plusPx - gapPx * 4) / 4f).coerceAtLeast(0f)
    fun slotX(index: Int): Float = padPx + index * (tabPx + gapPx) + if (index >= 2) plusPx + gapPx else 0f

    val indicatorX = remember { Animatable(Float.NaN) }
    val stretch = remember { Animatable(0f) }
    val target = rememberUpdatedState(if (selectedIndex >= 0) slotX(selectedIndex) else null)
    val reducedState = rememberUpdatedState(reduced)
    LaunchedEffect(indicatorX, stretch) {
        var stretchJob: Job? = null
        // Um coletor só, sem reiniciar o efeito a cada toque: o animateTo novo cancela o
        // anterior pelo mutex do Animatable e herda a velocidade atual. Toques rápidos
        // seguidos fazem a mola partir de onde o indicador está, sem pulo.
        snapshotFlow { target.value }.collect { x ->
            if (x == null) return@collect
            if (indicatorX.value.isNaN() || reducedState.value) {
                stretchJob?.cancel()
                stretch.snapTo(0f)
                indicatorX.snapTo(x)
                return@collect
            }
            if (x == indicatorX.targetValue && !indicatorX.isRunning) return@collect
            launch {
                indicatorX.animateTo(
                    x,
                    spring(FinaiNavBarSpec.IndicatorSlideDamping, FinaiNavBarSpec.IndicatorSlideStiffness),
                )
            }
            stretchJob?.cancel()
            stretchJob = launch {
                launch { stretch.animateTo(1f, tween(FinaiNavBarSpec.StretchMillis, easing = FinaiNavBarSpec.Smooth)) }
                delay(FinaiNavBarSpec.StretchHoldMillis)
                stretch.animateTo(0f, tween(FinaiNavBarSpec.StretchReleaseMillis, easing = FinaiNavBarSpec.Smooth))
            }
        }
    }
    val indicatorAlpha = animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.ColorMillis),
        label = "navIndicatorAlpha",
    )
    val indicatorSurface = rememberGlassSurface()

    Layout(
        modifier = Modifier.selectableGroup(),
        content = {
            // 0: indicador (atrás das abas).
            Box(
                Modifier
                    .graphicsLayer {
                        val s = stretch.value
                        scaleX = lerp(1f, FinaiNavBarSpec.StretchScaleX, s)
                        scaleY = lerp(1f, FinaiNavBarSpec.StretchScaleY, s)
                        alpha = indicatorAlpha.value
                    }
                    .drawBehind {
                        val pill = indicatorSurface.pillPath(size.width, size.height)
                        drawPath(pill, FinaiNavBarSpec.IndicatorFill)
                        drawGlassEdges(indicatorSurface, size.width, size.height, Color.Transparent, FinaiNavBarSpec.IndicatorHighlight)
                    },
            )
            // 1–4: abas; 5: botão +.
            NavTabs.forEachIndexed { index, tab ->
                NavTabItem(tab, selected = index == selectedIndex, expanded = expanded, reduced = reduced, narrow = tabPx < with(density) { 60.dp.toPx() }) {
                    onTab(index)
                }
            }
            PlusButton(addOpen = addOpen, reduced = reduced, onClick = onToggleAdd)
        },
    ) { measurables, constraints ->
        val height = constraints.maxHeight
        val tabW = tabPx.roundToInt()
        val indicatorInset = FinaiNavBarSpec.IndicatorInset.roundToPx()
        val indicator = measurables[0].measure(Constraints.fixed(tabW, (height - indicatorInset * 2).coerceAtLeast(0)))
        val tabs = (1..4).map { measurables[it].measure(Constraints.fixed(tabW, height)) }
        val plus = measurables[5].measure(Constraints.fixed(plusPx.roundToInt(), height))
        layout(constraints.maxWidth, height) {
            // A posição do indicador é lida aqui (fase de posicionamento): o
            // deslizamento só reposiciona, não remede nem recompõe nada.
            val x = indicatorX.value.takeUnless { it.isNaN() } ?: target.value ?: slotX(0)
            indicator.place(x.roundToInt(), indicatorInset)
            tabs.forEachIndexed { index, tab -> tab.place(slotX(index).roundToInt(), 0) }
            plus.place((padPx + 2 * (tabPx + gapPx)).roundToInt(), 0)
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavTabSpec,
    selected: Boolean,
    expanded: Boolean,
    reduced: Boolean,
    narrow: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) FinaiNavBarSpec.PressedScale else 1f,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.PressMillis, easing = FinaiNavBarSpec.Smooth),
        label = "navTabPress",
    )
    val color by animateColorAsState(
        targetValue = if (selected) FinaiNavBarSpec.Active else FinaiNavBarSpec.Inactive,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.ColorMillis),
        label = "navTabColor",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Sem ripple (playbook): o retorno do toque é a escala de 0,92.
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            // O rótulo some quando a barra recolhe; o TalkBack continua lendo o nome.
            .clearAndSetSemantics {
                contentDescription = tab.label
                role = Role.Tab
                this.selected = selected
            }
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(if (reduced) 0 else FinaiNavBarSpec.LabelSizeMillis, easing = FinaiNavBarSpec.Smooth)) +
                fadeIn(tween(if (reduced) 0 else FinaiNavBarSpec.LabelFadeMillis, easing = FinaiNavBarSpec.Smooth)),
            exit = shrinkVertically(tween(if (reduced) 0 else FinaiNavBarSpec.LabelSizeMillis, easing = FinaiNavBarSpec.Smooth)) +
                fadeOut(tween(if (reduced) 0 else FinaiNavBarSpec.LabelFadeMillis, easing = FinaiNavBarSpec.Smooth)),
        ) {
            Text(
                tab.label,
                color = color,
                fontSize = if (narrow) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun PlusButton(addOpen: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) FinaiNavBarSpec.PressedScale else 1f,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.PressMillis, easing = FinaiNavBarSpec.Springy),
        label = "navPlusPress",
    )
    val rotation = animateFloatAsState(
        targetValue = if (addOpen) 45f else 0f,
        animationSpec = if (reduced) snap() else tween(FinaiNavBarSpec.PressMillis, easing = FinaiNavBarSpec.Springy),
        label = "navPlusRotation",
    )
    // A área de toque é a altura inteira da barra; a pílula verde (56 × altura − 14) fica dentro.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tipTarget("nav.add")
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = "Ações rápidas"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = FinaiNavBarSpec.PlusInset)
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(FinaiColors.EmeraldButton),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation.value },
            )
        }
    }
}
