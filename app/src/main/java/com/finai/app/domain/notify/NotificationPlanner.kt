package com.finai.app.domain.notify

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CycleSource
import com.finai.app.domain.inbox.InboxItem
import com.finai.app.domain.toLocalDate
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A cara da notificação: cor do destaque e o símbolo do selo. */
enum class NotifyLook {
    Bill, Overdue, Shortfall, Limit, Coach, Capture, Daily, Streak, Closing, Goal, Income, Done,
}

/** O que cada botão faz. Os que abrem tela viram intent de Activity; os outros, de broadcast. */
enum class NotifyAction(val opensApp: Boolean) {
    PayBill(false), UndoPay(false), Snooze(false), Dismiss(false), AckCoach(false), NothingToday(false),
    CaptureSave(false), CaptureIgnore(false),
    OpenInbox(true), OpenAgenda(true), OpenSpending(true), OpenPlans(true), OpenEntry(true), OpenClosing(true), OpenSuggestion(true),
}

data class NotifyButton(val label: String, val action: NotifyAction, val primary: Boolean = false)

/**
 * Uma notificação pronta para desenhar, sem nada de Android. [key] identifica o evento (o mesmo
 * id estável da Caixa), para a ação do botão saber em que mexer e para não notificar duas vezes.
 */
data class NotifySpec(
    val key: String,
    val look: NotifyLook,
    val eyebrow: String,
    val title: String,
    val body: String,
    val amount: String? = null,
    val amountLabel: String? = null,
    /** 0–100; nulo = sem barra. */
    val progress: Int? = null,
    val progressLabel: String? = null,
    val buttons: List<NotifyButton> = emptyList(),
    val tap: NotifyAction = NotifyAction.OpenInbox,
    /** Id numérico do alvo do botão (conta, sugestão), quando há. */
    val targetId: Long? = null,
)

/**
 * Texto e botões das notificações (o "jeito Duolingo": uma frase com personalidade, o número que
 * importa em destaque e a ação mais provável a um toque). Kotlin puro: nenhum número aqui é
 * calculado, todos vêm do item da Caixa ou do retrato do dia.
 */
object NotificationPlanner {
    const val MAX_PER_RUN = 3

    fun forInbox(item: InboxItem, today: LocalDate): NotifySpec? {
        val money = formatBrl(item.valueCents / 100.0)
        val conta = (item.movement?.source as? CycleSource.Conta)?.conta
        val name = item.movement?.descricao?.take(28) ?: item.title
        return when (item.kind) {
            "conta" -> {
                val due = item.due ?: today
                val days = ChronoUnit.DAYS.between(today, due).toInt()
                val overdue = days < 0
                NotifySpec(
                    key = item.id, look = if (overdue) NotifyLook.Overdue else NotifyLook.Bill,
                    eyebrow = when { overdue -> "ATRASADA HÁ ${-days} ${if (days == -1) "DIA" else "DIAS"}"; days == 0 -> "VENCE HOJE"; days == 1 -> "VENCE AMANHÃ"; else -> "VENCE EM $days DIAS" },
                    title = if (overdue) "$name ficou para trás" else if (days == 0) "$name é hoje" else "$name está chegando",
                    body = if (overdue) "Quanto antes pagar, menos juros. Se já pagou, é só marcar." else "Já está na conta do seu livre. Pagou? Marca aqui mesmo.",
                    amount = money, amountLabel = "a pagar",
                    buttons = if (conta != null) listOf(NotifyButton("Já paguei", NotifyAction.PayBill, primary = true), NotifyButton("Lembrar amanhã", NotifyAction.Snooze))
                    else listOf(NotifyButton("Ver na Agenda", NotifyAction.OpenAgenda, primary = true), NotifyButton("Lembrar amanhã", NotifyAction.Snooze)),
                    tap = NotifyAction.OpenInbox, targetId = conta?.id,
                )
            }
            "entrada" -> NotifySpec(item.id, NotifyLook.Income, "ENTRADA PREVISTA", "$name deve cair ${if (item.due == today) "hoje" else "em breve"}",
                "Quando o dinheiro entrar, confirme para o saldo ficar certo.", money, "a receber",
                buttons = listOf(NotifyButton("Ver na Agenda", NotifyAction.OpenAgenda, primary = true), NotifyButton("Dispensar", NotifyAction.Dismiss)), tap = NotifyAction.OpenAgenda)
            "limite" -> {
                val over = item.level > 0
                val limit = Regex("""de (R\$\s?[\d.,]+)""").find(item.body)?.groupValues?.get(1)
                val category = item.title.substringBefore(" atingiu").substringBefore(" perto")
                NotifySpec(item.id, NotifyLook.Limit, if (over) "LIMITE ATINGIDO" else "QUASE NO LIMITE",
                    if (over) "$category passou do combinado" else "$category está no fim do limite",
                    if (over) "O limite do mês já foi. Dá para segurar até o mês virar?" else "Ainda dá tempo de fechar o mês dentro do limite.",
                    amount = money, amountLabel = limit?.let { "de $it" },
                    progress = limitProgress(item, limit), progressLabel = null,
                    buttons = listOf(NotifyButton("Ver gastos", NotifyAction.OpenSpending, primary = true), NotifyButton("Dispensar", NotifyAction.Dismiss)), tap = NotifyAction.OpenSpending)
            }
            "falta" -> NotifySpec(item.id, NotifyLook.Shortfall, "FALTA PREVISTA", "O saldo vai ficar negativo", item.body, money, "faltando",
                buttons = listOf(NotifyButton("Ver compromissos", NotifyAction.OpenAgenda, primary = true), NotifyButton("Lembrar amanhã", NotifyAction.Snooze)), tap = NotifyAction.OpenAgenda)
            "coach" -> NotifySpec(item.id, NotifyLook.Coach, "NOVIDADE NO SEU GASTO", item.title, item.body,
                buttons = listOf(NotifyButton("Ver gastos", NotifyAction.OpenSpending, primary = true), NotifyButton("Já sei", NotifyAction.AckCoach)), tap = NotifyAction.OpenSpending)
            "meta" -> NotifySpec(item.id, NotifyLook.Goal, "SUAS METAS", item.title, item.body,
                buttons = listOf(NotifyButton("Ver planos", NotifyAction.OpenPlans, primary = true), NotifyButton("Dispensar", NotifyAction.Dismiss)), tap = NotifyAction.OpenPlans)
            "captura" -> null // tem notificação própria ([forCapture]), postada na hora da leitura
            else -> NotifySpec(item.id, NotifyLook.Bill, "PARA VOCÊ VER", item.title, item.body,
                buttons = listOf(NotifyButton("Abrir a Caixa", NotifyAction.OpenInbox, primary = true), NotifyButton("Dispensar", NotifyAction.Dismiss)))
        }
    }

