package com.finai.app.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.CycleSource
import com.finai.app.domain.inbox.*
import com.finai.app.ui.components.*
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun InboxScreen(
    listState: LazyListState, items: List<InboxItem>, dismissedCount: Int,
    onOpen: (InboxItem) -> Unit, onEditSuggestion: (InboxItem) -> Unit,
    onPay: (InboxItem) -> Unit, onDismiss: (InboxItem) -> Unit,
    onSnooze: (InboxItem, LocalDate) -> Unit, onRestore: () -> Unit,
    onIgnore: suspend (Long) -> Unit, today: LocalDate = LocalDate.now(),
    onSaveSuggestion: (suspend (InboxItem) -> com.finai.app.domain.EntryReceipt?)? = null,
    onUndoSuggestion: suspend (Long, com.finai.app.domain.EntryReceipt) -> Unit = { _, _ -> },
) {
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Pair<Long, com.finai.app.domain.EntryReceipt>?>(null) }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("title") { PageTitle("Caixa", listState, subtitle = "${items.size} pendentes") }
        item("intro") { Text("Tudo que pede uma decisão sua fica aqui, uma vez só. O que você resolve ou dispensa sai da Caixa e de todas as telas.",
            fontSize = 14.sp, color = FinaiColors.TextSecondary) }
        if (error != null) item("error") {
            Text(error.orEmpty(), color = FinaiColors.RoseDark)
        }
        saved?.let { (id, receipt) -> item("saved") {
            Column {
                Text("Lançamento salvo.", color = FinaiColors.EmeraldDark)
                TextAction("Desfazer", { scope.launch {
                    try { onUndoSuggestion(id, receipt); saved = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { error = "Não foi possível desfazer. Tente de novo." }
                } })
            }
        } }
        if (items.isEmpty()) item("empty") {
            Text("Tudo em dia. Nenhuma decisão pendente.", color = FinaiColors.TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
        }
        InboxGroup.entries.forEach { group ->
            val groupItems = items.filter { it.group == group }
            val color = when (group) {
                InboxGroup.Confirm -> FinaiColors.EmeraldDark
                InboxGroup.Act -> FinaiColors.RoseDark
                InboxGroup.Watch -> FinaiColors.AmberDark
            }
            if (groupItems.isNotEmpty()) {
                item("heading-$group") {
                    val date = groupItems.mapNotNull { it.due }.minOrNull()
                    val title = when (group) {
                        InboxGroup.Confirm -> "PARA CONFIRMAR · lido das notificações"
                        InboxGroup.Act -> if (date == null || !date.isAfter(today)) "AGIR HOJE" else
                            "AGIR ATÉ ${date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("pt", "BR"))).uppercase()}"
                        InboxGroup.Watch -> "ACOMPANHAR"
                    }
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color,
                        modifier = Modifier.padding(top = 10.dp).semantics { heading() })
                }
                items(groupItems, key = { it.id }) { item ->
                    Column(Modifier.fillMaxWidth().testTag("inbox-${item.id}").background(FinaiColors.Surface, RoundedCornerShape(20.dp))
                        .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(20.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text(if (item.due != null) item.due.dayOfMonth.toString() else item.title.take(2),
                                    color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = FinaiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(item.body, color = FinaiColors.TextSecondary, fontSize = 13.sp)
                            }
                        }
                        if (item.valueCents != 0L) Text(formatBrl(item.valueCents / 100.0),
                            color = FinaiColors.TextPrimary, fontWeight = FontWeight.Bold)
                        when (group) {
                            InboxGroup.Confirm -> {
                                InboxActions { actionModifier ->
                                    ActionButton(if (saving) "Salvando…" else "Salvar", {
                                        if (!saving) {
                                            if (onSaveSuggestion == null) onOpen(item)
                                            else scope.launch {
                                                saving = true; error = null
                                                try { onSaveSuggestion(item)?.let { saved = item.suggestion!!.id to it } }
                                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                                catch (_: Exception) { error = "Não foi possível salvar. Tente de novo." }
                                                finally { saving = false }
                                            }
                                        }
                                    }, actionModifier, primary = true)
                                    ActionButton("Ignorar", {
                                        scope.launch {
                                            try { onIgnore(item.suggestion!!.id) }
                                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (_: Exception) { error = "Não foi possível ignorar. Tente de novo." }
                                        }
                                    }, actionModifier)
                                }
                                TextAction("Editar no formulário", { onEditSuggestion(item) })
                            }
                            InboxGroup.Act -> {
                                val canPay = item.movement?.source is CycleSource.Conta
                                InboxActions { actionModifier ->
                                    ActionButton(if (canPay) "Marcar paga" else "Ver na Agenda",
                                        { if (canPay) onPay(item) else onOpen(item) }, actionModifier, primary = true)
                                    ActionButton(if (item.due?.isAfter(today) == true) "Lembrar no dia" else "Lembrar amanhã",
                                        { onSnooze(item, item.due?.takeIf { it.isAfter(today) } ?: today.plusDays(1)) }, actionModifier)
                                }
                                TextAction("Dispensar", { onDismiss(item) }, color = FinaiColors.TextMuted)
                            }
                            InboxGroup.Watch -> InboxActions { actionModifier ->
                                ActionButton(item.insight?.actionLabel?.takeIf { it.isNotBlank() }
                                    ?: if (item.kind in setOf("limite", "coach", "assinatura")) "Ver gastos" else "Ver na Agenda",
                                    { onOpen(item) }, actionModifier, primary = true)
                                ActionButton(item.insight?.dismissLabel ?: "Dispensar", { onDismiss(item) }, actionModifier)
                            }
                        }
                    }
                }
            }
        }
        if (dismissedCount > 0) item("dismissed") {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$dismissedCount dispensados · não voltam sozinhos", color = FinaiColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                Text("Um aviso dispensado só volta se piorar: o limite for atingido, a conta atrasar ou o valor mudar mais de 20%.",
                    color = FinaiColors.TextMuted, fontSize = 12.sp)
                TextAction("Desfazer tudo", onRestore)
            }
        }
    }
}

/** Os dois botões mantêm palavras inteiras quando a fonte ou a largura reduz o espaço. */
@Composable
private fun InboxActions(content: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (fontScale >= 1.3f || maxWidth < 250.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier.weight(1f)) }
        }
    }
}
