package com.finai.app.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.importer.ImportOutcome
import com.finai.app.data.importer.ImportStage
import com.finai.app.data.importer.StatementImporter
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.importer.CategorySource
import com.finai.app.domain.importer.ImportItem
import com.finai.app.domain.importer.ImportPreview
import com.finai.app.domain.importer.RecurrenceVerdict
import com.finai.app.domain.toEpochMillis
import com.finai.app.util.FinaiLog
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado da tela de importação de fatura (Fase 4, planning.md §3.6/§9).
 *
 * Segue a mesma divisão do resto do app: nenhum parsing, OCR ou chamada de IA
 * acontece aqui — isso é do [StatementImporter] e de `domain/importer`. Este
 * ViewModel só guarda o que a tela mostra, aplica as edições do usuário na
 * revisão e, na confirmação, grava no Room, que continua sendo a fonte de
 * verdade dos números.
 */
data class ImportUiState(
    val stage: ImportStage? = null,
    val busy: Boolean = false,
    val preview: ImportPreview? = null,
    val error: String? = null,
    /** Preenchido depois de salvar, para a tela confirmar o que entrou. */
    val savedCount: Int? = null,
    val savedSubscriptions: Int = 0,
)

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(FinaiDatabase.get(application))
    private val importer = StatementImporter(application)

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState

    private var importJob: Job? = null

    fun importDocument(uri: Uri) {
        importJob?.cancel()
        _uiState.value = ImportUiState(busy = true, stage = ImportStage.READING)
        importJob = viewModelScope.launch {
            try {
                val existing = repository.transacoes.first()
                when (val outcome = importer.import(uri, existing, onStage = ::onStage)) {
                    is ImportOutcome.Success ->
                        _uiState.value = ImportUiState(preview = outcome.preview, busy = false, stage = null)
                    is ImportOutcome.Failure ->
                        _uiState.value = ImportUiState(error = outcome.message, busy = false, stage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // O StatementImporter já converte o que sabe prever em
                // ImportOutcome.Failure; isto cobre o resto (falha ao ler o Room,
                // OutOfMemory fora do bloco de extração) sem derrubar o app com a
                // tela de importação aberta — Fase 6.
                FinaiLog.e(TAG, "Falha inesperada ao importar a fatura", t)
                _uiState.value = ImportUiState(
                    error = "Não foi possível processar esse arquivo. Tente outro formato (CSV ou PDF) ou um arquivo menor.",
                    busy = false,
                    stage = null,
                )
            }
        }
    }

    private fun onStage(stage: ImportStage) = _uiState.update { it.copy(stage = stage) }

    // ── edições na revisão ───────────────────────────────────────────────

    fun toggleItem(id: String) = updateItem(id) { it.copy(selected = !it.selected) }

    fun setCategory(id: String, categoria: String) = updateItem(id) {
        it.copy(categoria = categoria, categorySource = CategorySource.USER, needsCategoryReview = false)
    }

    fun setAllSelected(selected: Boolean) = updatePreview { preview ->
        preview.copy(items = preview.items.map { it.copy(selected = selected) })
    }

    private fun updateItem(id: String, transform: (ImportItem) -> ImportItem) = updatePreview { preview ->
        preview.copy(items = preview.items.map { if (it.id == id) transform(it) else it })
    }

    private fun updatePreview(transform: (ImportPreview) -> ImportPreview) = _uiState.update { state ->
        state.preview?.let { state.copy(preview = transform(it)) } ?: state
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun reset() {
        importJob?.cancel()
        _uiState.value = ImportUiState()
    }

    // ── persistência ─────────────────────────────────────────────────────

    /**
     * Grava os lançamentos marcados como `origem = "importado"`. Item marcado
     * como assinatura recorrente também vira (ou atualiza) uma
     * [AssinaturaEntity], que é o que alimenta a seção de assinaturas da tela
     * Limites — sem duplicar uma assinatura que já existia com o mesmo nome.
     */
    fun confirmImport() {
        val preview = _uiState.value.preview ?: return
        val selected = preview.selectedItems
        if (selected.isEmpty()) return

        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                confirmImportInternal(preview, selected)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Fase 6: uma falha de escrita no meio da confirmação deixava o
                // app cair com parte dos lançamentos já gravados. Agora a tela
                // volta para a revisão com o aviso, e o usuário pode tentar de
                // novo — reimportar o mesmo arquivo sinaliza como duplicata o que
                // já tiver entrado, então nada é perdido nem duplicado em silêncio.
                FinaiLog.e(TAG, "Falha ao gravar os lançamentos importados", t)
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = "Não foi possível salvar os lançamentos. Confira o espaço livre do aparelho e tente de novo.",
                    )
                }
            }
        }
    }

    private suspend fun confirmImportInternal(preview: ImportPreview, selected: List<ImportItem>) {
        val origem = preview.sourceName.substringBeforeLast('.').take(40)
        val existingSubscriptions = repository.assinaturas.first()
        var subscriptionsSaved = 0

        selected.forEach { item ->
            repository.salvarTransacao(
                TransacaoEntity(
                    data = (item.entry.date ?: LocalDate.now()).toEpochMillis(),
                    descricao = describeForStorage(item),
                    valorCentavos = item.entry.amountCents,
                    categoria = item.categoria,
                    contaOrigem = origem,
                    recorrente = item.recurrence == RecurrenceVerdict.LIKELY || item.entry.installment != null,
                    origem = "importado",
                ),
            )

            if (item.recurrence == RecurrenceVerdict.LIKELY && item.entry.installment == null && !item.entry.isRefund) {
                val nome = item.entry.description.trim().take(40)
                val already = existingSubscriptions.firstOrNull { it.nome.equals(nome, ignoreCase = true) }
                repository.salvarAssinatura(
                    AssinaturaEntity(
                        id = already?.id ?: 0,
                        nome = nome,
                        valorCentavos = Math.abs(item.entry.amountCents),
                        ultimoUso = item.entry.date?.toEpochMillis(),
                        status = already?.status ?: "ativa",
                    ),
                )
                if (already == null) subscriptionsSaved++
            }
        }

        _uiState.value = ImportUiState(savedCount = selected.size, savedSubscriptions = subscriptionsSaved)
    }

    private companion object {
        const val TAG = "ImportViewModel"
    }

    private fun describeForStorage(item: ImportItem): String {
        val installment = item.entry.installment ?: return item.entry.description.trim()
        return "${item.entry.description.trim()} (${installment.label})"
    }
}
