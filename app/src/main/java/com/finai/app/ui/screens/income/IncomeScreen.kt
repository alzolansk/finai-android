package com.finai.app.ui.screens.income

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.AdvanceRule
import com.finai.app.domain.IncomePart
import com.finai.app.domain.IncomePlanner
import com.finai.app.domain.IncomeSplit
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.incomePart
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.ActionButton
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.FinaiSettingsTopBar
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion
import com.finai.app.util.formatBrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun brl(cents: Long) = formatBrl(cents / 100.0)

/**
 * Minha renda (ref. 1a): o salário líquido e como ele cai — integral ou em duas partes, com o
 * adiantamento em % ou em valor fixo. Mostra as entradas que isso gera na Agenda. Salvar troca
 * só as entradas ainda não efetivadas ([IncomePlanner.regenerate]).
 */
@Composable
fun IncomeScreen(
    visible: Boolean,
    config: IncomePlanner.Config?,
    onBack: () -> Unit,
    onSave: suspend (IncomeSplit?) -> Unit,
    onAddIncome: () -> Unit,
) {
    BackHandler(visible) { onBack() }
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(if (reduced) 0 else 300, easing = FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(if (reduced) 0 else 240, easing = FastOutSlowInEasing)) { it },
    ) {
        Column(Modifier.fillMaxSize().background(FinaiColors.Background)) {
            FinaiSettingsTopBar(title = "Minha renda", onNavigateBack = onBack)
            if (config == null) {
                Column(Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        "Nenhum salário recorrente cadastrado. Lance o salário como receita recorrente para escolher como você recebe.",
                        "Lançar salário", onAddIncome,
                    )
                }
            } else IncomeContent(config, onSave)
        }
    }
}

