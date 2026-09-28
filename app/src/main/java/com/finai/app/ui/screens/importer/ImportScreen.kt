package com.finai.app.ui.screens.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.importer.ImportStage
import com.finai.app.data.model.Categorias
import com.finai.app.domain.importer.CategorySource
import com.finai.app.domain.importer.DuplicateVerdict
import com.finai.app.domain.importer.ImportItem
import com.finai.app.domain.importer.ImportPreview
import com.finai.app.domain.importer.RecurrenceVerdict
import com.finai.app.state.ImportUiState
import com.finai.app.ui.components.PillTag
import com.finai.app.ui.components.SelectChip
import com.finai.app.ui.components.TextAction
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl
import java.time.Instant
import java.time.ZoneOffset

/**
 * Importação de fatura (Fase 4). A tela só mostra estado e coleta as decisões
 * do usuário: escolher o arquivo, revisar o que foi extraído, corrigir
 * categoria, desmarcar duplicata e confirmar. Nenhum OCR, parsing ou chamada
 * de IA acontece aqui — tudo vem pronto do [com.finai.app.state.ImportViewModel].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    state: ImportUiState,
    onPickDocument: (android.net.Uri) -> Unit,
    onToggleItem: (String) -> Unit,
    onSetCategory: (String, String) -> Unit,
    onSetAllSelected: (Boolean) -> Unit,
    onSetInvoiceReference: (String) -> Unit,
    onSetInvoiceDueDate: (String) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onOpenAgenda: () -> Unit,
) {
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickDocument)
    }
    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickDocument)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickDocument)
    }

    // Filtro da lista de revisão; volta a "Todos" a cada importação nova.
    var onlyReview by rememberSaveable(state.preview?.sourceName) { mutableStateOf(false) }
    val preview = state.preview
    val showConfirm = preview != null && state.savedCount == null && !state.busy && state.stage == null && state.error == null

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            // A barra de confirmação fica fixa por cima; o último item precisa rolar para cima dela.
            contentPadding = if (showConfirm) PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 250.dp) else ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("Importar fatura", style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary)
                    Text(
                        "PDF, planilha ou foto — lidos aqui no aparelho",
                        fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            when {
                state.savedCount != null -> item { SavedCard(state, onOpenAgenda, onReset) }
                state.busy || state.stage != null -> item { ProgressCard(state.stage) }
                state.error != null -> item { ErrorCard(state.error, onReset) }
                preview != null -> {
                    item { PreviewSummaryCard(preview, onSetAllSelected, onSetInvoiceReference, onSetInvoiceDueDate, onReset) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("Todos · ${preview.items.size}", !onlyReview) { onlyReview = false }
                            SelectChip("Precisa revisar · ${preview.needsReviewCount}", onlyReview) { onlyReview = true }
                        }
                    }
                    val shown = if (onlyReview) preview.items.filter { it.needsReview } else preview.items
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                "Nada para revisar: categoria, duplicatas e assinaturas foram resolvidas pela regra local.",
                                fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary,
                            )
                        }
                    }
                    items(shown, key = { it.id }) { item ->
                        ImportItemRow(item, onToggleItem, onSetCategory)
                    }
                }
                else -> {
                    item {
                        PickerCard(
                            onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onPickSpreadsheet = { sheetPicker.launch(SPREADSHEET_MIME_TYPES) },
                            onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                        )
                    }
                    item { PrivacyCard() }
                }
            }
        }

        // Fixa no rodapé, acima da barra de navegação (Fase 7, item 11): numa fatura grande,
        // como item da lista, ela ficava lá embaixo.
        if (showConfirm && preview != null) {
            ConfirmBar(
                preview, onConfirm,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 92.dp),
            )
        }
    }
}

private val SPREADSHEET_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/tab-separated-values",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

@Composable
private fun PickerCard(onPickPdf: () -> Unit, onPickSpreadsheet: () -> Unit, onPickImage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp))
            .background(FinaiColors.Surface)
            .padding(vertical = 24.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(FinaiColors.EmeraldSoftBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Upload, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(24.dp))
        }
        Text(
            "Escolha a fatura", fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "O texto é extraído no próprio aparelho. O arquivo não é enviado para nenhum servidor.",
            fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PickerButton("PDF", Icons.Filled.PictureAsPdf, onPickPdf, Modifier.weight(1f))
            PickerButton("Planilha", Icons.Filled.TableChart, onPickSpreadsheet, Modifier.weight(1f))
            PickerButton("Foto", Icons.Filled.Image, onPickImage, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PickerButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(FinaiColors.SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = FinaiColors.TextBody, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextBody, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PrivacyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(16.dp))
            Text("O que acontece com o arquivo", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            STEP_TEXTS.forEachIndexed { index, text -> StepRow(index + 1, text) }
        }
    }
}

private val STEP_TEXTS = listOf(
    "Extrai os lançamentos sem enviar o arquivo para fora do aparelho",
    "Classifica cada compra em uma categoria por regra local",
    "Detecta assinaturas recorrentes e parcelas",
    "Separa duplicados para você confirmar",
    "Só o que ficou ambíguo (nome do estabelecimento, valor e dia) pode ir para a IA",
)

@Composable
private fun StepRow(number: Int, text: String, done: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (done) FinaiColors.EmeraldSoftBg else FinaiColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else number.toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = if (done) FinaiColors.EmeraldDark else FinaiColors.TextMuted,
            )
        }
        Text(text, fontSize = 13.sp, lineHeight = 17.sp, color = if (done) FinaiColors.TextPrimary else FinaiColors.TextTertiary)
    }
}

@Composable
private fun ProgressCard(stage: ImportStage?) {
    val stages = listOf(
        ImportStage.READING to "Abrindo o arquivo",
        ImportStage.EXTRACTING to "Extraindo o texto no aparelho",
        ImportStage.PARSING to "Lendo datas, valores e parcelas",
        ImportStage.CLASSIFYING to "Classificando, checando duplicatas e assinaturas",
    )
    val currentIndex = stages.indexOfFirst { it.first == stage }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FinaiColors.Emerald)
            Text("Processando a fatura", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            stages.forEachIndexed { index, (_, label) -> StepRow(index + 1, label, done = index < currentIndex) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.RoseSoftBg, RoundedCornerShape(20.dp))
            .background(FinaiColors.RoseSoftBg)
            .padding(16.dp),
    ) {
        Text("Não deu para importar", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.RoseDeep)
        Text(message, fontSize = 13.sp, lineHeight = 17.sp, color = FinaiColors.RoseDeep, modifier = Modifier.padding(top = 6.dp))
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(FinaiColors.RoseDark)
                .clickable(onClick = onReset)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("Escolher outro arquivo", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun SavedCard(state: ImportUiState, onOpenAgenda: () -> Unit, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(20.dp))
            .background(FinaiColors.EmeraldSoftBg)
            .padding(16.dp),
    ) {
        Text(
            "${state.savedCount} lançamento(s) importado(s)",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDeep,
        )
        Text(
            buildString {
                append("Já entraram nos seus números: orçamentos, folga do mês e limites foram recalculados.")
                if (state.savedSubscriptions > 0) {
                    append(" ${state.savedSubscriptions} assinatura(s) nova(s) foram cadastradas na tela Limites.")
                }
            },
            fontSize = 13.sp, lineHeight = 17.sp, color = FinaiColors.EmeraldDeep, modifier = Modifier.padding(top = 6.dp),
        )
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(FinaiColors.EmeraldDark)
                    .clickable(onClick = onOpenAgenda)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("Ver na Agenda", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(FinaiColors.Surface)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("Importar outra", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextBody)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PreviewSummaryCard(
    preview: ImportPreview,
    onSetAllSelected: (Boolean) -> Unit,
    onSetInvoiceReference: (String) -> Unit,
    onSetInvoiceDueDate: (String) -> Unit,
    onReset: () -> Unit,
) {
    var showDueDatePicker by remember { mutableStateOf(false) }
    val dueDate = preview.invoiceMetadata.dueDate
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Text(preview.sourceName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Text(
            "Dados da fatura", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            "Confirme os dados abaixo. Campos ausentes não são inventados.",
            fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
        )
        OutlinedTextField(
            value = preview.invoiceMetadata.reference.orEmpty(),
            onValueChange = onSetInvoiceReference,
            label = { Text("Banco ou cartão") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = dueDate?.let { "%02d/%02d/%04d".format(it.dayOfMonth, it.monthValue, it.year) }.orEmpty(),
                onValueChange = {},
                label = { Text("Vencimento") },
                placeholder = { Text("Selecione a data") },
                singleLine = true,
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showDueDatePicker = true },
            )
        }
        preview.invoiceMetadata.closingDate?.let { closing ->
            Text(
                "Fechamento identificado: %02d/%02d/%04d".format(closing.dayOfMonth, closing.monthValue, closing.year),
                fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 5.dp),
            )
        }
        Text(
            buildString {
                append("${preview.items.size} lançamento(s) encontrados")
                if (preview.usedOcr) append(" · OCR no aparelho")
                if (preview.ignoredLines > 0) append(" · ${preview.ignoredLines} linha(s) ignorada(s)")
            },
            fontSize = 12.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 3.dp),
        )
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (preview.duplicateCount > 0) {
                PillTag("${preview.duplicateCount} duplicada(s)", FinaiColors.AmberSoftBg, FinaiColors.AmberDark)
            }
            if (preview.recurringCount > 0) {
                PillTag("${preview.recurringCount} recorrente(s)", FinaiColors.SurfaceMuted, FinaiColors.TextSecondary)
            }
            if (preview.reviewCount > 0) {
                PillTag("${preview.reviewCount} a revisar", FinaiColors.SurfaceMuted, FinaiColors.TextSecondary)
            }
        }
        preview.aiNote?.let { note ->
            Text(
                "IA indisponível: $note",
                fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (preview.aiUsed) {
            Text(
                "A IA foi usada só nos itens ambíguos, com o nome do estabelecimento, valor e dia — nunca com o arquivo.",
                fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        FlowRow(modifier = Modifier.padding(top = 6.dp).offset(x = (-6).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextAction("Marcar todos", { onSetAllSelected(true) })
            TextAction("Desmarcar todos", { onSetAllSelected(false) }, color = FinaiColors.TextSecondary)
            TextAction("Cancelar", onReset, color = FinaiColors.TextSecondary)
        }
    }

    if (showDueDatePicker) {
        val picker = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            colors = DatePickerDefaults.colors(containerColor = FinaiColors.Surface),
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onSetInvoiceDueDate("%02d/%02d/%04d".format(selected.dayOfMonth, selected.monthValue, selected.year))
                    }
                    showDueDatePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showDueDatePicker = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = picker, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportItemRow(
    item: ImportItem,
    onToggleItem: (String) -> Unit,
    onSetCategory: (String, String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val amountColor = if (item.entry.isRefund) FinaiColors.EmeraldDark else FinaiColors.TextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = { onToggleItem(item.id) },
            colors = CheckboxDefaults.colors(checkedColor = FinaiColors.EmeraldDark),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.entry.description,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatBrl(item.entry.amountCents / 100.0),
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = amountColor,
                )
            }
            Text(
                buildString {
                    append(item.entry.date?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: "sem data")
                    item.entry.installment?.let { append(" · parcela ${it.label}") }
                    if (item.entry.isRefund) append(" · estorno")
                },
                fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
            )

            FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    PillTag(
                        text = item.categoria + if (item.categorySource == CategorySource.AI) " · IA" else "",
                        background = if (item.needsCategoryReview) FinaiColors.AmberSoftBg else FinaiColors.SurfaceMuted,
                        foreground = if (item.needsCategoryReview) FinaiColors.AmberDark else FinaiColors.TextSecondary,
                        modifier = Modifier.heightIn(min = 32.dp).clickable { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Categorias.all.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria) },
                                onClick = {
                                    onSetCategory(item.id, categoria)
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                if (item.recurrence != RecurrenceVerdict.NONE) {
                    IconPill(
                        icon = Icons.Filled.Repeat,
                        label = if (item.recurrence == RecurrenceVerdict.LIKELY) "assinatura" else "talvez assinatura",
                        background = FinaiColors.SurfaceMuted,
                        foreground = FinaiColors.TextSecondary,
                    )
                }
                if (item.duplicate != DuplicateVerdict.NONE) {
                    IconPill(
                        icon = Icons.Filled.ContentCopy,
                        label = if (item.duplicate == DuplicateVerdict.LIKELY) "duplicada" else "talvez duplicada",
                        background = FinaiColors.AmberSoftBg,
                        foreground = FinaiColors.AmberDark,
                    )
                }
            }

            (item.duplicateReason ?: item.recurrenceReason)?.let { reason ->
                Text(reason, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun IconPill(icon: ImageVector, label: String, background: Color, foreground: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(background).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = foreground)
    }
}

@Composable
private fun ConfirmBar(preview: ImportPreview, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val selected = preview.selectedItems
    val totalCents = selected.sumOf { it.entry.amountCents }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(FinaiColors.Ink)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Checklist, contentDescription = null, tint = FinaiColors.TextOnDarkMuted, modifier = Modifier.size(16.dp))
            Text(
                "${selected.size} de ${preview.items.size} selecionados · ${formatBrl(totalCents / 100.0)}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextOnDarkFull,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .heightIn(min = 48.dp)
                .background(if (selected.isEmpty()) FinaiColors.InkBorder else FinaiColors.EmeraldDark)
                .clickable(enabled = selected.isNotEmpty(), onClick = onConfirm)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (selected.isEmpty()) "Selecione ao menos um lançamento" else "Salvar ${selected.size} lançamento(s)",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (selected.isEmpty()) FinaiColors.TextOnDarkMuted else Color.White,
            )
        }
    }
}
