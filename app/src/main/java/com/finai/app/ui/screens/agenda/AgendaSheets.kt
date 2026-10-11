package com.finai.app.ui.screens.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.DebtInstallment
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.EditScope
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.PayCycle
import com.finai.app.domain.RecurringSeries
import com.finai.app.domain.TransactionType
import com.finai.app.domain.freeCashSign
import com.finai.app.domain.hasOverrideIn
import com.finai.app.domain.isGoalContribution
import com.finai.app.domain.isRecurringSeries
import com.finai.app.domain.recurrenceDay
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.EntryCalendar
import com.finai.app.ui.components.EntryGlyph
import com.finai.app.ui.components.EntrySheet
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/*
 * Folhas inferiores da Agenda. Tocar num lançamento abre as informações e as ações dele aqui,
 * em vez de botões fixos em cada linha. Editar reabre o formulário do "+" preenchido (FinaiApp);
 * a parcela de dívida edita a data aqui mesmo, porque é o único campo dela que muda por parcela.
 */

private fun dayMonth(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

private fun monthName(m: YearMonth) = "${MONTH_NAMES_PT[m.monthValue - 1].lowercase()} de ${m.year}"

/** O que impede editar um lançamento pela Agenda, ou nulo se pode. */
internal fun editBlockReason(t: TransacaoEntity): String? = when {
    t.isGoalContribution -> "Aporte em objetivo: para corrigir, exclua e registre de novo, assim o objetivo acompanha."
    t.origem == DebtSchedule.PAYMENT_ORIGIN -> "Pagamento de parcela: para corrigir, desfaça o pagamento e pague de novo."
    else -> null
}

/** Valor com sinal e cor, como na lista: entra em verde, sai neutro. */
@Composable
private fun SheetAmount(cents: Long, sign: Int) {
    Text(
        (if (sign > 0) "+ " else "") + formatBrl(cents / 100.0),
        fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
        color = if (sign > 0) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
    )
}

@Composable
private fun SheetLine(text: String, color: Color = FinaiColors.TextTertiary) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = color, modifier = Modifier.padding(top = 2.dp))
}

/** Linha de ação de 52 dp: ícone, rótulo e, se houver, um detalhe curto. */
@Composable
private fun SheetAction(
    icon: String, label: String, detail: String? = null, destructive: Boolean = false, primary: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = when {
        destructive -> FinaiColors.RoseDark
        primary -> FinaiColors.OnInkStrong
        else -> FinaiColors.TextPrimary
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
            .then(if (primary) Modifier.background(FinaiColors.InkStrong) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryGlyph(icon, ink, size = 20)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold, color = ink)
            detail?.let {
                Text(it, fontSize = 12.5.sp, lineHeight = 16.sp, color = if (primary) ink.copy(alpha = .75f) else FinaiColors.TextMuted)
            }
        }
    }
}

@Composable
private fun SheetDivider() = HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun SheetChip(label: String, accent: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .background(if (accent) FinaiColors.EmeraldSoftBg else FinaiColors.SurfaceMuted)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (accent) FinaiColors.EmeraldDark else FinaiColors.TextSecondary, maxLines = 1)
    }
}

