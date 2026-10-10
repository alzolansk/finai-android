package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.domain.GoalFlow
import com.finai.app.domain.formatMonthYearShort
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion
import java.time.LocalDate

/**
 * Registrar aporte (referência 1a): folha por cima da tela de Objetivos, com o fundo desfocado
 * (o desfoque é aplicado em `FinaiApp`). Valor por botões −/+, controle deslizante e atalhos; a
 * prévia diz para quanto a meta vai. Depois de guardar, a mesma folha vira a confirmação: o valor
 * sobe de R$ 0 até o aporte em 1 s e, na meta com alvo, a barra e o percentual enchem do valor
 * antigo ao novo no mesmo ritmo (ease-out cubic).
 */
@Composable
fun GoalContributionSheet(
    visible: Boolean,
    objetivo: ObjetivoEntity?,
    dailySpendingCents: Long?,
    onSave: (ObjetivoEntity, Long) -> Unit,
    onUndo: (ObjetivoEntity, Long) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Guarda o último objetivo: a lista do Room é reemitida depois de salvar, e a folha não pode
    // depender dela para continuar aberta nem desenhar vazia na saída.
    var shown by remember { mutableStateOf(objetivo) }
    if (objetivo != null) shown = objetivo
    // O que foi guardado agora: (meta antes do aporte, valor). Nulo enquanto está na entrada.
    var saved by remember { mutableStateOf<Pair<ObjetivoEntity, Long>?>(null) }
    // Ao fechar, a confirmação fica até a folha sair da tela; depois a próxima abertura começa na entrada.
    LaunchedEffect(visible) {
        if (!visible) { kotlinx.coroutines.delay(320); saved = null }
    }

    val close = { if (saved != null) onDone() else onDismiss() }
    BackHandler(enabled = visible) { close() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (FinaiColors.isDark) .55f else .38f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300, easing = EaseOutCubic)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
        ) {
            val goal = shown ?: return@AnimatedVisibility
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(GoalUi.Sheet)
                    .border(1.dp, GoalUi.Border, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 14.dp).size(38.dp, 4.dp).clip(CircleShape).background(GoalUi.Border))
                AnimatedContent(
                    targetState = saved,
                    // Sem recorte: o brilho verde do botão passa da borda do conteúdo.
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(260) })
                    },
                    label = "aporte",
                ) { result ->
                    if (result == null) {
                        key(goal.id) {
                            // Retrato da meta ao abrir: depois de guardar, o Room reemite a meta já
                            // somada, e a entrada (ainda saindo de cena) não pode redesenhar com ela.
                            val snapshot = remember { goal }
                            ContributionInput(snapshot, dailySpendingCents, onClose = onDismiss) { amount ->
                                saved = snapshot to amount
                                onSave(snapshot, amount)
                            }
                        }
                    } else {
                        ContributionSuccess(
                            before = result.first, amountCents = result.second, dailySpendingCents = dailySpendingCents,
                            onUndo = { onUndo(result.first, result.second) },
                            onDone = onDone,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributionInput(goal: ObjetivoEntity, dailySpendingCents: Long?, onClose: () -> Unit, onSave: (Long) -> Unit) {
    val today = LocalDate.now()
    val max = GoalFlow.contributionMaxCents(goal)
    var amount by rememberSaveable { mutableLongStateOf(GoalFlow.defaultContributionCents(goal, today)) }
    val preview = GoalFlow.preview(goal, amount, dailySpendingCents)

    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(GoalUi.EmeraldSoft),
                contentAlignment = Alignment.Center,
            ) {
                GoalGlyph(if (goal.tipo == "Reserva") "target" else goal.tipo, GoalUi.EmeraldText, size = 21)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(goal.nome, color = GoalUi.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    if (goal.isOpenReserve) "${brl(goal.valorGuardadoCentavos)} guardados · sem prazo"
                    else "${brl(goal.valorGuardadoCentavos)} de ${brl(goal.valorAlvoCentavos)} · até ${formatMonthYearShort(goal.prazo.toLocalDate())}",
                    color = GoalUi.Muted, fontSize = 12.5.sp, maxLines = 1,
                )
            }
            GoalRoundButton("close", "Fechar", onClose, filled = true, size = 34)
        }

        Text(
            "Quanto você está guardando?", color = GoalUi.Muted, fontSize = 13.5.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GoalRoundButton("minus", "Diminuir", {
                amount = (amount - GoalFlow.CONTRIBUTION_BUTTON_STEP_CENTS).coerceAtLeast(GoalFlow.CONTRIBUTION_STEP_CENTS)
            }, enabled = amount > GoalFlow.CONTRIBUTION_STEP_CENTS)
            Text(
                brl(amount), color = GoalUi.Ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
            )
            GoalRoundButton("plus", "Aumentar", {
                amount = (amount + GoalFlow.CONTRIBUTION_BUTTON_STEP_CENTS).coerceAtMost(max)
            }, enabled = amount < max)
        }

        GoalSlider(
            fraction = GoalFlow.fractionOf(amount, 0, max),
            onFraction = { f -> amount = GoalFlow.valueAt(f, 0, max, GoalFlow.CONTRIBUTION_STEP_CENTS).coerceAtLeast(GoalFlow.CONTRIBUTION_STEP_CENTS) },
            valueLabel = brl(amount), description = "Valor do aporte",
            modifier = Modifier.padding(top = 14.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("R$ 0", color = GoalUi.Muted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            Text(brl(max), color = GoalUi.Muted, fontSize = 11.5.sp)
        }

        val presets = GoalFlow.CONTRIBUTION_PRESETS.filter { it <= max }
        if (presets.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p -> GoalPresetChip(brl(p), selected = false, onClick = { amount = p }) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(14.dp))
                .background(GoalUi.EmeraldSoft.copy(alpha = if (FinaiColors.isDark) .55f else 1f))
                .border(1.dp, GoalUi.EmeraldLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(GoalUi.Emerald), contentAlignment = Alignment.Center) {
                GoalGlyph("check", GoalUi.OnEmerald, size = 13, stroke = 2.6f)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(preview.title, color = GoalUi.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(preview.detail, color = GoalUi.Muted, fontSize = 12.sp)
            }
        }

        GoalPrimaryButton("Guardar ${brl(amount)}", { onSave(amount) }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

@Composable
private fun ContributionSuccess(before: ObjetivoEntity, amountCents: Long, dailySpendingCents: Long?, onUndo: () -> Unit, onDone: () -> Unit) {
    val after = before.valorGuardadoCentavos + amountCents
    val reduced = rememberReducedMotion()
    // Um único relógio de 0 a 1 para a contagem, o percentual e a barra: os três andam juntos.
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            t.snapTo(1f)
        } else {
            // Começa depois que a confirmação termina de aparecer (fade de 60 + 220 ms): a contagem
            // de 1 s fica inteira à vista, de R$ 0 até o valor.
            kotlinx.coroutines.delay(280)
            t.animateTo(1f, tween(1000, easing = EaseOutCubic))
        }
    }
    val p = t.value

    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HaloCheck()
        Text(
            "+" + brl(Math.round(amountCents * p.toDouble())),
            color = GoalUi.EmeraldText, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp,
            modifier = Modifier.padding(top = 6.dp).semantics { contentDescription = "Mais ${brl(amountCents)}" },
        )
        Text("guardados em ${before.nome}", color = GoalUi.Muted, fontSize = 13.5.sp)

        if (!before.isOpenReserve) {
            val target = before.valorAlvoCentavos
            val fromF = (before.valorGuardadoCentavos.toFloat() / target).coerceIn(0f, 1f)
            val toF = (after.toFloat() / target).coerceIn(0f, 1f)
            val fromPct = GoalFlow.percent(before.valorGuardadoCentavos, target)
            val toPct = GoalFlow.percent(after, target)
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${(fromPct + (toPct - fromPct) * p).toInt()}%", color = GoalUi.Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "$toPct% da meta" },
                    )
                    Text("da meta de ${brl(target)}", color = GoalUi.Muted, fontSize = 12.sp)
                }
                GoalBar(fromF + (toF - fromF) * p, height = 8, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (before.isOpenReserve) {
                val days = GoalFlow.coverageDays(after, dailySpendingCents)
                StatBox("Total na reserva", brl(after), Modifier.weight(1f))
                StatBox("Cobertura", days?.let { "$it dias" } ?: "Sem gastos", Modifier.weight(1f))
            } else {
                val missing = before.valorAlvoCentavos - after
                StatBox("Total guardado", brl(after), Modifier.weight(1f))
                StatBox(if (missing > 0) "Falta" else "Meta", if (missing > 0) brl(missing) else "Alcançada", Modifier.weight(1f))
            }
        }

        Text(
            GoalFlow.successMessage(before, amountCents, LocalDate.now()),
            color = GoalUi.Ink.copy(alpha = .86f), fontSize = 13.5.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalSecondaryButton("Desfazer", onUndo)
            GoalPrimaryButton("Concluir", onDone, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(GoalUi.Inset).border(1.dp, GoalUi.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, color = GoalUi.Muted, fontSize = 11.5.sp)
        Text(value, color = GoalUi.Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}
