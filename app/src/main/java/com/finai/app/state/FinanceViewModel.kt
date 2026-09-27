package com.finai.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.ai.AiResponseCache
import com.finai.app.data.notifications.FinaiNotifier
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.TransactionType
import com.finai.app.domain.transactionsInMonth
import com.finai.app.domain.AlertCalculator
import com.finai.app.domain.BehaviorCoach
import com.finai.app.domain.BudgetCalculator
import com.finai.app.domain.DebtCalculator
import com.finai.app.domain.GoalCalculator
import com.finai.app.domain.GoalPlan
import com.finai.app.domain.SafeToSpendCalculator
import com.finai.app.domain.SafeToSpendResult
import com.finai.app.domain.SavingsCapacityCalculator
import com.finai.app.domain.SubscriptionCalculator
import com.finai.app.domain.formatMonthYearShort
import com.finai.app.domain.monthKey
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.toUiBudget
import com.finai.app.domain.toUiDebt
import com.finai.app.domain.toUiGoal
import com.finai.app.domain.toUiSubscription
import com.finai.app.domain.toUiTimelineEntry
import com.finai.app.domain.toUiWeekBill
import com.finai.app.util.FinaiLog
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    /**
     * Última falha de escrita no Room, para a tela avisar em vez de o app
     * simplesmente não fazer nada (Fase 6). Antes disso, toda escrita rodava
     * num `viewModelScope.launch` sem `try`: uma exceção do SQLite (disco
     * cheio, banco corrompido, migração recusada) virava exceção não tratada
     * na coroutine e derrubava o processo no meio de um lançamento manual.
     */
    private val _persistenceError = MutableStateFlow<String?>(null)
    val persistenceError: StateFlow<String?> = _persistenceError.asStateFlow()

    fun dismissPersistenceError() { _persistenceError.value = null }

    /**
     * Roda uma escrita no Room sem deixar uma falha derrubar o app. [acao] é
     * escrito para caber em "Não foi possível {acao}." e vai tanto para o
     * logcat quanto para a mensagem que o usuário vê — nunca a exceção crua,
     * que pode carregar caminho de arquivo.
     */
    private fun launchSafely(acao: String, block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FinaiLog.e(TAG, "Falha ao $acao", t)
            _persistenceError.value = "Não foi possível $acao. Nada foi alterado — tente de novo."
        }
    }

    private val mesAtual = monthKey()

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.contas,
        repository.objetivos,
        repository.dividas,
        repository.transacoes,
        repository.faturasCartao,
    ) { contas, objetivos, dividas, transacoes, faturasCartao ->
        Snapshot(contas, objetivos, dividas, transacoes, faturasCartao, emptyList())
    }.combine(repository.assinaturas) { snapshot, assinaturas ->
        snapshot.copy(assinaturas = assinaturas)
    }.combine(repository.orcamentosDoMes(mesAtual)) { snapshot, orcamentos ->
        buildState(snapshot, orcamentos)
    }.catch { t ->
        // Uma exceção aqui (leitura do banco, ou um calculator diante de um dado
        // inesperado) cancelaria o StateFlow e levaria o app junto. Em vez disso
        // a tela fica no último estado válido e avisa — os cálculos são
        // determinísticos e testados, então isto é rede de segurança, não
        // caminho esperado.
        FinaiLog.e(TAG, "Falha ao recalcular o estado financeiro", t)
        _persistenceError.value = "Não foi possível atualizar os números agora. Reabra o app se a tela continuar desatualizada."
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    private data class Snapshot(
        val contas: List<ContaEntity>,
        val objetivos: List<ObjetivoEntity>,
        val dividas: List<DividaEntity>,
        val transacoes: List<TransacaoEntity>,
        val faturasCartao: List<FaturaCartaoEntity>,
        val assinaturas: List<AssinaturaEntity>,
    )

    private fun buildState(s: Snapshot, orcamentos: List<OrcamentoCategoriaEntity>): FinanceUiState {
        val today = LocalDate.now()
        val monthlyCapacityCents = SavingsCapacityCalculator.monthlyCapacityCents(s.contas, s.transacoes, s.dividas, today)
        val goalPlans = GoalCalculator.plan(s.objetivos, monthlyCapacityCents, today)
        // Cartão novo: um erro aqui não pode levar a Início inteira junto (o .catch do
        // combine deixaria a tela congelada no estado anterior).
        val payCycle = runCatching { com.finai.app.domain.PayCycle.of(s.contas, s.transacoes, s.dividas, today) }
            .onFailure { FinaiLog.e(TAG, "Falha ao calcular o ciclo do salário", it) }
            .getOrNull()
        val safe = payCycle?.let { SafeToSpendCalculator.fromCycle(it, aporteMensalMetasCents(goalPlans)) }
            ?: SafeToSpendCalculator.calculate(s.contas, s.transacoes, aporteMensalMetasCents(goalPlans), today)
        val debtSummary = DebtCalculator.summarize(s.dividas, today)
        val transacoesDoMes = s.transacoes.transactionsInMonth(today)
        val budgetProgress = BudgetCalculator.forCategories(orcamentos, transacoesDoMes, today)
        val subscriptionInsights = SubscriptionCalculator.insights(s.assinaturas, today)

        val nextWeekEnd = today.plusDays(7)
        // Conta continua sendo o compromisso com data certa; lançamento avulso já
        // cadastrado com data futura (ex.: salário do dia 30) também é "esperado nos
        // próximos 7 dias" do ponto de vista do usuário — sem isto essa seção ficava
        // vazia pra quem lança tudo pelo "+" central em vez de "Nova conta".
        val weekFromContas = s.contas
            .filter {
                val d = it.vencimento.toLocalDate()
                !d.isBefore(today) && !d.isAfter(nextWeekEnd) && it.status != "pago"
            }
            .map { it.vencimento.toLocalDate() to it.toUiWeekBill(today) }
        val weekFromTransacoes = s.transacoes
            .filter {
                // Item de fatura tem a data da compra, não do pagamento: quem vence é a
                // fatura (a ContaEntity dela, já em weekFromContas), não cada compra.
                it.tipo != TransactionType.Transferencia.name && !it.recorrente && it.faturaId == null &&
                    it.data.toLocalDate().let { d -> !d.isBefore(today) && !d.isAfter(nextWeekEnd) }
            }
            .map { it.data.toLocalDate() to it.toUiWeekBill(today) }
        val weekBills = (weekFromContas + weekFromTransacoes)
            .sortedBy { it.first }
            .take(6)
            .map { it.second }

        val timelineExtras = com.finai.app.domain.ExtraIncomeTimeline.forYear(s.contas, s.transacoes, Year.now().value)
        val timelineTotalCents = timelineExtras.sumOf { it.cents }

        val topDebt = debtSummary.ordered.firstOrNull()
        // Mesma conta dos totais da Agenda do mês (recebimentos − contas a pagar).
        val saldoCents = com.finai.app.domain.MonthCashFlow.of(
            s.contas, s.transacoes, s.dividas, java.time.YearMonth.from(today), today,
        ).saldoCents

        return FinanceUiState(
            loading = false,
            greeting = greetingFor(LocalTime.now()),
            subGreeting = subGreetingFor(weekBills.size),
            rawContas = s.contas,
            rawFaturasCartao = s.faturasCartao,
            rawObjetivos = s.objetivos,
            rawDividas = s.dividas,
            rawOrcamentos = orcamentos,
            rawTransacoesDoMes = transacoesDoMes,
            rawTransacoes = s.transacoes,
            goals = goalPlans.map { it.toUiGoal() },
            safeToday = safe,
            safeTodayLabel = formatBrl(safe.safeTodayCents / 100.0),
            saldoCents = saldoCents,
            saldoLabel = formatBrl(saldoCents / 100.0),
            payCycle = payCycle,
            safeNote = safeNoteFor(safe, payCycle != null, aporteMensalMetasCents(goalPlans)),
            nextWeekBills = weekBills,
            timeline = timelineExtras.map { it.toUiTimelineEntry() },
            timelineNote = if (timelineExtras.isEmpty())
                "Nenhuma entrada extra neste ano. Toque em + para lançar 13º, bônus ou restituição — ou marque uma receita já lançada como extra na Agenda."
            else
                "Somando as entradas extras cadastradas, você tem ${formatBrl0(timelineTotalCents / 100.0)} fora da renda recorrente neste ano.",
            behaviorPattern = BehaviorCoach.detect(s.transacoes, today).firstOrNull(),
            monthlyCapacityCents = monthlyCapacityCents,
            monthlyCapacityLabel = formatBrl0(monthlyCapacityCents / 100.0),
            goalsNeededCents = goalPlans.sumOf { it.monthlyContributionNeededCents },
            goalsFundedCents = goalPlans.sumOf { it.monthlyContributionFundedCents },
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
            alerts = AlertCalculator.alerts(s.contas, budgetProgress, subscriptionInsights, goalPlans, today),
        )
    }

    /**
     * Separa "falta dinheiro para as contas" de "a meta não cabe". Antes as duas
     * coisas viravam um único "Faltam R$ X para cobrir contas e metas", e a IA
     * lia o aporte das metas como um rombo no mês.
     */
    private fun safeNoteFor(safe: SafeToSpendResult, byCycle: Boolean, reservedForGoalsCents: Long): String {
        val until = if (byCycle) "até o salário do dia ${safe.lastDayOfMonth}" else "até o dia ${safe.lastDayOfMonth}"
        val billsSlack = safe.slackThisMonthCents + reservedForGoalsCents
        return when {
            billsSlack < 0 ->
                "Faltam ${formatBrl0(-billsSlack / 100.0)} para cobrir as contas $until."
            safe.slackThisMonthCents < 0 ->
                "As contas estão cobertas $until, mas o aporte das metas não cabe inteiro agora."
            reservedForGoalsCents > 0 ->
                "Sobram ${formatBrl0(safe.slackThisMonthCents / 100.0)} $until, já descontadas as contas e o aporte das metas."
            else ->
                "Sobram ${formatBrl0(safe.slackThisMonthCents / 100.0)} $until, já descontadas as contas."
        }
    }

    private fun aporteMensalMetasCents(plans: List<GoalPlan>): Long =
        plans.sumOf { it.monthlyContributionFundedCents }

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

    // ── manual entry: transações ───────────────────────────────
    fun addTransacao(
        descricao: String,
        valorReais: Double,
        categoria: String,
        contaOrigem: String,
        data: LocalDate,
        recorrente: Boolean,
    ) {
        launchSafely("salvar o lançamento") {
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

    suspend fun saveTransactionEntry(entry: TransacaoEntity) {
        require(entry.valorCentavos > 0 && entry.categoria.isNotBlank())
        repository.salvarTransacao(entry)
    }

    /** Marca/desmarca uma receita já lançada como entrada extra (Linha do tempo do ano). */
    fun setTransacaoExtra(transacao: TransacaoEntity, extra: Boolean) = launchSafely("atualizar o lançamento") {
        repository.salvarTransacao(transacao.copy(extra = extra, recorrente = if (extra) false else transacao.recorrente))
    }

    fun deleteTransacao(transacao: TransacaoEntity) = launchSafely("excluir o lançamento") { repository.excluirTransacao(transacao) }

    // ── manual entry: contas ───────────────────────────────────
    fun saveConta(conta: ContaEntity) = launchSafely("salvar a conta") { repository.salvarConta(conta) }
    fun deleteConta(conta: ContaEntity) = launchSafely("excluir a conta") { repository.excluirConta(conta) }

    /**
     * Marca uma conta como paga/recebida (ou desfaz). Sem isto não havia
     * nenhuma ação na UI para uma conta sair de "pendente". Fechado na Fase 5
     * porque ela depende disso para os eventos serem testáveis de verdade
     * (uma conta atrasada só some dos avisos quando é paga; uma "entrada
     * extra confirmada" só existe quando um recebimento é marcado).
     */
    fun marcarContaPaga(contaId: Long, pago: Boolean) {
        launchSafely("atualizar a conta") {
            val conta = uiState.value.rawContas.firstOrNull { it.id == contaId } ?: return@launchSafely
            repository.salvarConta(conta.copy(status = if (pago) "pago" else "pendente"))
        }
    }

    // ── manual entry: objetivos ─────────────────────────────────
    fun saveObjetivo(objetivo: ObjetivoEntity) = launchSafely("salvar o objetivo") { repository.salvarObjetivo(objetivo) }
    fun deleteObjetivo(objetivo: ObjetivoEntity) = launchSafely("excluir o objetivo") { repository.excluirObjetivo(objetivo) }

    fun deleteObjetivoById(id: Long) {
        launchSafely("excluir o objetivo") {
            uiState.value.rawObjetivos.firstOrNull { it.id == id }?.let { repository.excluirObjetivo(it) }
        }
    }

    fun contribuirParaObjetivo(objetivoId: Long, valorReais: Double) {
        launchSafely("registrar a contribuição") {
            val objetivo = uiState.value.rawObjetivos.firstOrNull { it.id == objetivoId } ?: return@launchSafely
            repository.salvarObjetivo(objetivo.copy(valorGuardadoCentavos = objetivo.valorGuardadoCentavos + Math.round(valorReais * 100)))
        }
    }

    // ── manual entry: dívidas ───────────────────────────────────
    fun saveDivida(divida: DividaEntity) = launchSafely("salvar a dívida") { repository.salvarDivida(divida) }
    fun deleteDivida(divida: DividaEntity) = launchSafely("excluir a dívida") { repository.excluirDivida(divida) }

    fun deleteDividaById(id: Long) {
        launchSafely("excluir a dívida") {
            uiState.value.rawDividas.firstOrNull { it.id == id }?.let { repository.excluirDivida(it) }
        }
    }

    /**
     * Registra o pagamento de uma parcela: some uma parcela de [DividaEntity.parcelasRestantes]
     * e desconta [DividaEntity.valorParcelaCentavos] do valor em aberto. Sem isto não havia
     * nenhuma forma de a dívida avançar conforme o usuário paga — ficava travada no valor
     * cadastrado no dia da negociação para sempre.
     */
    fun pagarParcela(dividaId: Long) {
        launchSafely("registrar pagamento da parcela") {
            val divida = uiState.value.rawDividas.firstOrNull { it.id == dividaId } ?: return@launchSafely
            if (divida.parcelasRestantes <= 0) return@launchSafely
            val hoje = LocalDate.now()
            val numero = if (divida.parcelasTotais > 0) divida.parcelasTotais - divida.parcelasRestantes + 1 else null
            // O pagamento vira um gasto do dia: a parcela sai da projeção (afterPayment) e passa
            // a contar por aqui, então o saldo do mês e os totais da Agenda não "devolvem" o valor.
            repository.salvarTransacao(
                TransacaoEntity(
                    data = hoje.toEpochMillis(),
                    descricao = if (numero != null) "${divida.nome} · parcela $numero de ${divida.parcelasTotais}" else "${divida.nome} · parcela",
                    valorCentavos = minOf(divida.valorParcelaCentavos, divida.valorAbertoCentavos).coerceAtLeast(0),
                    categoria = com.finai.app.domain.DebtSchedule.PAYMENT_CATEGORY,
                    contaOrigem = "",
                    recorrente = false,
                    origem = com.finai.app.domain.DebtSchedule.PAYMENT_ORIGIN,
                ),
            )
            // Avança o vencimento junto: a Agenda projeta as parcelas seguintes a partir dele.
            repository.salvarDivida(com.finai.app.domain.DebtSchedule.afterPayment(divida, hoje))
        }
    }

    // ── orçamento ────────────────────────────────────────────────
    fun setBudgetLimit(categoria: String, limiteReais: Double) {
        launchSafely("salvar o limite da categoria") {
            repository.salvarLimiteOrcamento(OrcamentoCategoriaEntity(categoria, Math.round(limiteReais * 100), mesAtual))
        }
    }

    fun toggleAssinatura(nome: String, ativa: Boolean) {
        launchSafely("atualizar a assinatura") {
            val assinatura = repository.assinaturas.first().firstOrNull { it.nome == nome } ?: return@launchSafely
            repository.atualizarAssinatura(assinatura.copy(status = if (ativa) "ativa" else "cancelada"))
        }
    }

    // ── Configurações: apagar todos os dados ────────────────────
    /**
     * "Apagar todos os dados" (Configurações). Limpa o Room e as duas
     * ligações de estado derivado que não moram nele — o cache de respostas
     * de IA em memória e as notificações já entregues na barra do sistema —
     * e só então avisa [onDone] (que reinicia o estado de memoização da IA e
     * volta o app para o fluxo de onboarding). Não mexe em chave de provedor
     * nem em contagem de cota: são configuração técnica, não dado financeiro.
     */
    fun apagarTodosOsDados(onDone: () -> Unit = {}) {
        launchSafely("apagar todos os dados") {
            repository.apagarTodosOsDados()
            AiResponseCache.clear()
            FinaiNotifier(getApplication()).cancelAll()
            onDone()
        }
    }

    private companion object {
        const val TAG = "FinanceViewModel"
    }
}