    private fun limitProgress(item: InboxItem, limit: String?): Int? {
        val cents = limit?.filter { it.isDigit() }?.toLongOrNull() ?: return null
        return if (cents <= 0) null else (item.valueCents * 100 / cents).toInt().coerceIn(0, 100)
    }

    /** "Movimentação encontrada": salvar direto só quando nada falta e não parece repetida. */
    fun forCapture(id: Long, description: String, cents: Long?, appName: String, canSaveDirectly: Boolean, test: Boolean): NotifySpec = NotifySpec(
        key = "captura-$id", look = NotifyLook.Capture, eyebrow = if (test) "TESTE DO FINAI" else "LIDO DE ${appName.uppercase().take(18)}",
        title = description.ifBlank { "Movimentação encontrada" }.take(40),
        body = if (canSaveDirectly) "Está tudo aqui. Salvo para você?" else "Falta conferir um detalhe antes de salvar.",
        amount = cents?.let { formatBrl(it / 100.0) } ?: "Valor a confirmar", amountLabel = "nada foi lançado ainda",
        buttons = listOf(
            if (canSaveDirectly) NotifyButton("Salvar", NotifyAction.CaptureSave, primary = true) else NotifyButton("Conferir", NotifyAction.OpenSuggestion, primary = true),
            NotifyButton("Ignorar", NotifyAction.CaptureIgnore),
        ),
        tap = NotifyAction.OpenSuggestion, targetId = id,
    )

    /** Dias seguidos (terminando em [today] ou ontem) com algum lançamento ou com "Nada hoje". */
    fun streak(transacoes: List<TransacaoEntity>, quietDays: Set<String>, today: LocalDate): Int {
        val active = transacoes.filter { it.origem != com.finai.app.domain.ADJUSTMENT_ORIGIN && !it.recorrente }
            .map { it.data.toLocalDate() }.filter { !it.isAfter(today) }.toHashSet()
        quietDays.mapNotNullTo(active) { runCatching { LocalDate.parse(it) }.getOrNull() }
        var day = if (today in active) today else today.minusDays(1)
        var count = 0
        while (day in active && count < 999) { count++; day = day.minusDays(1) }
        return count
    }

    /** Já tem registro de hoje (lançamento com data de hoje, ou "Nada hoje")? Então não lembra. */
    fun loggedToday(transacoes: List<TransacaoEntity>, quietDays: Set<String>, today: LocalDate): Boolean =
        today.toString() in quietDays || transacoes.any { !it.recorrente && it.data.toLocalDate() == today }