/**
 * Lançamento (avulso, importado, item de fatura ou ocorrência de recorrente): informações e
 * ações. Excluir um recorrente pergunta o alcance aqui mesmo, sem diálogo por cima.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionSheet(
    transacao: TransacaoEntity,
    /** Registro gravado da série (a ocorrência mostrada tem a data do mês, não a inicial). */
    series: TransacaoEntity?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleMainIncome: (TransacaoEntity, Boolean) -> Unit,
    onToggleExtra: (TransacaoEntity, Boolean) -> Unit,
    /** Avulso: excluir (com confirmação da Agenda). */
    onDelete: (TransacaoEntity) -> Unit,
    /** Recorrente: excluir só este mês ou dele em diante. */
    onDeleteOccurrence: (TransacaoEntity, EditScope) -> Unit,
    /** Aberta pelo "Excluir" do menu ⋮: já pergunta o alcance. */
    startInDelete: Boolean = false,
) {
    val recurring = transacao.isRecurringSeries
    val month = RecurringSeries.referenceMonth(transacao)
    var confirmingDelete by rememberSaveable(transacao.id) { mutableStateOf(startInDelete) }
    val receita = transacao.tipo == TransactionType.Receita.name
    val date = transacao.data.toLocalDate()
    EntrySheet(transacao.descricao.ifBlank { transacao.categoria }, onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            SheetAmount(transacao.valorCentavos, transacao.freeCashSign)
            val tipo = TransactionType.values().firstOrNull { it.name == transacao.tipo }?.label ?: transacao.tipo
            SheetLine(
                listOfNotNull(
                    tipo,
                    transacao.categoria.takeIf { transacao.tipo == TransactionType.Gasto.name },
                    (if (date.isAfter(today)) "previsto para " else "em ") + dayMonth(date),
                    transacao.contaOrigem.takeIf { it.isNotBlank() },
                    transacao.contaDestino?.takeIf { transacao.tipo == TransactionType.Transferencia.name }?.let { "para $it" },
                ).joinToString(" · "),
            )
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (recurring) SheetChip(series?.recurrenceDay?.let { "Todo mês, dia $it" } ?: "Todo mês")
                if (receita && PayCycle.isMainIncome(transacao)) SheetChip("Renda principal", accent = true)
                if (transacao.extra) SheetChip("Extra", accent = true)
                if (transacao.faturaId != null) SheetChip("Na fatura")
                if (transacao.origem == "importado") SheetChip("Importado")
            }
            if (recurring && transacao.hasOverrideIn(month)) {
                SheetLine("Alterado só em ${monthName(month)}. Os outros meses seguem a recorrência.", FinaiColors.TextSecondary)
            }
            SheetDivider()

            if (confirmingDelete && recurring) {
                Text("Excluir qual parte?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                SheetAction("calendar", "Somente este mês", "Só ${monthName(month)}. A recorrência continua.", destructive = true) {
                    onDeleteOccurrence(transacao, EditScope.OnlyThis); onDismiss()
                }
                SheetAction("trash", "Este mês e os próximos", "De ${monthName(month)} em diante. Os meses anteriores ficam.", destructive = true) {
                    onDeleteOccurrence(transacao, EditScope.ThisAndFollowing); onDismiss()
                }
                SheetAction("back", "Voltar") { confirmingDelete = false }
            } else {
                TransactionActions(transacao, recurring, receita, onDismiss, onEdit, onToggleMainIncome, onToggleExtra, onDelete) { confirmingDelete = true }
            }
        }
    }
}

@Composable
private fun TransactionActions(
    transacao: TransacaoEntity,
    recurring: Boolean,
    receita: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleMainIncome: (TransacaoEntity, Boolean) -> Unit,
    onToggleExtra: (TransacaoEntity, Boolean) -> Unit,
    onDelete: (TransacaoEntity) -> Unit,
    onAskDeleteScope: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val blocked = editBlockReason(transacao)
        if (blocked == null) {
            SheetAction(
            "edit", "Editar",
            if (transacao.tipo == TransactionType.Gasto.name) "Valor, data, descrição, categoria ou conta" else "Valor, data, descrição ou conta",
            primary = true, onClick = onEdit,
        )
        } else {
            SheetLine(blocked)
        }
        if (receita && transacao.faturaId == null && !transacao.extra) {
            SheetAction("star", if (transacao.rendaPrincipal) "Desmarcar renda principal" else "Marcar como renda principal") {
                onToggleMainIncome(transacao, !transacao.rendaPrincipal); onDismiss()
            }
        }
        if (receita && transacao.faturaId == null) {
            SheetAction("sparkle", if (transacao.extra) "Desmarcar entrada extra" else "Marcar como entrada extra") {
                onToggleExtra(transacao, !transacao.extra); onDismiss()
            }
        }
        SheetAction(
            "trash",
            if (transacao.origem == DebtSchedule.PAYMENT_ORIGIN) "Desfazer pagamento" else "Excluir",
            destructive = true,
        ) {
            if (recurring) onAskDeleteScope() else { onDismiss(); onDelete(transacao) }
        }
    }
}

/**
 * Parcela de dívida: pagar (só a mais antiga em aberto), mudar a data só desta parcela e, à
 * parte e explícito, mudar o dia desta e das próximas. Parcela paga não aparece aqui: ela sai
 * da projeção, então não há como alterá-la.
 */
