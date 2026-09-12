package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.data.repository.FinanceSeeder
import com.finai.app.domain.BudgetCalculator
import com.finai.app.domain.DebtCalculator
import com.finai.app.domain.GoalCalculator
import com.finai.app.domain.GoalPlan
import com.finai.app.domain.SafeToSpendCalculator
import com.finai.app.domain.SavingsCapacityCalculator
import com.finai.app.domain.SubscriptionCalculator
import com.finai.app.domain.formatMonthYearShort
import com.finai.app.domain.monthKey
import com.finai.app.domain.monthRangeMillis
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.toUiBudget
import com.finai.app.domain.toUiDebt
import com.finai.app.domain.toUiGoal
import com.finai.app.domain.toUiSubscription
import com.finai.app.domain.toUiTimelineEntry
import com.finai.app.domain.toUiWeekBill
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns every number that used to live in `FinaiFixtures` — safe-to-spend,
 * goal progress, savings capacity, debt order/payoff, budget progress — now
 * computed for real from Room via the calculators in `domain/`
 * (planning.md §6, §9 Fase 1). Kept as one app-scoped ViewModel rather than
 * one per screen: with a single local database and no per-screen paging or
 * lifecycle need yet, splitting further would just be five thin wrappers
 * around the same five flows. [AppViewModel] keeps owning the overlay-only
 * UI state (chat, sheets) that has nothing to do with persisted data.
 */
