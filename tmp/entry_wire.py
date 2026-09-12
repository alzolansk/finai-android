from pathlib import Path
base=Path('app/src/main/java/com/finai/app')
p=base/'FinaiApp.kt'
s=p.read_text(encoding='utf-8').replace('import com.finai.app.ui.components.AddTransactionDialog','import com.finai.app.ui.components.TransactionEntryScreen\nimport com.finai.app.domain.transactionsInMonth\nimport androidx.compose.runtime.saveable.rememberSaveable')
s=s.replace('var showAddTransaction by remember {', 'var showAddTransaction by rememberSaveable {')
start=s.index('        if (showAddTransaction) {')
end=s.index('        if (showAddGoal)',start)
s=s[:start]+'''        TransactionEntryScreen(
            visible = showAddTransaction,
            accounts = financeState.rawTransacoes.map { it.contaOrigem }.distinct(),
            onDismiss = { showAddTransaction = false },
            onSave = financeViewModel::saveTransactionEntry,
            onViewEntry = { date ->
                viewModel.showAgendaDate(date)
                showAddTransaction = false
                navigateTo(FinaiDestination.Agenda)
            },
        )

'''+s[end:]
start=s.index('    val transacoesDoMes = transacoes\n')
end=s.index('    return AgendaData(',start)
s=s[:start]+'''    val transacoesDoMes = transacoes
        .transactionsInMonth(java.time.LocalDate.of(year, monthIndex + 1, 1))
        .sortedByDescending { it.data }
'''+s[end:]
p.write_text(s,encoding='utf-8')
p=base/'ui/components/EntryDialogs.kt'
s=p.read_text(encoding='utf-8');start=s.index('@Composable\nfun AddTransactionDialog');end=s.index('@Composable\nfun AddGoalDialog',start)
s=s[:start]+s[end:];p.write_text(s,encoding='utf-8')
p=base/'ui/screens/agenda/AgendaScreen.kt'
s=p.read_text(encoding='utf-8').replace('Text(formatBrl0(transacao.valorCentavos / 100.0), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)', '''Text(com.finai.app.util.formatBrl(transacao.valorCentavos / 100.0), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when (transacao.tipo) {
                "Receita" -> Color(0xFF059669)
                "Transferencia" -> Color(0xFF4F46E5)
                else -> FinaiColors.TextPrimary
            })''')
s=s.replace('"${transacao.categoria} · %02d/%02d"', '("${if (transacao.tipo == \"Transferencia\") \"Transferência\" else transacao.tipo} · ${transacao.categoria}" +\n                    (if (transacao.recorrente) " · recorrente" else "") + " · %02d/%02d")')
p.write_text(s,encoding='utf-8')