@Composable
internal fun InstallmentSheet(
    parcela: DebtInstallment,
    month: YearMonth,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPay: () -> Unit,
    onEditDebt: () -> Unit,
    onSaveDate: suspend (LocalDate) -> Unit,
    onChangeDayFrom: suspend (Int) -> Unit,
    /** "Editar parcela" do menu ⋮ abre direto no calendário. */
    startEditingDate: Boolean = false,
) {
    // "acoes" | "data" | "dia"
    var mode by rememberSaveable(parcela.divida.id, month.toString()) { mutableStateOf(if (startEditingDate) "data" else "acoes") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val adjusted = DebtSchedule.adjustmentsOf(parcela.divida).containsKey(month)
    val title = parcela.divida.nome + (parcela.numero?.let { " · $it de ${parcela.total}" } ?: "")

    fun run(block: suspend () -> Unit) {
        if (saving) return
        saving = true; error = null
        scope.launch {
            try { block(); onDismiss() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error = e.message ?: "Não foi possível salvar." }
            catch (e: Exception) { error = "Não foi possível salvar. Tente novamente." }
            finally { saving = false }
        }
    }

    EntrySheet(title, onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            SheetAmount(parcela.valorCentavos, -1)
            SheetLine(
                listOfNotNull(
                    "Parcela de dívida",
                    parcela.vencimento?.let { (if (parcela.atrasada) "venceu em " else "vence em ") + dayMonth(it) } ?: "sem dia definido",
                ).joinToString(" · "),
                if (parcela.atrasada) FinaiColors.RoseDark else FinaiColors.TextTertiary,
            )
            if (adjusted) SheetLine("Data alterada só nesta parcela. As outras seguem o dia combinado.", FinaiColors.TextSecondary)
            SheetDivider()
            when (mode) {
                "data" -> {
                    Text("Nova data desta parcela", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    SheetLine("Só a parcela de ${monthName(month)} muda. Valor, número e as outras parcelas continuam iguais.")
                    Box(Modifier.padding(top = 8.dp)) {
                        EntryCalendar(
                            parcela.vencimento ?: month.atDay(1), onSelect = { d -> run { onSaveDate(d) } },
                            showShortcuts = false, minDate = month.atDay(1), maxDate = month.atEndOfMonth(),
                        )
                    }
                    SheetAction("back", "Voltar") { mode = "acoes"; error = null }
                }
                "dia" -> {
                    Text("Novo dia de vencimento", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    SheetLine(
                        "Esta parcela e as próximas passam a vencer no dia escolhido (29 a 31 num mês mais " +
                            "curto cai no último dia). As parcelas antes desta não mudam.",
                    )
                    DayGrid(parcela.divida.diaVencimento ?: parcela.vencimento?.dayOfMonth) { day -> run { onChangeDayFrom(day) } }
                    SheetAction("back", "Voltar") { mode = "acoes"; error = null }
                }
                else -> {
                    if (parcela.isNext) {
                        SheetAction("check", if (parcela.atrasada) "Já paguei esta parcela" else "Paguei a parcela", primary = true) {
                            onPay(); onDismiss()
                        }
                    }
                    SheetAction("calendar", "Editar parcela", "Muda a data só desta parcela") { mode = "data" }
                    if (parcela.divida.proximoVencimento != null) {
                        SheetAction("repeat", "Mudar o dia desta e das próximas", "As parcelas anteriores não mudam") { mode = "dia" }
                    }
                    SheetAction("installment", "Editar dívida", "Valor, parcelas, juros e vencimento do contrato") { onDismiss(); onEditDebt() }
                }
            }
            error?.let { Text(it, color = FinaiColors.RoseDark, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            if (saving) SheetLine("Salvando…")
        }
    }
}

/** Dias 1 a 31, sete por linha; um toque escolhe e grava. */
@Composable
private fun DayGrid(selected: Int?, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        (1..31).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val isSel = day == selected
                    Box(Modifier.weight(1f).heightIn(min = 46.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(99.dp))
                                .background(if (isSel) FinaiColors.InkStrong else Color.Transparent)
                                .then(if (isSel) Modifier else Modifier.border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(99.dp)))
                                .clickable(role = Role.Button, onClickLabel = "Vencer todo dia $day") { onPick(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.toString(), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (isSel) FinaiColors.OnInkStrong else FinaiColors.TextPrimary)
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
