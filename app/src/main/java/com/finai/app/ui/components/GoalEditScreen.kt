package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.domain.Completion
import com.finai.app.domain.GoalFlow
import com.finai.app.domain.formatMonthYearLong
import com.finai.app.domain.formatMonthYearShort
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.monthsUntil
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.theme.FinaiColors
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Editar objetivo (referência 1c): os objetivos em chips no topo para trocar sem sair, uma
 * prévia que acompanha o que está sendo editado, os campos em cartões e "O que vai mudar" antes
 * de salvar. Salvar fica na tela e mostra "Alterações salvas".
 */
@Composable
fun GoalEditScreen(
    goalId: Long?,
    objetivos: List<ObjetivoEntity>,
    onSave: (ObjetivoEntity) -> Unit,
    onDelete: (ObjetivoEntity) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(goalId != null, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }, exit = fadeOut(tween(160))) {
        var lastId by remember { mutableStateOf(goalId) }
        if (goalId != null) lastId = goalId
        val startId = lastId ?: return@AnimatedVisibility
        key(startId) { GoalEditContent(startId, objetivos, onSave, onDelete, onClose) }
    }
}

@Composable
private fun GoalEditContent(
    startId: Long,
    objetivos: List<ObjetivoEntity>,
    onSave: (ObjetivoEntity) -> Unit,
    onDelete: (ObjetivoEntity) -> Unit,
    onClose: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val focus = LocalFocusManager.current
    var selectedId by remember { mutableLongStateOf(startId) }
    val source = objetivos.firstOrNull { it.id == selectedId }
    // Fecha se o objetivo sumiu (excluído em outro lugar).
    if (source == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var baseline by remember(selectedId) { mutableStateOf(source) }
    var draft by remember(selectedId) { mutableStateOf(source) }
    val changes = GoalFlow.changes(baseline, draft)
    var pendingSwitch by remember { mutableStateOf<Long?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var toastKey by remember { mutableIntStateOf(0) }
    var toastVisible by remember { mutableStateOf(false) }
    LaunchedEffect(toastKey) {
        if (toastKey > 0) { toastVisible = true; delay(1800); toastVisible = false }
    }

    val continua = draft.isOpenReserve
    val isReserve = draft.tipo == "Reserva"

    fun withTarget(o: ObjetivoEntity): ObjetivoEntity {
        if (!o.isOpenReserve) return o
        val target = baseline.valorAlvoCentavos.takeIf { it > 0 }
            ?: maxOf(5_000_00L, GoalFlow.roundUp(o.valorGuardadoCentavos + 1_000_00L, 1_000_00L)).coerceAtMost(GoalFlow.TARGET_MAX_CENTS)
        val prazo = if (!baseline.isOpenReserve) baseline.prazo else GoalFlow.deadlineFor(12, today).toEpochMillis()
        return o.copy(valorAlvoCentavos = target, prazo = prazo, aporteMensalCentavos = 0)
    }

    fun save() {
        focus.clearFocus()
        val clean = draft.copy(nome = draft.nome.trim(), descricao = draft.descricao.trim())
        onSave(clean)
        baseline = clean
        draft = clean
        toastKey++
    }

    fun requestSwitch(id: Long) {
        if (id == selectedId) return
        if (changes.isNotEmpty()) pendingSwitch = id else selectedId = id
    }

    fun leave() { focus.clearFocus(); if (changes.isNotEmpty()) confirmLeave = true else onClose() }
    BackHandler { leave() }

    Box(Modifier.fillMaxSize().background(GoalUi.Page).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GoalRoundButton("back", "Voltar", { leave() }, filled = true)
                Text("Editar objetivo", color = GoalUi.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = changes.isNotEmpty(), role = Role.Button) { focus.clearFocus(); draft = baseline }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Descartar", color = if (changes.isNotEmpty()) GoalUi.Ink.copy(alpha = .75f) else GoalUi.Faint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            // Ordem fixa (concluídas por último, depois a de criação): salvar uma prioridade nova
            // não pode tirar o chip do lugar enquanto se edita.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                objetivos.sortedWith(compareBy<ObjetivoEntity> { Completion.isDone(it) }.thenBy { it.id }).forEach { o ->
                    val sel = o.id == selectedId
                    Box(
                        Modifier.height(36.dp).clip(CircleShape).background(if (sel) GoalUi.Selected else GoalUi.Control)
                            .clickable(role = Role.Tab) { requestSwitch(o.id) }
                            .semantics { selected = sel }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(o.nome, color = if (sel) GoalUi.OnSelected else GoalUi.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                PreviewCard(draft, today)

                GoalCard {
                    Label("Nome")
                    UnderlineField(draft.nome, { draft = draft.copy(nome = it.take(40)) }, placeholder = GoalFlow.placeholder(draft.tipo))
                    Label("Tipo", Modifier.padding(top = 16.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalFlow.TYPES.forEach { t ->
                            TypeChip(t, draft.tipo == t, Modifier.weight(1f)) {
                                draft = if (t != "Reserva") withTarget(draft.copy(tipo = t)) else draft.copy(tipo = t)
                            }
                        }
                    }
                }

                if (isReserve) {
                    GoalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reserva contínua", color = GoalUi.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Sem valor-alvo nem prazo", color = GoalUi.Muted, fontSize = 12.5.sp)
                            }
                            GoalSwitch(continua, { on ->
                                draft = if (on) draft.copy(valorAlvoCentavos = 0, aporteMensalCentavos = baseline.aporteMensalCentavos.takeIf { baseline.isOpenReserve } ?: 0)
                                else withTarget(draft)
                            }, "Reserva contínua")
                        }
                        if (continua) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(GoalUi.Border))
                            val planned = draft.aporteMensalCentavos
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Aporte mensal planejado", color = GoalUi.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(if (planned > 0) brl(planned) else "Nenhum", color = GoalUi.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            GoalSlider(
                                GoalFlow.fractionOf(planned, 0, GoalFlow.PLANNED_MAX_CENTS),
                                { f -> draft = draft.copy(aporteMensalCentavos = GoalFlow.valueAt(f, 0, GoalFlow.PLANNED_MAX_CENTS, GoalFlow.PLANNED_STEP_CENTS)) },
                                if (planned > 0) brl(planned) else "Nenhum", Modifier.padding(top = 4.dp), description = "Aporte mensal planejado",
                            )
                            Text(
                                "Opcional. Sem valor, o assistente não cobra aportes desta reserva.",
                                color = GoalUi.Muted, fontSize = 12.5.sp, lineHeight = 17.sp,
                            )
                        }
                    }
                }

                if (!continua) {
                    GoalCard {
                        Label("Valor da meta")
                        AmountStepper(draft.valorAlvoCentavos, GoalFlow.TARGET_STEP_CENTS, GoalFlow.TARGET_MIN_CENTS, GoalFlow.TARGET_MAX_CENTS) {
                            draft = draft.copy(valorAlvoCentavos = it)
                        }
                        GoalSlider(
                            GoalFlow.fractionOf(draft.valorAlvoCentavos, GoalFlow.TARGET_MIN_CENTS, GoalFlow.TARGET_MAX_CENTS),
                            { f -> draft = draft.copy(valorAlvoCentavos = GoalFlow.valueAt(f, GoalFlow.TARGET_MIN_CENTS, GoalFlow.TARGET_MAX_CENTS, GoalFlow.TARGET_STEP_CENTS)) },
                            brl(draft.valorAlvoCentavos), Modifier.padding(top = 6.dp), description = "Valor da meta",
                        )
                        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(GoalUi.Border))
                        val prazo = draft.prazo.toLocalDate()
                        val months = GoalFlow.monthsFor(prazo, today)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("Prazo", Modifier.weight(1f))
                            Text(formatMonthYearLong(prazo), color = GoalUi.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        GoalSlider(
                            GoalFlow.fractionOf(months.toLong(), GoalFlow.MIN_MONTHS.toLong(), GoalFlow.MAX_MONTHS.toLong()),
                            { f ->
                                val m = GoalFlow.valueAt(f, GoalFlow.MIN_MONTHS.toLong(), GoalFlow.MAX_MONTHS.toLong(), 1).toInt()
                                draft = draft.copy(prazo = GoalFlow.deadlineFor(m, today).toEpochMillis())
                            },
                            formatMonthYearShort(prazo), Modifier.padding(top = 4.dp), description = "Prazo",
                        )
                        val needed = GoalFlow.monthlyNeededCents(draft.valorAlvoCentavos, draft.valorGuardadoCentavos, monthsUntil(prazo, today))
                        Text(
                            when {
                                prazo.isBefore(today) -> "O prazo já passou. Arraste para definir um novo."
                                needed == 0L -> "${GoalFlow.horizonLabel(months).replaceFirstChar { it.uppercase() }} · o valor da meta já está guardado"
                                else -> "${GoalFlow.horizonLabel(months).replaceFirstChar { it.uppercase() }} · ${brl(needed)}/mês para chegar lá"
                            },
                            color = if (prazo.isBefore(today)) GoalUi.Rose else GoalUi.Muted, fontSize = 12.5.sp,
                        )
                    }
                }

                GoalCard {
                    Label("Saldo atual")
                    val max = GoalFlow.balanceMaxCents(continua, draft.valorAlvoCentavos, maxOf(draft.valorGuardadoCentavos, baseline.valorGuardadoCentavos))
                    AmountStepper(draft.valorGuardadoCentavos, GoalFlow.BALANCE_STEP_CENTS, 0, Long.MAX_VALUE / 4) {
                        draft = draft.copy(valorGuardadoCentavos = it)
                    }
                    GoalSlider(
                        GoalFlow.fractionOf(draft.valorGuardadoCentavos, 0, max),
                        { f -> draft = draft.copy(valorGuardadoCentavos = GoalFlow.valueAt(f, 0, max, GoalFlow.BALANCE_STEP_CENTS)) },
                        brl(draft.valorGuardadoCentavos), Modifier.padding(top = 6.dp), description = "Saldo atual",
                    )
                    Text(
                        "Use para corrigir o saldo. Para guardar dinheiro novo, use “Adicionar valor”.",
                        color = GoalUi.Muted, fontSize = 12.5.sp, lineHeight = 17.sp,
                    )
                }

                GoalCard {
                    Label("Prioridade", Modifier.padding(bottom = 8.dp))
                    GoalSegmented(GoalFlow.PRIORITY_LABELS, GoalFlow.priorityIndex(draft.prioridade), { i ->
                        // Volta ao número original quando a faixa é a mesma: não conta como alteração.
                        draft = draft.copy(prioridade = if (GoalFlow.priorityIndex(baseline.prioridade) == i) baseline.prioridade else i + 1)
                    }, background = GoalUi.Inset)
                    Label("Descrição", Modifier.padding(top = 18.dp, bottom = 8.dp))
                    DescriptionField(draft.descricao) { draft = draft.copy(descricao = it.take(300)) }
                }

                AnimatedVisibility(changes.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ChangesCard(changes)
                }

                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                        .border(1.dp, GoalUi.RoseLine, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { focus.clearFocus(); confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Excluir objetivo", color = GoalUi.Rose, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                GoalPrimaryButton(
                    if (changes.isEmpty()) "Salvar" else "Salvar · ${changes.size} ${if (changes.size == 1) "alteração" else "alterações"}",
                    { save() }, Modifier.fillMaxWidth(),
                    enabled = changes.isNotEmpty() && draft.nome.isNotBlank(),
                )
            }
        }

        AnimatedVisibility(
            toastVisible,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 },
        ) {
            Row(
                Modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(GoalUi.Selected)
                    .padding(horizontal = 18.dp, vertical = 11.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalGlyph("check", if (FinaiColors.isDark) GoalUi.Emerald else Color(0xFF34D399), size = 16, stroke = 2.4f)
                Text("Alterações salvas", color = GoalUi.OnSelected, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    pendingSwitch?.let { target ->
        DiscardDialog(
            onDiscard = { pendingSwitch = null; selectedId = target },
            onKeep = { pendingSwitch = null },
        )
    }
    if (confirmLeave) {
        DiscardDialog(onDiscard = { confirmLeave = false; onClose() }, onKeep = { confirmLeave = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir objetivo?") },
            text = { Text("“${baseline.nome}” e o valor registrado nele saem do app. Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(baseline); onClose() }) { Text("Excluir", color = GoalUi.Rose) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Descartar alterações?") },
        text = { Text("O que você mudou neste objetivo ainda não foi salvo.") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Continuar editando") } },
    )
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = GoalUi.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun PreviewCard(o: ObjetivoEntity, today: LocalDate) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(GoalUi.EmeraldSoft, GoalUi.EmeraldSoft.copy(alpha = if (FinaiColors.isDark) .45f else .6f))))
            .border(1.dp, GoalUi.EmeraldLine, shape)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PRÉVIA", color = GoalUi.EmeraldText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(GoalUi.Control).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(o.tipo, color = GoalUi.Ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            o.nome.ifBlank { "Sem nome" }, color = if (o.nome.isBlank()) GoalUi.Faint else GoalUi.Ink,
            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(top = 10.dp),
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(brl(o.valorGuardadoCentavos), color = GoalUi.Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Text(" guardados", color = GoalUi.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (!o.isOpenReserve) {
            GoalBar((o.valorGuardadoCentavos.toFloat() / o.valorAlvoCentavos).coerceIn(0f, 1f), modifier = Modifier.padding(top = 10.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val prazo = o.prazo.toLocalDate()
            Text(
                if (o.isOpenReserve) "Reserva contínua" else "Meta ${brl(o.valorAlvoCentavos)} · ${formatMonthYearShort(prazo)}",
                color = GoalUi.Muted, fontSize = 12.5.sp, modifier = Modifier.weight(1f),
            )
            Text(
                if (o.isOpenReserve) (if (o.aporteMensalCentavos > 0) "${brl(o.aporteMensalCentavos)}/mês planejado" else "Sem aporte planejado")
                else GoalFlow.monthlyNeededCents(o.valorAlvoCentavos, o.valorGuardadoCentavos, monthsUntil(prazo, today))
                    .let { if (it > 0) "${brl(it)}/mês" else "Meta alcançada" },
                color = GoalUi.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun UnderlineField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = GoalUi.Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, fontFamily = EntryFont),
        cursorBrush = SolidColor(GoalUi.Emerald),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 6.dp)) {
                    if (value.isEmpty()) Text(placeholder, color = GoalUi.Faint, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    inner()
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(if (focused) GoalUi.Emerald else GoalUi.Border))
            }
        },
    )
}

@Composable
private fun TypeChip(tipo: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(42.dp).clip(shape)
            .background(if (selected) GoalUi.EmeraldSoft else Color.Transparent)
            .border(1.dp, if (selected) GoalUi.Emerald else GoalUi.Border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(tipo, color = if (selected) GoalUi.EmeraldText else GoalUi.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** − valor + (o valor da meta e o saldo atual). */
@Composable
private fun AmountStepper(cents: Long, step: Long, min: Long, max: Long, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GoalRoundButton("minus", "Diminuir", { onChange((cents - step).coerceAtLeast(min)) }, enabled = cents > min)
        Text(
            brl(cents), color = GoalUi.Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
        )
        GoalRoundButton("plus", "Aumentar", { onChange((cents + step).coerceAtMost(max)) }, enabled = cents < max)
    }
}

@Composable
private fun DescriptionField(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange,
        textStyle = TextStyle(color = GoalUi.Ink, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = EntryFont),
        cursorBrush = SolidColor(GoalUi.Emerald),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(GoalUi.Inset)
                    .border(1.dp, if (focused) GoalUi.Emerald else GoalUi.Border, shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text("Por que essa meta existe, o que fica de fora dela…", color = GoalUi.Faint, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
private fun ChangesCard(changes: List<GoalFlow.Change>) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(GoalUi.EmeraldSoft.copy(alpha = if (FinaiColors.isDark) .45f else .7f))
            .border(1.dp, GoalUi.EmeraldLine, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("O QUE VAI MUDAR", color = GoalUi.EmeraldText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        changes.forEach { c ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(c.label, color = GoalUi.Muted, fontSize = 12.sp)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    c.before?.let {
                        Text(it, color = GoalUi.Muted, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough, maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp))
                    }
                    GoalGlyph("arrow", GoalUi.Emerald, size = 14, stroke = 2.2f)
                    Text(c.after, color = GoalUi.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2,
                        modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
