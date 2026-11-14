package com.finai.app.domain.assistant

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import java.time.LocalDate

/** Dados sintéticos dos testes do assistente (nunca o backup pessoal). */
internal object T {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 14)

    fun d(y: Int, m: Int, day: Int): LocalDate = LocalDate.of(y, m, day)

    fun tx(
        id: Long, date: LocalDate, cents: Long, desc: String, cat: String = "Outros", tipo: String = "Gasto",
        recorrente: Boolean = false, principal: Boolean = false, conta: String = "Nubank",
    ) = TransacaoEntity(
        id = id, data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = cat, contaOrigem = conta,
        recorrente = recorrente, tipo = tipo, origem = "manual", rendaPrincipal = principal,
    )

    /** Salário recorrente que começou em [start] (o dia do mês vem dele). */
    fun salary(id: Long = 1, start: LocalDate, cents: Long = 585_000, name: String = "Salário") =
        tx(id, start, cents, name, "Receita", "Receita", recorrente = true, principal = true, conta = "Itaú")

    fun conta(id: Long, nome: String, cents: Long, due: LocalDate, paid: Boolean = false) = ContaEntity(
        id = id, nome = nome, valorCentavos = cents, vencimento = due.toEpochMillis(),
        status = if (paid) "pago" else "pendente", tipo = "a_pagar", recorrente = false,
    )

    fun debt(id: Long, nome: String, remaining: Int = 5, eachCents: Long = 20_000, next: LocalDate = d(2026, 11, 20)) = DividaEntity(
        id = id, nome = nome, valorOriginalCentavos = eachCents * (remaining + 3), valorAbertoCentavos = eachCents * remaining,
        taxaJurosMensalBasisPoints = 300, parcelasRestantes = remaining, valorParcelaCentavos = eachCents, parcelasTotais = remaining + 3,
        proximoVencimento = next.toEpochMillis(), diaVencimento = next.dayOfMonth,
    )

    fun goal(id: Long, nome: String, saved: Long = 100_000, target: Long = 500_000, deadline: LocalDate = d(2027, 10, 31)) = ObjetivoEntity(
        id = id, tipo = "Viagem", nome = nome, valorAlvoCentavos = target, valorGuardadoCentavos = saved,
        prazo = deadline.toEpochMillis(), prioridade = id.toInt(),
    )

    fun snap(
        transacoes: List<TransacaoEntity> = emptyList(),
        contas: List<ContaEntity> = emptyList(),
        dividas: List<DividaEntity> = emptyList(),
        objetivos: List<ObjetivoEntity> = emptyList(),
        today: LocalDate = TODAY,
        margin: Long = 30_000,
    ) = AssistantSnapshot(contas, transacoes, dividas, margin, today, objetivos = objetivos)

    fun plan(name: String, s: AssistantSnapshot, confirmed: Boolean = false, vararg args: Pair<String, String>) =
        ActionPlanner.plan(ActionRequest(name, args.toMap()), s, confirmed)

    fun parse(text: String, s: AssistantSnapshot, session: AssistantSession = AssistantSession()) = IntentParser.action(text, s, session)
}

/** Executor falso: grava na memória só a contagem; a mesma chave não se repete (o contrato do real). */
internal class FakeExecutor : ActionExecutor {
    val applied = mutableListOf<Pair<ActionPlan, String>>()
    val undone = mutableListOf<Long?>()
    private val keys = mutableSetOf<String>()
    var nextStatus: ActionStatus? = null

    override suspend fun apply(plan: ActionPlan, key: String, conversationId: Long): ExecResult {
        nextStatus?.let { return ExecResult(it, message = "Os dados mudaram.") }
        if (!keys.add(key)) return ExecResult(ActionStatus.AlreadyApplied, actionId = applied.size.toLong())
        applied += plan to key
        return ExecResult(ActionStatus.Applied, actionId = applied.size.toLong())
    }

    override suspend fun undo(conversationId: Long, actionId: Long?): ExecResult {
        undone += actionId
        return if (applied.isEmpty()) ExecResult(ActionStatus.Unsupported, message = "Nada a desfazer.")
        else ExecResult(ActionStatus.Undone, actionId = actionId, summary = "O recebimento voltou.")
    }
}

internal class FakeSessions : SessionStore {
    val map = mutableMapOf<Long, AssistantSession>()
    override suspend fun load(conversationId: Long) = map[conversationId] ?: AssistantSession()
    override suspend fun save(conversationId: Long, session: AssistantSession) { map[conversationId] = session }
}
