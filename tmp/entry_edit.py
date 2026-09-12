from pathlib import Path
base=Path('app/src/main/java/com/finai/app')
def edit(name, old, new):
    p=base/name
    s=p.read_text(encoding='utf-8')
    assert old in s, (name,old)
    p.write_text(s.replace(old,new),encoding='utf-8')
edit('data/local/entity/FinaiEntities.kt','val origem: String, //', '@androidx.room.ColumnInfo(defaultValue = "\'Gasto\'") val tipo: String = "Gasto",\n    val origem: String, //')
edit('data/local/FinaiDatabase.kt','version = 3','version = 4')
edit('data/local/FinaiDatabase.kt','@Volatile private var instance', '''val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transacoes ADD COLUMN tipo TEXT NOT NULL DEFAULT 'Gasto'")
            }
        }

        @Volatile private var instance''')
edit('data/local/FinaiDatabase.kt','.fallbackToDestructiveMigration()', '.addMigrations(MIGRATION_3_4)\n                .fallbackToDestructiveMigration()')
edit('domain/SafeToSpendCalculator.kt','val gastosDoMes = transacoes\n            .filter { it.data in range }\n            .sumOf { it.valorCentavos }', '''val gastosDoMes = transacoes.transactionsInMonth(today)
            .sumOf { when (it.tipo) {
                "Receita" -> -it.valorCentavos
                "Transferencia" -> 0L
                else -> it.valorCentavos
            } }''')
edit('domain/BudgetCalculator.kt','transacoes.groupBy','transacoes.filter { it.tipo == "Gasto" }.groupBy')
edit('domain/BehaviorCoach.kt','transacoes.filter { it.data in','transacoes.filter { it.tipo == "Gasto" && it.data in')
edit('state/FinanceViewModel.kt','import com.finai.app.domain.AlertCalculator','import com.finai.app.domain.transactionsInMonth\nimport com.finai.app.domain.AlertCalculator')
edit('state/FinanceViewModel.kt','s.transacoes.filter { it.data in monthRange }','s.transacoes.transactionsInMonth(today)')
edit('state/FinanceViewModel.kt','    fun deleteTransacao', '''    suspend fun saveTransactionEntry(entry: TransacaoEntity) {
        require(entry.valorCentavos > 0 && entry.categoria.isNotBlank())
        repository.salvarTransacao(entry)
    }

    fun deleteTransacao''')
edit('state/AppViewModel.kt','    fun prevMonth()', '''    fun showAgendaDate(date: java.time.LocalDate) = _uiState.update {
        it.copy(monthIndex = date.monthValue - 1, agendaYear = date.year)
    }

    fun prevMonth()''')
