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
import com.finai.app.domain.FinancialPlan
import com.finai.app.domain.SafeToSpendResult
import com.finai.app.domain.Celebration
import com.finai.app.domain.CelebrationStyle
import com.finai.app.domain.Completion
import com.finai.app.domain.toPaidDebt
import com.finai.app.domain.celebrationStyleFor
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
    private val prefs = com.finai.app.data.prefs.FinaiPreferences(application)

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

    /** Meta concluída / dívida quitada agora há pouco — desenhada por cima de tudo em FinaiApp. */
    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    fun dismissCelebration() { _celebration.value = null }

    private fun celebrateGoal(objetivo: ObjetivoEntity) {
        val extra = objetivo.valorGuardadoCentavos - objetivo.valorAlvoCentavos
        _celebration.value = Celebration(
            style = celebrationStyleFor(objetivo.tipo),
            title = if (celebrationStyleFor(objetivo.tipo) == CelebrationStyle.Travel) "Viagem garantida!" else "Meta concluída!",
            name = objetivo.nome,
            detail = "${formatBrl0(objetivo.valorGuardadoCentavos / 100.0)} guardados" +
                if (extra > 0) " · ${formatBrl0(extra / 100.0)} além do alvo" else "",
        )
    }

    private fun celebrateDebt(divida: DividaEntity) {
        _celebration.value = Celebration(
            style = CelebrationStyle.DebtFree,
            title = "Dívida quitada!",
            name = divida.nome,
            detail = if (divida.valorOriginalCentavos > 0) "${formatBrl0(divida.valorOriginalCentavos / 100.0)} pagos até o fim" else "Uma a menos na lista",
        )
    }

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
        snapshot to orcamentos
    }.combine(prefs.dismissedInsights) { (snapshot, orcamentos), dismissed ->
        buildState(snapshot, orcamentos, dismissed)
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

    private fun buildState(s: Snapshot, orcamentos: List<OrcamentoCategoriaEntity>, dismissed: Set<String> = emptySet()): FinanceUiState {
        val today = LocalDate.now()
        // Uma conta só, lida por todas as telas e pela IA: ciclo dia a dia, disponível e o
        // destino único da sobra (dívida cara ou metas).
        val plan = FinancialPlan.build(s.contas, s.transacoes, s.dividas, s.objetivos, today) {
            FinaiLog.e(TAG, "Falha ao calcular o ciclo do salário", it)
        }
        // Sem nenhuma entrada lançada não há sobra a projetar: a visão das metas não compara com orçamento.
        val hasIncome = s.transacoes.any { it.tipo == TransactionType.Receita.name && it.valorCentavos > 0 } ||
            s.contas.any { it.tipo == "a_receber" }
        val goalsOverview = com.finai.app.domain.GoalPlanning.overview(plan, hasIncome, today)
        // O que o usuário dispensou some da tela e da lista "já vê" da IA; a decisão de manter
        // metas separadas vai para a IA como fato.
        val (goalInsights, dismissedInsightCount) = com.finai.app.domain.GoalPlanning.visible(
            com.finai.app.domain.GoalPlanning.insights(plan, goalsOverview, s.dividas, hasIncome, today), dismissed,
        )
        // Agenda sequencial: os mesmos ciclos do plano, em ordem. Falha aqui não pode levar o resto junto.
        val cashFlow = runCatching {
            com.finai.app.domain.CashFlowTimeline.build(s.contas, s.transacoes, s.dividas, today, plan.cycle)
        }.onFailure { FinaiLog.e(TAG, "Falha ao montar a Agenda sequencial", it) }.getOrNull()
        val monthlyCapacityCents = plan.capacidadeMensalCents
        val savingsProjection = plan.projection
        val goalPlans = plan.goals
        val payCycle = plan.cycle
        val safe = plan.safe
        // Quitadas saem da estratégia, do total e da negociação; ficam só no histórico.
        val paidDividas = s.dividas.filter(Completion::isPaid)
        val debtSummary = plan.debts
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
        val uiBudgets = budgetProgress.map { it.toUiBudget() }
        val uiSubscriptions = subscriptionInsights.map { it.assinatura.toUiSubscription(it) }
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
            goals = goalPlans.filterNot { Completion.isDone(it.objetivo) }.map {
                it.toUiGoal(plan.goalNote(it), plan.forGoal(it.objetivo.id), plan.today, capacityKnown = hasIncome, planShort = plan.goalNoteShort(it))
            },
            completedGoals = goalPlans.filter { Completion.isDone(it.objetivo) }
                .sortedByDescending { it.objetivo.concluidoEm ?: 0L }
                .map { it.toUiGoal(today = plan.today) },
            paidDebts = paidDividas.sortedByDescending { it.quitadaEm ?: 0L }.map { it.toPaidDebt() },
            safeToday = safe,
            safeTodayLabel = formatBrl(safe.safeTodayCents / 100.0),
            saldoCents = saldoCents,
            saldoLabel = formatBrl(saldoCents / 100.0),
            payCycle = payCycle,
            safeNote = safeNoteFor(safe, payCycle, plan),
            spendExplanation = com.finai.app.domain.SpendExplanation.of(safe, payCycle, today, plan.recommendationSummary.takeIf { plan.recommendations.isNotEmpty() }),
            plan = plan,
            situation = com.finai.app.domain.HomeSituation.of(safe, payCycle),
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
            savingsProjection = savingsProjection,
            goalsFundedCents = goalPlans.sumOf { it.monthlyContributionFundedCents },
            goalsOverview = goalsOverview,
            averageDailySpendingCents = com.finai.app.domain.GoalFlow.averageDailySpendingCents(s.contas, s.transacoes, s.dividas, today),
            goalInsights = goalInsights,
            dismissedInsightCount = dismissedInsightCount,
            goalFacts = com.finai.app.domain.GoalPlanning.factsBlock(plan, goalsOverview, goalInsights, s.dividas, hasIncome, today, dismissed),
            allBillsAndIncome = s.contas,
            debts = debtSummary.ordered.map { it.toUiDebt(plan.debtNote(it.divida)) },
            debtTotalLabel = formatBrl0(debtSummary.totalOpenCents / 100.0),
            debtInterestLabel = formatBrl0(debtSummary.totalMonthlyInterestCents / 100.0),
            debtFreeLabel = when {
                s.dividas.isEmpty() -> "—"
                debtSummary.hasUnpayableDebt -> "Defina parcelas para simular"
                debtSummary.debtFreeDate == null -> "—"
                else -> formatMonthYearShort(debtSummary.debtFreeDate)
            },
            debtStrategyNote = "Ordenado pelo custo do juro, não pelo tamanho da dívida. " + when {
                plan.recommendations.any { it.target == com.finai.app.domain.RecommendationTarget.Debt } -> plan.recommendationSummary
                debtSummary.ordered.any { it.divida.taxaJurosMensalBasisPoints >= FinancialPlan.EXPENSIVE_DEBT_BASIS_POINTS } ->
                    "Nada livre ${plan.untilLabel}; quando houver, a recomendação é amortizar primeiro \"${debtSummary.ordered.first().divida.nome}\"."
                debtSummary.ordered.isNotEmpty() -> "Nenhuma passa de ${FinancialPlan.EXPENSIVE_DEBT_BASIS_POINTS / 100}% ao mês: o plano recomenda o livre para as metas, e aqui basta pagar as parcelas."
                else -> ""
            },
            negotiationTitle = topDebt?.let { "Ligação sobre \"${it.divida.nome}\" — revise antes de fechar" }
                ?: "Nenhuma dívida cadastrada",
            budgets = uiBudgets,
            subscriptions = uiSubscriptions,
            decisions = plan.decisions(
                budgetsOver = uiBudgets.filter { it.spent > it.limit }.map { it.name },
                unusedSubscriptions = uiSubscriptions.filter { it.cta == "Cancelar" }.map { it.name },
            ),
            aiDebtsBlock = com.finai.app.domain.AiContext.debtsBlock(debtSummary.ordered, today),
            aiGoalsBlock = com.finai.app.domain.AiContext.goalsBlock(goalPlans.filterNot { Completion.isDone(it.objetivo) }),
            aiPlanBlock = plan.aiBlock(),
            cashFlow = cashFlow,
            aiCashFlowBlock = cashFlow?.aiBlock().orEmpty(),
            alerts = AlertCalculator.alerts(s.contas, budgetProgress, subscriptionInsights, goalPlans, today, payCycle?.shortfall),
        )
    }

    /**
     * Uma frase só, na ordem de urgência: falta prevista antes de tudo (com quando e por
     * quê), depois "a meta não cabe", depois quanto sobra. Separa "falta dinheiro para as
     * contas" de "a meta não cabe" — antes as duas viravam um único "Faltam R$ X", e a IA
     * lia o aporte das metas como um rombo no mês.
     */
    private fun safeNoteFor(safe: SafeToSpendResult, cycle: com.finai.app.domain.PayCycle?, plan: FinancialPlan): String {
        val until = if (cycle != null) "até o salário do dia ${safe.lastDayOfMonth}" else "até o dia ${safe.lastDayOfMonth}"
        safe.shortfall?.let { falta ->
            val valor = formatBrl0(falta.cents / 100.0)
            val porque = falta.causa?.let { " — $it" }.orEmpty()
            val today = cycle?.today ?: LocalDate.now()
            return if (falta.date.isAfter(today))
                "Em ${dayMonth(falta.date)} faltam $valor$porque, antes do salário do dia ${safe.lastDayOfMonth}. Não há valor livre para gastar até lá."
            else
                "O dinheiro já não fecha: faltam $valor$porque $until. Não há valor livre para gastar até lá."
        }
        val descontado = "já descontados os compromissos" + if (plan.reservadoFuturoCents > 0) " e a reserva necessária" else ""
        val recomendacao = plan.recomendacao?.let { " Recomendação do plano: usar ${formatBrl0(it.cents / 100.0)} em ${it.name} (continua livre até você registrar)." }.orEmpty()
        val floor = safe.floor
        // O dia mais apertado vem antes do fim do ciclo: o valor é o que sobra nele, não no fim.
        return if (cycle != null && floor != null && floor.cents < cycle.livreCents && floor.date.isAfter(cycle.today))
            "Sobram ${formatBrl0(safe.slackThisMonthCents.coerceAtLeast(0) / 100.0)} $until contando o dia mais apertado (${dayMonth(floor.date)}), $descontado.$recomendacao"
        else
            "Sobram ${formatBrl0(safe.slackThisMonthCents.coerceAtLeast(0) / 100.0)} $until, $descontado.$recomendacao"
    }

    private fun dayMonth(d: LocalDate): String = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

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

    /**
     * "Registrar compra" do chat: a ação separada, depois de o usuário confirmar, que grava o que a
     * simulação só projetou ([com.finai.app.domain.scenario.PurchaseScenario.toRecords]). Simular
     * nunca passa por aqui. [onDone] só roda depois de o Room gravar.
     */
    fun registrarCompra(scenario: com.finai.app.domain.scenario.PurchaseScenario, onDone: () -> Unit) =
        launchSafely("registrar a compra") {
            repository.salvarTransacoes(scenario.toRecords())
            onDone()
        }

    /** Assistente inicial: grava renda principal e contas da semana; falha sobe para a tela avisar. */
    suspend fun saveInitialSetup(entries: List<TransacaoEntity>) {
        entries.forEach { repository.salvarTransacao(it) }
    }

    // ── lançamento do "+": cada gravação devolve o recibo que o "Desfazer" usa ──
    suspend fun saveTransactionEntry(entry: TransacaoEntity): com.finai.app.domain.EntryReceipt {
        require(entry.valorCentavos > 0 && entry.categoria.isNotBlank())
        return repository.salvarLancamento(entry)
    }

    suspend fun saveCardPurchase(entry: TransacaoEntity, payment: com.finai.app.domain.CardPayment): com.finai.app.domain.EntryReceipt {
        require(entry.valorCentavos > 0 && entry.categoria.isNotBlank())
        return repository.salvarCompraCartao(entry, payment)
    }

    /** Transferência para objetivo/reserva. A comemoração, se concluir a meta, espera a confirmação fechar. */
    suspend fun saveContribution(entry: TransacaoEntity): com.finai.app.domain.EntryReceipt {
        val receipt = repository.registrarAporte(entry)
        deferredCelebration = receipt.goal?.takeIf { it.celebrate }
            ?.let { g -> uiState.value.rawObjetivos.firstOrNull { it.id == g.goalId }?.copy(valorGuardadoCentavos = g.afterCents) }
        return receipt
    }

    /** "Desfazer" da confirmação: reverte tudo o que o recibo gravou, numa transação. */
    suspend fun undoEntry(receipt: com.finai.app.domain.EntryReceipt) {
        deferredCelebration = null
        repository.desfazer(receipt)
    }

    /** Marca/desmarca uma receita já lançada como entrada extra (Linha do tempo do ano). */
    fun setTransacaoExtra(transacao: TransacaoEntity, extra: Boolean) = launchSafely("atualizar o lançamento") {
        val original = originalOf(transacao)
        repository.salvarTransacao(
            original.copy(
                extra = extra,
                recorrente = if (extra) false else original.recorrente,
                rendaPrincipal = if (extra) false else original.rendaPrincipal,
            ),
        )
    }

    /**
     * Excluir o gasto de um pagamento de parcela ("Tablet · parcela 9 de 12") desfaz o
     * pagamento: a parcela volta para a dívida. Era o único jeito de corrigir um "Paguei a
     * parcela" tocado por engano — antes, excluir o gasto sumia com o valor e a dívida
     * continuava avançada.
     */
    fun deleteTransacao(transacao: TransacaoEntity) = launchSafely("excluir o lançamento") {
        repository.excluirTransacao(transacao)
        val nome = com.finai.app.domain.DebtSchedule.debtNameOf(transacao) ?: return@launchSafely
        val divida = uiState.value.rawDividas.firstOrNull { it.nome.trim() == nome } ?: return@launchSafely
        val update = Completion.onDebtSaved(divida, com.finai.app.domain.DebtSchedule.undoPayment(divida, transacao), System.currentTimeMillis())
        repository.salvarDivida(update.divida)
    }

    /**
     * Marca/desmarca uma receita como renda principal — é o que define o ciclo "até o próximo
     * salário" (Fase 7, item 5). Renda principal é recorrente por natureza e nunca é extra.
     */
    fun setTransacaoRendaPrincipal(transacao: TransacaoEntity, principal: Boolean) = launchSafely("atualizar o lançamento") {
        val original = originalOf(transacao)
        repository.salvarTransacao(original.copy(rendaPrincipal = principal, extra = if (principal) false else original.extra))
    }

    // ── edição pela Agenda ─────────────────────────────────────
    /**
     * Salva a edição de um lançamento (o formulário do "+" reaberto preenchido). [month] é o mês
     * da ocorrência; [scope] só existe para recorrente ("Somente este mês" / "Este mês e os
     * próximos"). Lança [IllegalArgumentException] com a mensagem para a tela quando a edição
     * não é permitida. Devolve o id da linha que a Agenda destaca.
     */
    suspend fun editTransaction(id: Long, month: java.time.YearMonth, change: com.finai.app.domain.EntryChange, scope: com.finai.app.domain.EditScope?): Long =
        repository.editarLancamento(id, month, change, scope)

    /** Excluir uma ocorrência de recorrente só neste mês, ou dela em diante. */
    fun deleteOccurrence(transacao: TransacaoEntity, scope: com.finai.app.domain.EditScope) = launchSafely("excluir o lançamento") {
        repository.excluirOcorrencia(transacao.id, com.finai.app.domain.RecurringSeries.referenceMonth(transacao), scope)
    }

    /**
     * "Editar parcela": muda o vencimento só da parcela de [month]. As outras parcelas e o dia
     * combinado da dívida não mudam.
     */
    suspend fun adjustInstallmentDate(dividaId: Long, month: java.time.YearMonth, date: LocalDate) {
        val divida = uiState.value.rawDividas.firstOrNull { it.id == dividaId }
            ?: throw IllegalArgumentException("Essa dívida não existe mais.")
        repository.salvarDivida(com.finai.app.domain.DebtSchedule.withInstallmentDate(divida, month, date, LocalDate.now()))
    }

    /** "Mudar o dia desta e das próximas": as parcelas anteriores ficam na data que tinham. */
    suspend fun changeInstallmentDayFrom(dividaId: Long, month: java.time.YearMonth, day: Int) {
        val divida = uiState.value.rawDividas.firstOrNull { it.id == dividaId }
            ?: throw IllegalArgumentException("Essa dívida não existe mais.")
        repository.salvarDivida(com.finai.app.domain.DebtSchedule.withDueDayFrom(divida, month, day, LocalDate.now()))
    }

    /**
     * A Agenda mostra a ocorrência do mês de um recorrente: mesmo id, mas com a data trocada
     * ([com.finai.app.domain.transactionsInMonth]). Gravar essa cópia moveria o início da
     * série para o mês exibido; por isso a edição parte do registro guardado.
     */
    private fun originalOf(transacao: TransacaoEntity): TransacaoEntity =
        uiState.value.rawTransacoes.firstOrNull { it.id == transacao.id } ?: transacao

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
    fun saveObjetivo(objetivo: ObjetivoEntity) = launchSafely("salvar o objetivo") {
        val before = uiState.value.rawObjetivos.firstOrNull { it.id == objetivo.id && objetivo.id != 0L }
        val update = Completion.onGoalSaved(before, objetivo, System.currentTimeMillis())
        repository.salvarObjetivo(update.objetivo)
        if (update.celebrate) celebrateGoal(update.objetivo)
    }
    fun deleteObjetivo(objetivo: ObjetivoEntity) = launchSafely("excluir o objetivo") { repository.excluirObjetivo(objetivo) }

    fun deleteObjetivoById(id: Long) {
        launchSafely("excluir o objetivo") {
            uiState.value.rawObjetivos.firstOrNull { it.id == id }?.let { repository.excluirObjetivo(it) }
        }
    }

    /**
     * Comemoração de um aporte que concluiu a meta, guardada até o usuário tocar em "Concluir" na
     * confirmação do aporte — senão ela cobriria a própria confirmação. "Desfazer" a descarta.
     */
    private var deferredCelebration: ObjetivoEntity? = null

    /** Recibo do último aporte feito pela folha "Registrar aporte", para o "Desfazer" dela. */
    private var sheetContribution: com.finai.app.domain.EntryReceipt? = null
    private var sheetContributionJob: Job? = null

    /**
     * Registra um aporte (folha "Registrar aporte"). É a mesma operação do "+" → Transferência:
     * grava a transferência (origem: a conta usada por último) e soma no objetivo juntas, então
     * o valor sai do livre uma vez e entra no histórico do objetivo. Antes só somava no objetivo
     * e o dinheiro continuava contado como livre. A comemoração, se houver, sai em
     * [releaseContributionCelebration].
     */
    fun registrarAporte(objetivoId: Long, cents: Long) {
        sheetContributionJob = launchSafely("registrar o aporte") {
            val state = uiState.value
            val objetivo = state.rawObjetivos.firstOrNull { it.id == objetivoId } ?: return@launchSafely
            val cards = com.finai.app.domain.EntryOptions.knownCards(state.rawFaturasCartao, state.rawTransacoes)
            val origem = com.finai.app.domain.EntryOptions.recentAccounts(state.rawTransacoes, cards).first()
            val receipt = saveContribution(
                TransacaoEntity(
                    data = LocalDate.now().toEpochMillis(), descricao = "Aporte · ${objetivo.nome}", valorCentavos = cents,
                    categoria = TransactionType.Transferencia.label, contaOrigem = origem, recorrente = false,
                    origem = "manual", tipo = TransactionType.Transferencia.name, objetivoId = objetivo.id, contaDestino = objetivo.nome,
                ),
            )
            sheetContribution = receipt
        }
    }

    /** "Desfazer" na confirmação: apaga a transferência e tira o valor do objetivo juntos. */
    fun desfazerAporte(objetivoId: Long, cents: Long) {
        deferredCelebration = null
        launchSafely("desfazer o aporte") {
            // "Desfazer" tocado antes de a gravação terminar: espera o recibo existir.
            sheetContributionJob?.join()
            val receipt = sheetContribution?.takeIf { it.goal?.goalId == objetivoId } ?: return@launchSafely
            repository.desfazer(receipt)
            sheetContribution = null
        }
    }

    fun releaseContributionCelebration() {
        deferredCelebration?.let(::celebrateGoal)
        deferredCelebration = null
    }

    // ── manual entry: dívidas ───────────────────────────────────
    fun saveDivida(divida: DividaEntity) = launchSafely("salvar a dívida") {
        val before = uiState.value.rawDividas.firstOrNull { it.id == divida.id && divida.id != 0L }
        val update = Completion.onDebtSaved(before, divida, System.currentTimeMillis())
        repository.salvarDivida(update.divida)
        if (update.celebrate) celebrateDebt(update.divida)
    }

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
            val update = Completion.onDebtSaved(divida, com.finai.app.domain.DebtSchedule.afterPayment(divida, hoje), System.currentTimeMillis())
            repository.salvarDivida(update.divida)
            if (update.celebrate) celebrateDebt(update.divida)
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

    // ── Configurações: exportar e restaurar (Fase 7, item 12) ────
    /** Resultado da última exportação/restauração, para a tela avisar. */
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()
    fun dismissBackupStatus() { _backupStatus.value = null }

    /** Backup lido e validado, esperando o "substitui tudo" do usuário. */
    private val _pendingRestore = MutableStateFlow<com.finai.app.data.backup.FinaiBackup?>(null)
    val pendingRestore: StateFlow<com.finai.app.data.backup.FinaiBackup?> = _pendingRestore.asStateFlow()
    fun cancelRestore() { _pendingRestore.value = null }

    /** Grava o JSON no arquivo que o usuário escolheu pelo seletor do sistema (SAF, sem permissão nova). */
    fun exportBackup(uri: android.net.Uri) = viewModelScope.launch {
        try {
            val backup = repository.exportar(com.finai.app.data.local.FINAI_DB_VERSION, System.currentTimeMillis())
            val json = com.finai.app.data.backup.BackupCodec.encode(backup)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                    ?: error("sem stream")
            }
            _backupStatus.value = "Backup salvo: ${backup.summary()}. Guarde o arquivo em lugar seguro — ele tem seus dados financeiros sem criptografia."
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FinaiLog.e(TAG, "Falha ao exportar o backup", t)
            _backupStatus.value = "Não foi possível salvar o backup. Tente outro local."
        }
    }

    /** Lê e valida o arquivo; só grava depois da confirmação ([confirmRestore]). */
    fun readBackup(uri: android.net.Uri) = viewModelScope.launch {
        try {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } ?: error("sem stream")
            _pendingRestore.value = com.finai.app.data.backup.BackupCodec.decode(text, com.finai.app.data.local.FINAI_DB_VERSION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.finai.app.data.backup.BackupFormatException) {
            _backupStatus.value = e.message
        } catch (t: Throwable) {
            FinaiLog.e(TAG, "Falha ao ler o backup", t)
            _backupStatus.value = "Não foi possível abrir o arquivo."
        }
    }

    fun confirmRestore(onDone: () -> Unit = {}) {
        val backup = _pendingRestore.value ?: return
        _pendingRestore.value = null
        launchSafely("restaurar o backup") {
            repository.restaurar(backup)
            AiResponseCache.clear()
            FinaiNotifier(getApplication()).cancelAll()
            _backupStatus.value = "Dados restaurados: ${backup.summary()}."
            onDone()
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
    /** "Manter separadas" / "Dispensar" num ponto de atenção de Objetivos. */
    fun dismissInsight(key: String) = launchSafely("dispensar o aviso") { prefs.dismissInsight(key) }

    /** "Mostrar dispensados": todos os pontos voltam, inclusive "manter separadas". */
    fun restoreDismissedInsights() = launchSafely("mostrar os avisos dispensados") { prefs.clearDismissedInsights() }

    fun apagarTodosOsDados(onDone: () -> Unit = {}) {
        launchSafely("apagar todos os dados") {
            repository.apagarTodosOsDados()
            prefs.clearDismissedInsights()
            AiResponseCache.clear()
            FinaiNotifier(getApplication()).cancelAll()
            onDone()
        }
    }

    private companion object {
        const val TAG = "FinanceViewModel"
    }
}