class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(FinaiDatabase.get(application))
    private val prefs = FinaiPreferences(application)

    init {
        viewModelScope.launch {
            val alreadySeeded = prefs.seeded.first()
            if (!alreadySeeded) {
                FinanceSeeder.seedOnce(repository, alreadySeeded = false)
                prefs.setSeeded(true)
            }
        }
    }

    private val mesAtual = monthKey()

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.contas,
        repository.objetivos,
        repository.dividas,
        repository.transacoes,
        repository.assinaturas,
    ) { contas, objetivos, dividas, transacoes, assinaturas ->
        Snapshot(contas, objetivos, dividas, transacoes, assinaturas)
    }.combine(repository.orcamentosDoMes(mesAtual)) { snapshot, orcamentos ->
        buildState(snapshot, orcamentos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    private data class Snapshot(
        val contas: List<ContaEntity>,
        val objetivos: List<ObjetivoEntity>,
        val dividas: List<DividaEntity>,
        val transacoes: List<TransacaoEntity>,
        val assinaturas: List<AssinaturaEntity>,
    )

    private fun buildState(s: Snapshot, orcamentos: List<OrcamentoCategoriaEntity>): FinanceUiState {
        val today = LocalDate.now()
        val monthlyCapacityCents = SavingsCapacityCalculator.monthlyCapacityCents(s.contas, s.dividas)
        val goalPlans = GoalCalculator.plan(s.objetivos, monthlyCapacityCents, today)
        val safe = SafeToSpendCalculator.calculate(s.contas, s.transacoes, aporteMensalMetasCents(goalPlans), today)
        val debtSummary = DebtCalculator.summarize(s.dividas, today)
        val monthRange = monthRangeMillis(today)
        val transacoesDoMes = s.transacoes.filter { it.data in monthRange }
        val budgetProgress = BudgetCalculator.forCategories(orcamentos, transacoesDoMes, today)
        val subscriptionInsights = SubscriptionCalculator.insights(s.assinaturas, today)

        val nextWeekEnd = today.plusDays(7)
        val weekBills = s.contas
            .filter {
                val d = it.vencimento.toLocalDate()
                !d.isBefore(today) && !d.isAfter(nextWeekEnd) && it.status != "pago"
            }
            .sortedBy { it.vencimento }
            .take(6)
            .map { it.toUiWeekBill() }

        val timelineContas = s.contas
            .filter { it.tipo == "a_receber" && !it.recorrente && it.vencimento.toLocalDate().year == Year.now().value }
            .sortedBy { it.vencimento }
        val timelineTotalCents = timelineContas.sumOf { it.valorCentavos }

        val topDebt = debtSummary.ordered.firstOrNull()

        return FinanceUiState(
            loading = false,
            greeting = greetingFor(LocalTime.now()),
            subGreeting = subGreetingFor(weekBills.size),
            rawContas = s.contas,
            rawObjetivos = s.objetivos,
            rawDividas = s.dividas,
            rawOrcamentos = orcamentos,
            rawTransacoesDoMes = transacoesDoMes,
            goals = goalPlans.map { it.toUiGoal() },
            safeToday = safe,
            safeTodayLabel = formatBrl(safe.safeTodayCents / 100.0),
            safeNote = if (safe.slackThisMonthCents >= 0)
                "Sobram ${formatBrl0(safe.slackThisMonthCents / 100.0)} até o dia ${safe.lastDayOfMonth}, já descontadas contas e metas."
            else
                "Você já comprometeu ${formatBrl0(-safe.slackThisMonthCents / 100.0)} a mais do que entra este mês.",
            nextWeekBills = weekBills,
            timeline = timelineContas.map { it.toUiTimelineEntry() },
            timelineNote = if (timelineContas.isEmpty())
                "Nenhuma entrada extra cadastrada para este ano. Lance 13º, bônus ou restituição como conta a receber para vê-los aqui."
            else
                "Somando as entradas extras cadastradas, você tem ${formatBrl0(timelineTotalCents / 100.0)} fora da renda recorrente neste ano.",
            coachTitle = coachTitleFor(transacoesDoMes),
            coachBody = coachBodyFor(transacoesDoMes),
            monthlyCapacityCents = monthlyCapacityCents,
            monthlyCapacityLabel = formatBrl0(monthlyCapacityCents / 100.0),
            allBillsAndIncome = s.contas,
            debts = debtSummary.ordered.map { it.toUiDebt() },
            debtTotalLabel = formatBrl0(debtSummary.totalOpenCents / 100.0),
            debtInterestLabel = formatBrl0(debtSummary.totalMonthlyInterestCents / 100.0),
            debtFreeLabel = when {
                s.dividas.isEmpty() -> "—"
                debtSummary.hasUnpayableDebt -> "Defina parcelas para simular"
                debtSummary.debtFreeDate == null -> "—"
                else -> formatMonthYearShort(debtSummary.debtFreeDate)
            },
            debtStrategyNote = "Ordenado pelo custo do juro, não pelo tamanho da dívida.",
            negotiationTitle = topDebt?.let { "Ligação sobre \"${it.divida.nome}\" — revise antes de fechar" }
                ?: "Nenhuma dívida cadastrada",
            budgets = budgetProgress.map { it.toUiBudget() },
            subscriptions = subscriptionInsights.map { it.assinatura.toUiSubscription(it) },
        )
    }

    private fun aporteMensalMetasCents(plans: List<GoalPlan>): Long =
        plans.sumOf { it.monthlyContributionNeededCents }

    private fun greetingFor(time: LocalTime): String = when {
        time.hour < 12 -> "Bom dia"
        time.hour < 18 -> "Boa tarde"
        else -> "Boa noite"
    }

    private fun subGreetingFor(count: Int): String = when (count) {
        0 -> "Nenhuma conta prevista para os próximos 7 dias."
        1 -> "Você tem 1 conta nos próximos 7 dias."
        else -> "Você tem $count contas nos próximos 7 dias."
    }

    private fun coachTitleFor(transacoesDoMes: List<TransacaoEntity>): String {
        val top = transacoesDoMes.groupBy { it.categoria }.maxByOrNull { (_, list) -> list.sumOf { it.valorCentavos } }
        return if (top == null) "Sem lançamentos este mês ainda"
        else "Maior categoria de gasto: ${top.key}"
    }

    private fun coachBodyFor(transacoesDoMes: List<TransacaoEntity>): String {
        val top = transacoesDoMes.groupBy { it.categoria }.maxByOrNull { (_, list) -> list.sumOf { it.valorCentavos } }
            ?: return "Lance seus gastos para ver o padrão de consumo do mês. A leitura de comportamento com IA chega na Fase 5."
        val total = top.value.sumOf { it.valorCentavos }
        return "${formatBrl0(total / 100.0)} em ${top.value.size} lançamento(s) este mês. A leitura de comportamento com IA chega na Fase 5."
    }

    // ── manual entry: transações ───────────────────────────────
    fun addTransacao(
        descricao: String,
        valorReais: Double,
        categoria: String,
        contaOrigem: String,
        data: LocalDate,
        recorrente: Boolean,
    ) {
        viewModelScope.launch {
            repository.salvarTransacao(
                TransacaoEntity(
                    data = data.toEpochMillis(),
                    descricao = descricao,
                    valorCentavos = Math.round(valorReais * 100),
                    categoria = categoria,
                    contaOrigem = contaOrigem,
                    recorrente = recorrente,
                    origem = "manual",
                ),
            )
        }
    }

    fun deleteTransacao(transacao: TransacaoEntity) = viewModelScope.launch { repository.excluirTransacao(transacao) }

    // ── manual entry: contas ───────────────────────────────────
    fun saveConta(conta: ContaEntity) = viewModelScope.launch { repository.salvarConta(conta) }
    fun deleteConta(conta: ContaEntity) = viewModelScope.launch { repository.excluirConta(conta) }

    // ── manual entry: objetivos ─────────────────────────────────
    fun saveObjetivo(objetivo: ObjetivoEntity) = viewModelScope.launch { repository.salvarObjetivo(objetivo) }
    fun deleteObjetivo(objetivo: ObjetivoEntity) = viewModelScope.launch { repository.excluirObjetivo(objetivo) }

    fun deleteObjetivoById(id: Long) {
        viewModelScope.launch {
            uiState.value.rawObjetivos.firstOrNull { it.id == id }?.let { repository.excluirObjetivo(it) }
        }
    }

    fun contribuirParaObjetivo(objetivoId: Long, valorReais: Double) {
        viewModelScope.launch {
            val objetivo = uiState.value.rawObjetivos.firstOrNull { it.id == objetivoId } ?: return@launch
            repository.salvarObjetivo(objetivo.copy(valorGuardadoCentavos = objetivo.valorGuardadoCentavos + Math.round(valorReais * 100)))
        }
    }

    // ── manual entry: dívidas ───────────────────────────────────
    fun saveDivida(divida: DividaEntity) = viewModelScope.launch { repository.salvarDivida(divida) }
    fun deleteDivida(divida: DividaEntity) = viewModelScope.launch { repository.excluirDivida(divida) }

    fun deleteDividaById(id: Long) {
        viewModelScope.launch {
            uiState.value.rawDividas.firstOrNull { it.id == id }?.let { repository.excluirDivida(it) }
        }
    }

    // ── orçamento ────────────────────────────────────────────────
    fun setBudgetLimit(categoria: String, limiteReais: Double) {
        viewModelScope.launch {
            repository.salvarLimiteOrcamento(OrcamentoCategoriaEntity(categoria, Math.round(limiteReais * 100), mesAtual))
        }
    }

    fun toggleAssinatura(nome: String, ativa: Boolean) {
        viewModelScope.launch {
            val assinatura = repository.assinaturas.first().firstOrNull { it.nome == nome } ?: return@launch
            repository.atualizarAssinatura(assinatura.copy(status = if (ativa) "ativa" else "cancelada"))
        }
    }
}