    private val DAILY = listOf(
        "Gastou alguma coisa hoje?" to "Dez segundos agora valem mais que tentar lembrar no domingo.",
        "Fechando o dia" to "Um café, um Pix, um mercado: o que saiu da conta hoje?",
        "Seu saldo confia em você" to "Lance o que gastou hoje e o livre de amanhã fica certo.",
        "Passou o cartão hoje?" to "Conta agora, enquanto ainda lembra o valor.",
        "Dia tranquilo ou dia de gasto?" to "Se não gastou nada, é só tocar em \"Nada hoje\".",
        "Hora de bater o caixa" to "Dois toques e o dia fica registrado.",
        "Não deixe para a fatura contar" to "O que você lança hoje não vira surpresa no fim do mês.",
    )

    fun daily(today: LocalDate, streak: Int, freeCents: Long?): NotifySpec {
        val (title, body) = DAILY[today.dayOfYear % DAILY.size]
        val hot = streak >= 2
        return NotifySpec(
            key = "diario-$today", look = if (hot) NotifyLook.Streak else NotifyLook.Daily,
            eyebrow = if (hot) "$streak DIAS SEGUIDOS" else "LEMBRETE DO DIA",
            title = if (streak >= 7) "$streak dias sem falhar. Mantém?" else title,
            body = if (hot) "$body Não quebre a sequência." else body,
            amount = freeCents?.takeIf { it > 0 }?.let { formatBrl0(it / 100.0) }, amountLabel = freeCents?.takeIf { it > 0 }?.let { "livres até a próxima entrada" },
            progress = if (hot) (streak.coerceAtMost(7) * 100 / 7) else null,
            progressLabel = if (hot && streak < 7) "faltam ${7 - streak} para uma semana inteira" else if (hot) "semana completa" else null,
            buttons = listOf(NotifyButton("Lançar gasto", NotifyAction.OpenEntry, primary = true), NotifyButton("Nada hoje", NotifyAction.NothingToday)),
            tap = NotifyAction.OpenEntry,
        )
    }

    fun closing(monthName: String, leftCents: Long, month: String): NotifySpec = NotifySpec(
        key = "fechamento-$month", look = NotifyLook.Closing, eyebrow = "FECHAMENTO DE ${monthName.uppercase()}",
        title = if (leftCents >= 0) "$monthName fechou no azul" else "$monthName fechou apertado",
        body = "Quatro passos rápidos para começar o mês novo com tudo no lugar.",
        amount = formatBrl0(kotlin.math.abs(leftCents) / 100.0), amountLabel = if (leftCents >= 0) "de sobra" else "faltando",
        buttons = listOf(NotifyButton("Fechar o mês", NotifyAction.OpenClosing, primary = true), NotifyButton("Depois", NotifyAction.Dismiss)), tap = NotifyAction.OpenClosing,
    )

    /** Confirmação que troca a notificação depois de um botão que agiu sem abrir o app. */
    fun done(key: String, title: String, body: String, undo: Boolean, targetId: Long?): NotifySpec = NotifySpec(
        key = key, look = NotifyLook.Done, eyebrow = "FEITO", title = title, body = body,
        buttons = if (undo) listOf(NotifyButton("Desfazer", NotifyAction.UndoPay)) else emptyList(), tap = NotifyAction.OpenInbox, targetId = targetId,
    )

    /** Exemplos da tela de Configurações (botões levam ao app; nada é alterado). */
    fun samples(today: LocalDate): List<NotifySpec> = listOf(
        NotifySpec("exemplo-conta", NotifyLook.Bill, "VENCE AMANHÃ", "Fatura do cartão está chegando", "Já está na conta do seu livre. Pagou? Marca aqui mesmo.",
            "R$ 1.320,00", "a pagar", buttons = listOf(NotifyButton("Já paguei", NotifyAction.OpenInbox, true), NotifyButton("Lembrar amanhã", NotifyAction.OpenInbox))),
        NotifySpec("exemplo-limite", NotifyLook.Limit, "QUASE NO LIMITE", "Lazer está no fim do limite", "Ainda dá tempo de fechar o mês dentro do limite.",
            "R$ 270,00", "de R$ 300,00", 90, buttons = listOf(NotifyButton("Ver gastos", NotifyAction.OpenSpending, true), NotifyButton("Dispensar", NotifyAction.OpenInbox))),
        // "Nada hoje" do lembrete de verdade grava o dia; no exemplo os botões só abrem o app.
        daily(today, 5, 122_000).let { d -> d.copy(key = "exemplo-diario", buttons = d.buttons.map { b -> if (b.action.opensApp) b else b.copy(action = NotifyAction.OpenInbox) }) },
    )
}
