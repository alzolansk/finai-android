package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.domain.GoalFlow
import com.finai.app.domain.formatMonthYearLong
import com.finai.app.domain.toEpochMillis
import java.time.LocalDate

/**
 * Novo objetivo (referência 1b) em tela cheia, três passos: tipo e nome; quanto precisa e quanto
 * já tem; até quando, com o aporte mensal e se ele cabe na sobra média. Reserva pode ser
 * contínua (sem valor-alvo): aí o passo 3 pergunta o aporte mensal planejado em vez do prazo.
 * Termina na confirmação "Objetivo criado".
 */
@Composable
fun GoalCreateScreen(
    visible: Boolean,
    averageSurplusCents: Long?,
    otherGoalsMonthlyCents: Long,
    onCreate: (ObjetivoEntity) -> Unit,
    onViewGoals: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }, exit = fadeOut(tween(160))) {
        // "Criar outro" recomeça do zero trocando a chave.
        var session by rememberSaveable { mutableIntStateOf(0) }
        key(session) {
            GoalCreateContent(averageSurplusCents, otherGoalsMonthlyCents, onCreate, onViewGoals, onDismiss, onAnother = { session++ })
        }
    }
}

@Composable
private fun GoalCreateContent(
    averageSurplusCents: Long?,
    otherGoalsMonthlyCents: Long,
    onCreate: (ObjetivoEntity) -> Unit,
    onViewGoals: () -> Unit,
    onDismiss: () -> Unit,
    onAnother: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var tipo by rememberSaveable { mutableStateOf<String?>(null) }
    var nome by rememberSaveable { mutableStateOf("") }
    var continua by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableLongStateOf(5_000_00L) }
    var saved by rememberSaveable { mutableLongStateOf(0L) }
    var months by rememberSaveable { mutableIntStateOf(12) }
    var planned by rememberSaveable { mutableLongStateOf(0L) }
    var priority by rememberSaveable { mutableIntStateOf(1) }
    val focus = LocalFocusManager.current
    val isContinua = tipo == "Reserva" && continua
    val needed = if (isContinua) planned else GoalFlow.monthlyNeededCents(target, saved, months.toLong())
    val fit = GoalFlow.fit(needed, averageSurplusCents, otherGoalsMonthlyCents)

    fun create() {
        focus.clearFocus()
        onCreate(
            ObjetivoEntity(
                tipo = tipo ?: "Outro", nome = nome.trim(),
                valorAlvoCentavos = if (isContinua) 0 else target,
                valorGuardadoCentavos = saved,
                // A reserva contínua não usa prazo; a coluna é obrigatória, então vai um ano à frente.
                prazo = (if (isContinua) today.plusYears(1) else GoalFlow.deadlineFor(months, today)).toEpochMillis(),
                prioridade = priority + 1,
                aporteMensalCentavos = if (isContinua) planned else 0,
            ),
        )
        step = 4
    }

    BackHandler {
        focus.clearFocus()
        if (step in 2..3) step-- else onDismiss()
    }

    Box(Modifier.fillMaxSize().background(GoalUi.Page).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            if (step <= 3) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoalRoundButton("back", "Voltar", { focus.clearFocus(); if (step > 1) step-- else onDismiss() }, filled = true)
                    Row(Modifier.weight(1f).padding(start = 6.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { i ->
                            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < step) GoalUi.Emerald else GoalUi.Track))
                        }
                    }
                    Text("$step de 3", color = GoalUi.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 10 else -it / 10 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { if (forward) -it / 10 else it / 10 })
                },
                modifier = Modifier.weight(1f),
                label = "passo",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    when (s) {
                        1 -> StepKind(tipo, { tipo = it; if (it != "Reserva") continua = false }, nome, { nome = it })
                        2 -> StepAmount(
                            tipo == "Reserva", isContinua, { on ->
                                continua = on
                                saved = saved.coerceAtMost(GoalFlow.balanceMaxCents(on, target, 0))
                            },
                            target, { target = it; saved = saved.coerceAtMost(it) }, saved, { saved = it },
                        )
                        3 -> if (isContinua) StepPlanned(planned, { planned = it }, fit, priority, { priority = it })
                        else StepDeadline(today, months, { months = it }, needed, fit, priority, { priority = it })
                        else -> StepDone(
                            nome.trim(),
                            if (isContinua) listOf(
                                "Tipo" to "Reserva contínua",
                                "Saldo atual" to brl(saved),
                                "Aporte mensal" to if (planned > 0) "${brl(planned)}/mês" else "Nenhum",
                                "Prioridade" to GoalFlow.PRIORITY_LABELS[priority],
                            ) else listOf(
                                "Meta" to brl(target),
                                "Já guardado" to brl(saved),
                                "Prazo" to formatMonthYearLong(GoalFlow.deadlineFor(months, today)),
                                "Aporte mensal" to "${brl(needed)}/mês",
                                "Prioridade" to GoalFlow.PRIORITY_LABELS[priority],
                            ),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    1 -> GoalPrimaryButton("Continuar", { focus.clearFocus(); step = 2 }, Modifier.fillMaxWidth(), enabled = tipo != null && nome.isNotBlank())
                    2 -> GoalPrimaryButton("Continuar", { step = 3 }, Modifier.fillMaxWidth())
                    3 -> GoalPrimaryButton("Criar objetivo", { create() }, Modifier.fillMaxWidth())
                    else -> {
                        GoalSecondaryButton("Criar outro", onAnother)
                        GoalPrimaryButton("Ver objetivos", onViewGoals, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(
        title, color = GoalUi.Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp,
        modifier = Modifier.padding(top = 22.dp).semantics { heading() },
    )
    Text(subtitle, color = GoalUi.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StepKind(tipo: String?, onTipo: (String) -> Unit, nome: String, onNome: (String) -> Unit) {
    val focus = LocalFocusManager.current
    StepTitle("Qual é o objetivo?", "Escolha um tipo e dê um nome que te motive.")
    Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalFlow.TYPES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> KindTile(t, selected = tipo == t, onClick = { onTipo(t) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
    Text("Nome do objetivo", color = GoalUi.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 26.dp))
    var focused by remember { mutableStateOf(false) }
    val kind = tipo ?: "Viagem"
    BasicTextField(
        value = nome, onValueChange = { onNome(it.take(40)) }, singleLine = true,
        textStyle = TextStyle(color = GoalUi.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = EntryFont, letterSpacing = (-0.5).sp),
        cursorBrush = SolidColor(GoalUi.Emerald),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 6.dp)) {
                    if (nome.isEmpty()) Text(GoalFlow.placeholder(kind), color = GoalUi.Faint, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    inner()
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(if (focused) GoalUi.Emerald else GoalUi.Border))
            }
        },
    )
    FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoalFlow.suggestions(kind).forEach { s ->
            val sel = nome.trim().equals(s, ignoreCase = true)
            Box(
                Modifier.clip(CircleShape).background(if (sel) GoalUi.EmeraldSoft else GoalUi.Card)
                    .border(1.dp, if (sel) GoalUi.Emerald else GoalUi.Border, CircleShape)
                    .clickable(role = Role.Button) { onNome(s); focus.clearFocus() }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text(s, color = if (sel) GoalUi.EmeraldText else GoalUi.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KindTile(tipo: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val color = if (selected) GoalUi.EmeraldText else GoalUi.Ink
    Column(
        modifier.height(84.dp).clip(shape)
            .background(if (selected) GoalUi.EmeraldSoft else GoalUi.Card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) GoalUi.Emerald else GoalUi.Border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        GoalGlyph(tipo, color, size = 20)
        Text(tipo, color = color, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StepAmount(
    isReserve: Boolean,
    continua: Boolean,
    onContinua: (Boolean) -> Unit,
    target: Long,
    onTarget: (Long) -> Unit,
    saved: Long,
    onSaved: (Long) -> Unit,
) {
    StepTitle(
        "Quanto você precisa?",
        if (continua) "Sem valor-alvo: a reserva cresce sem um fim definido." else "Arraste para definir a meta. Ajuste fino com os atalhos.",
    )
    if (isReserve) {
        GoalCard(Modifier.padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reserva contínua", color = GoalUi.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Sem valor-alvo nem prazo", color = GoalUi.Muted, fontSize = 12.5.sp)
                }
                GoalSwitch(continua, onContinua, "Reserva contínua")
            }
        }
    }
    if (!continua) {
        Text(
            brl(target), color = GoalUi.Ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
        )
        GoalSlider(
            GoalFlow.fractionOf(target, GoalFlow.TARGET_MIN_CENTS, GoalFlow.TARGET_MAX_CENTS),
            { f -> onTarget(GoalFlow.valueAt(f, GoalFlow.TARGET_MIN_CENTS, GoalFlow.TARGET_MAX_CENTS, GoalFlow.TARGET_STEP_CENTS)) },
            brl(target), Modifier.padding(top = 26.dp), description = "Valor da meta",
        )
        Row(Modifier.fillMaxWidth()) {
            Text(brl(GoalFlow.TARGET_MIN_CENTS), color = GoalUi.Muted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            Text(brl(GoalFlow.TARGET_MAX_CENTS), color = GoalUi.Muted, fontSize = 11.5.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalFlow.TARGET_PRESETS.forEach { p ->
                GoalPresetChip("${p / 100_000} mil", selected = target == p, onClick = { onTarget(p) }, modifier = Modifier.weight(1f))
            }
        }
    }
    val max = GoalFlow.balanceMaxCents(continua, target, 0)
    GoalCard(Modifier.padding(top = if (continua) 12.dp else 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (continua) "Quanto já tem nela?" else "Já tem algo guardado?",
                color = GoalUi.Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
            )
            Text(brl(saved), color = GoalUi.EmeraldText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        GoalSlider(
            GoalFlow.fractionOf(saved, 0, max),
            { f -> onSaved(GoalFlow.valueAt(f, 0, max, GoalFlow.BALANCE_STEP_CENTS)) },
            brl(saved), Modifier.padding(top = 4.dp), description = "Valor já guardado",
        )
        Text(
            when {
                saved == 0L -> "Começando do zero"
                continua -> "Saldo inicial da reserva"
                else -> "${GoalFlow.percent(saved, target)}% da meta já guardado"
            },
            color = GoalUi.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun MonthlyCard(title: String, neededCents: Long, fit: GoalFlow.Fit, alreadyThere: Boolean) {
    GoalCard(Modifier.padding(top = 26.dp)) {
        Text(title, color = GoalUi.Muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(brl(neededCents), color = GoalUi.Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(" /mês", color = GoalUi.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
        }
        if (alreadyThere) {
            Text("Você já tem o valor da meta.", color = GoalUi.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            GoalBar(fit.barFraction, fitBarColor(fit.level), Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FitPill(fit.level, fit.label)
                Text(fit.text, color = GoalUi.Muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PrioritySection(priority: Int, onPriority: (Int) -> Unit) {
    Text("Prioridade", color = GoalUi.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
    GoalSegmented(GoalFlow.PRIORITY_LABELS, priority, onPriority)
}

@Composable
private fun StepDeadline(
    today: LocalDate,
    months: Int,
    onMonths: (Int) -> Unit,
    neededCents: Long,
    fit: GoalFlow.Fit,
    priority: Int,
    onPriority: (Int) -> Unit,
) {
    StepTitle("Até quando?", "O assistente calcula o aporte mensal e checa se cabe no seu orçamento.")
    val label = formatMonthYearLong(GoalFlow.deadlineFor(months, today))
    Text(
        label, color = GoalUi.Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
    )
    Text(GoalFlow.horizonLabel(months), color = GoalUi.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    GoalSlider(
        GoalFlow.fractionOf(months.toLong(), GoalFlow.MIN_MONTHS.toLong(), GoalFlow.MAX_MONTHS.toLong()),
        { f -> onMonths(GoalFlow.valueAt(f, GoalFlow.MIN_MONTHS.toLong(), GoalFlow.MAX_MONTHS.toLong(), 1).toInt()) },
        label, Modifier.padding(top = 22.dp), description = "Prazo",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("1 mês", "1 ano", "2 anos", "5 anos").forEach { Text(it, color = GoalUi.Muted, fontSize = 11.5.sp) }
    }
    MonthlyCard("Você vai precisar guardar", neededCents, fit, alreadyThere = neededCents == 0L)
    PrioritySection(priority, onPriority)
}

@Composable
private fun StepPlanned(planned: Long, onPlanned: (Long) -> Unit, fit: GoalFlow.Fit, priority: Int, onPriority: (Int) -> Unit) {
    StepTitle("Quanto por mês?", "Opcional. Sem valor, o assistente não cobra aportes desta reserva.")
    Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text(
            if (planned > 0) brl(planned) else "Nenhum", color = GoalUi.Ink, fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp,
        )
        if (planned > 0) Text(" /mês", color = GoalUi.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
    }
    GoalSlider(
        GoalFlow.fractionOf(planned, 0, GoalFlow.PLANNED_MAX_CENTS),
        { f -> onPlanned(GoalFlow.valueAt(f, 0, GoalFlow.PLANNED_MAX_CENTS, GoalFlow.PLANNED_STEP_CENTS)) },
        if (planned > 0) brl(planned) else "Nenhum", Modifier.padding(top = 22.dp), description = "Aporte mensal planejado",
    )
    Row(Modifier.fillMaxWidth()) {
        Text("Nenhum", color = GoalUi.Muted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
        Text(brl(GoalFlow.PLANNED_MAX_CENTS), color = GoalUi.Muted, fontSize = 11.5.sp)
    }
    if (planned > 0) MonthlyCard("Você planeja guardar", planned, fit, alreadyThere = false)
    PrioritySection(priority, onPriority)
}

@Composable
private fun StepDone(nome: String, rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HaloCheck()
        Text("Objetivo criado", color = GoalUi.EmeraldText, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
        Text(
            nome, color = GoalUi.Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp).clip(RoundedCornerShape(18.dp)).background(GoalUi.Card)
                .border(1.dp, GoalUi.Border, RoundedCornerShape(18.dp)),
        ) {
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GoalUi.Border))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = GoalUi.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(value, color = GoalUi.Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