@Composable
private fun IncomeContent(config: IncomePlanner.Config, onSave: suspend (IncomeSplit?) -> Unit) {
    val key = "${config.template.id}-${config.split?.rule?.encode()}"
    val initial = config.split?.rule
    var split by rememberSaveable(key) { mutableStateOf(config.split != null) }
    var percentMode by rememberSaveable(key) { mutableStateOf(initial !is AdvanceRule.Fixed) }
    var pct by rememberSaveable(key) { mutableIntStateOf((initial as? AdvanceRule.Percent)?.pct ?: 40) }
    var fixed by rememberSaveable(key) { mutableIntStateOf(((initial as? AdvanceRule.Fixed)?.cents ?: 200_000L).toInt()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val total = config.totalCents
    val rule: AdvanceRule = if (percentMode) AdvanceRule.Percent(pct) else AdvanceRule.Fixed(fixed.toLong())
    val chosen = if (split) IncomeSplit(rule) else null
    val advance = chosen?.advanceCents(total) ?: 0L
    val advancePct = if (total > 0) Math.round(advance * 100.0 / total).toInt() else 0
    val entries = remember(config, split, rule) {
        IncomePlanner.generate(config.template, total, chosen, config.nextCompetence, config.template.data.toLocalDate().dayOfMonth, "preview")
    }
    val competence = config.nextCompetence
    val competenceLabel = "${MONTH_NAMES_PT[competence.monthValue - 1].lowercase()} de ${competence.year}"
    val shortMonth = MONTH_ABBREV_PT[competence.monthValue - 1].lowercase()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IncomeCard {
            Text("SALÁRIO LÍQUIDO MENSAL", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
            Text(brl(total), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 6.dp))
            Text(config.descricao + config.conta.takeIf { it.isNotBlank() }?.let { " · conta $it" }.orEmpty(), fontSize = 12.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 4.dp))
        }

        IncomeCard {
            Text("Como você recebe", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Segmented(listOf("Integral" to false, "Em duas partes" to true), split, { split = it; message = null }, Modifier.padding(top = 10.dp))
            if (split) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Adiantamento", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary, modifier = Modifier.weight(1f))
                    Segmented(listOf("%" to true, "Valor fixo" to false), percentMode, { percentMode = it; message = null }, small = true)
                }
                Text(
                    brl(advance) + if (percentMode) " · $pct%" else "",
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 8.dp),
                )
                val min = if (percentMode) IncomeSplit.MIN_PCT else IncomeSplit.MIN_FIXED_CENTS.toInt()
                val max = if (percentMode) IncomeSplit.MAX_PCT else IncomeSplit.MAX_FIXED_CENTS.toInt()
                val step = if (percentMode) IncomeSplit.PCT_STEP else IncomeSplit.FIXED_STEP_CENTS.toInt()
                Slider(
                    value = (if (percentMode) pct else fixed).coerceIn(min, max).toFloat(),
                    onValueChange = { v ->
                        message = null
                        // Segue o passo do controle, mesmo que o valor guardado venha de fora dele.
                        val snapped = min + Math.round((v - min) / step) * step
                        if (percentMode) pct = snapped else fixed = snapped
                    },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = (max - min) / step - 1,
                    colors = SliderDefaults.colors(thumbColor = FinaiColors.Emerald, activeTrackColor = FinaiColors.Emerald),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(advancePct.coerceIn(1, 99).toFloat()).height(10.dp).background(FinaiColors.EmeraldSoftStroke))
                    Box(Modifier.weight((100 - advancePct).coerceIn(1, 99).toFloat()).height(10.dp).background(FinaiColors.Emerald))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Adiantamento $advancePct%", fontSize = 12.sp, color = FinaiColors.TextTertiary)
                    Text("Saldo ${100 - advancePct}%", fontSize = 12.sp, color = FinaiColors.TextTertiary)
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayTile("Adiantamento cai", "Dia ${IncomeSplit.ADVANCE_DAY}", "do mês trabalhado", Modifier.weight(1f))
                    DayTile("Saldo cai", "Dia ${IncomeSplit.BALANCE_DAY}", "do mês seguinte", Modifier.weight(1f))
                }
            }
        }

        IncomeCard {
            Text("Entradas geradas na agenda", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text("Competência: $competenceLabel", fontSize = 12.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEach { e ->
                    val d = e.data.toLocalDate()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(FinaiColors.EmeraldSoftBg),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("%02d".format(d.dayOfMonth), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDark)
                            Text(MONTH_ABBREV_PT[d.monthValue - 1].uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(e.descricao, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                            Text(
                                "Competência $shortMonth" + if (e.incomePart == IncomePart.Balance) " · líquido − adiantamento" else "",
                                fontSize = 12.sp, color = FinaiColors.TextTertiary,
                            )
                        }
                        Text("+ " + brl(e.valorCentavos), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
                    }
                }
            }
            Text(
                "O fechamento de ${MONTH_NAMES_PT[competence.monthValue - 1].lowercase()} conta ${brl(total)} uma única vez." +
                    if (split) " O saldo do dia ${IncomeSplit.BALANCE_DAY} já vem com o adiantamento descontado, então a receita não duplica." else "",
                fontSize = 12.5.sp, lineHeight = 18.sp, color = FinaiColors.EmeraldDark,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(FinaiColors.EmeraldSoftBg).border(1.dp, FinaiColors.EmeraldSoftStroke, RoundedCornerShape(13.dp)).padding(12.dp),
            )
        }

        message?.let {
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (failed) FinaiColors.RoseDark else FinaiColors.EmeraldDark)
        }
        ActionButton(
            if (saving) "Salvando…" else "Salvar renda",
            onClick = {
                if (!saving) {
                    saving = true; message = null
                    scope.launch {
                        try {
                            onSave(chosen); failed = false; message = "Renda salva. As próximas entradas foram atualizadas."
                        } catch (e: CancellationException) { throw e }
                        catch (e: IllegalArgumentException) { failed = true; message = e.message ?: "Confira os dados." }
                        catch (e: Exception) { failed = true; message = "Não foi possível salvar. Tente novamente." }
                        finally { saving = false }
                    }
                }
            },
            primary = true, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IncomeCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp)).padding(16.dp),
        content = content,
    )
}

@Composable
private fun DayTile(label: String, day: String, detail: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(FinaiColors.SurfaceMuted).padding(12.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
        Text(day, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 3.dp))
        Text(detail, fontSize = 12.sp, color = FinaiColors.TextTertiary)
    }
}

/** Escolha entre duas opções, na pílula que o app já usa (aba da Agenda, tipo do lançamento). */
@Composable
private fun <T> Segmented(
    options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, small: Boolean = false,
) {
    Row(
        modifier.then(if (small) Modifier else Modifier.fillMaxWidth()).clip(CircleShape)
            .background(FinaiColors.SurfaceMuted).padding(if (small) 3.dp else 4.dp),
    ) {
        options.forEach { (label, value) ->
            val isSel = value == selected
            Box(
                Modifier.then(if (small) Modifier else Modifier.weight(1f)).height(if (small) 36.dp else 44.dp).clip(CircleShape)
                    .background(if (isSel) FinaiColors.InkStrong else Color.Transparent)
                    .selectable(isSel, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = if (small) 12.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) FinaiColors.OnInkStrong else FinaiColors.TextTertiary, maxLines = 1)
            }
        }
    }
}
