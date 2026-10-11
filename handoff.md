# Handoff atual — novo lançamento conforme playbook de 14 páginas

Atualização: 11/10/2026. Branch: `nova-tela-gastos`. Base: `267ea2a` (primeira etapa, Codex).
**Status: fluxo do playbook implementado. Não compilado com o Gradle Android nem testado em
aparelho; nenhum commit desta etapa.**

## Fontes e decisões

- Playbook: `uploads/design-gasto/finai-gasto-playbook.pdf` (14 páginas, lido inteiro nesta etapa).
  `uploads/` é ignorada pelo Git: o PDF não vem no clone.
- Confirmadas (planning.md §11, 10/10): reserva contínua sem alvo (saldo antes/depois e cobertura em
  dias, nada de percentual ou "falta"); contas e cartões continuam só nomes de origem + faturas, com
  confirmação do vencimento quando os dados não bastam.
- Novas, tomadas nesta etapa e registradas em planning.md §11 para confirmação: ver lá.
- O estado único `EntryUiState` do playbook (página 13) é o conteúdo que o filtro bloqueou na etapa
  do Codex. Não foi recriado: o rascunho continua em `rememberSaveable`, campo a campo.

## O que foi feito

| Área | Arquivo | O quê |
| --- | --- | --- |
| Regras | `domain/EntryOptions.kt` (novo) | Pagamento, tipos de receita, destinos, categorias por uso recente, contas/cartões recentes, parcelas em centavos, vencimento da fatura com grau de certeza, prévia do aporte, valor por extenso, recibo, destaque da Agenda, histórico de aportes |
| Regras | `domain/TransactionEntry.kt` | `isGoalContribution`, `freeCashSign` (regra única do livre) |
| Cálculo | `PayCycle`, `MonthCashFlow`, `CashFlowTimeline`, `scenario/PeriodProjection`, `SafeToSpendCalculator` | Aporte sai do livre uma vez; transferência entre contas continua neutra. `MonthCashFlow.aportesCents` sai do saldo, fora de "contas a pagar" e da média de gasto da reserva |
| Room | `FinaiEntities.kt`, `FinaiDatabase.kt` | v13: `transacoes.objetivoId`, `transacoes.contaDestino` (anuláveis, `MIGRATION_12_13`) |
| Backup | `data/backup/FinaiBackup.kt` | Exporta/restaura os dois campos novos |
| Persistência | `data/repository/FinanceRepository.kt` | `salvarLancamento`, `salvarCompraCartao` e `registrarAporte` devolvem `EntryReceipt`; `desfazer(receipt)` reverte tudo numa transação; `salvarFatura` devolve o que gravou; `excluirTransacao` de um aporte tira o valor do objetivo |
| Estado | `state/FinanceViewModel.kt` | `saveContribution`, `undoEntry`; a folha "Registrar aporte" grava a mesma transferência (origem: a conta usada por último) e desfaz pelo recibo |
| Tela | `ui/components/TransactionEntryScreen.kt` | Reescrita conforme o playbook (abaixo) |
| Tela | `FinaiApp.kt`, `AgendaScreen.kt`, `GoalsScreen.kt`, `GoalCreateScreen.kt` | Ligações; destaque e rolagem até o item na Agenda; aporte com sinal na soma do dia e "para X"; histórico de aportes nos detalhes do objetivo; criar reserva a partir do lançamento e voltar a ele |
| Testes JVM | `test/.../domain/EntryOptionsTest.kt` (novo) | 14 testes |
| Instrumentados | `TransactionEntryFlowTest`, `CardPurchaseFlowTest`, `EntryKeypadLayoutTest` (atualizados); `ContributionRepositoryTest`, `Migration12To13Test` (novos) | Escritos, **não executados** |

### Tela

- Topo: X e Gasto/Receita/Transferência (indicador 500 ms, curva .34/1.45/.55/1; o Codex usava mola).
  Rótulos das abas encolhem juntos, para o maior caber inteiro.
- Valor: 54/42 sp; linha de ajuda "Toque nos números abaixo" / "Toque para editar o valor" /
  "10× de R$ 289,90" em verde. Lido por extenso ("Dois mil oitocentos e noventa e nove reais").
- Gasto: faixa horizontal 76 × 84 dp (largura cresce com a fonte até 1,45×), uso recente primeiro,
  selecionado verde 16% e escala 1,04 (300 ms). Débito/Pix · Crédito · Parcelado. Crédito: linha
  "Entra na fatura Nubank de nov · vence 10/11". Parcelado: cartão com slider 2–24 (passos de 1,
  "10 parcelas de R$ 289,90"), total, sobra de centavos e "1ª parcela … · última em ago/27".
  Sem certeza da fatura, a linha pede "Confirmar" e o salvar fica bloqueado até confirmar.
- Receita: Salário · Recorrente · Avulsa (padrão) · Extra. Categoria gravada = nome do tipo.
- Transferência: Reserva · Objetivo · Outra conta. Reserva = objetivos do tipo Reserva (com ou sem
  alvo) e reservas contínuas; Objetivo = os demais em andamento. 0 opções: "Criar reserva/objetivo"
  (abre a criação por cima e volta). 1: "Vai direto para", já escolhida. 2+: "Qual reserva?", salvar
  bloqueado até escolher. Prévia ao vivo: "62% → 64%" com o ganho em verde claro; contínua:
  "R$ 1.000,00 → R$ 1.500,00" e "Cobre 6 → 10 dias de gastos". Outra conta: destino obrigatório e
  diferente da origem.
- Pílulas de 38 dp (toque de 48 dp): data, conta/cartão/origem, descrição e, só no gasto no débito,
  "Todo mês". Cada uma abre um painel próprio (380 ms, curva .2/.8/.2/1).
- Botão: Salvar gasto · Salvar receita · Salvar transferência · Guardar na reserva · Guardar no objetivo.
- Depois de salvar: base vira a confirmação (check por tipo, ou o cartão da reserva com count-up:
  0–350 ms escala 0,98 e valor anterior; 350–1350 ms valor e barra sobem com 1 − (1 − t)³; depois
  halo 600 ms, mola para escala 1 e "Faltam R$ X"/cobertura). Resumo em uma linha. Aviso de vidro no
  topo com Desfazer. Lançar outro (mantém o tipo, teclado aberto). Ver na agenda (mês do lançamento,
  ou da 1ª fatura, com a linha destacada). Animações desligadas: estados diretos. O recibo sobrevive
  à rotação (`Saver`).

## Validação feita (e o que ela não cobre)

Gradle Android não roda neste ambiente: a rede bloqueia `dl.google.com` (Maven do Google). Feito:
- 279 testes JVM (`domain/` + backup) verdes com kotlinc 1.9.22 avulso e stubs de anotação do Room.
- 8 cenários do repositório (salvar, cartão com fatura nova e reaproveitada, desfazer bloqueado por
  fatura paga, aporte, meta que conclui e reabre, reserva sem alvo, exclusão pela Agenda, objetivo
  inexistente) contra DAOs falsos em memória com rollback simulado. Fica no scratchpad da sessão,
  não no repo (depende de stubs do Room). A versão com Room real é o `ContributionRepositoryTest`.
- Checagem de tipos, com o plugin do Compose 1.5.8, de todas as telas alteradas contra o Compose
  Desktop 1.6.0 (APIs Android em stubs). `FinanceViewModel` e `FinaiApp` foram revisados à mão.
- A tela foi dirigida no Compose Desktop (toques, slider, desfazer, lançar outro, região "live"
  com o valor final) e capturada em 412×892, 320×640 com fonte 150%, 640×360, escuro e claro.

Não cobre: build Android/R8, Room real, `connectedDebugAndroidTest`, TalkBack em uso, 60 fps,
desfoque real dos painéis (Android 12+) e aparelho físico.

```
./gradlew assembleDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.finai.app.TransactionEntryFlowTest,com.finai.app.CardPurchaseFlowTest,com.finai.app.EntryKeypadLayoutTest,com.finai.app.data.repository.ContributionRepositoryTest,com.finai.app.data.repository.CardPurchaseRepositoryTest,com.finai.app.data.local.Migration12To13Test
```
No Windows, o handoff anterior usava `rtk proxy java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain ...`.
**Depois do primeiro build, commitar `app/schemas/com.finai.app.data.local.FinaiDatabase/13.json`.**

## Limitações e pendências

- O aviso de Desfazer é um painel translúcido, não vidro com desfoque real.
- Desfazer existe enquanto a confirmação está aberta. Depois, o caminho é excluir na Agenda (que, no
  aporte, também tira o valor do objetivo). Se o processo morrer entre gravar e confirmar, o
  lançamento fica gravado e a confirmação não volta.
- Sem fechamento conhecido do cartão, o app sugere o vencimento pelo dia da última fatura e sempre
  pede confirmação. Não existe calendário de cartão.
- Aportes registrados antes desta versão não têm transferência: continuam sem histórico.
- Parcelas lançadas no app e depois importadas na fatura podem não ser reconhecidas como duplicata
  (limitação já registrada na simulação de compra).

---

# Histórico preservado — handoff de 12/09/2026

**Conteúdo histórico abaixo; não usar seus números, schema ou referência como
estado atual da demanda de outubro.**

## Handoff da sessão — fluxo de criação de lançamentos

Data: 12/09/2026  
Projeto: `C:\Users\joaov\AndroidStudioProjects\finai-android`

## Objetivo

Refatorar o fluxo de criação de lançamentos do FinAI conforme:

- `project/ref/Finai Mobile App Design.pdf`
- `project/ref/lancamento.png`
- `project/ref/categoria.png`

As referências foram lidas integralmente. O PDF tem 13 slides e define a tela full-screen, teclado próprio, entrada em centavos, tipos Gasto/Receita/Transferência, bottom sheet de categorias, recorrência, validação, confirmação e navegação para Agenda.

## Implementação concluída

- Criada a tela Compose dedicada `app/src/main/java/com/finai/app/ui/components/TransactionEntryScreen.kt`.
- O antigo `AddTransactionDialog` de lançamentos foi removido de `EntryDialogs.kt`; os diálogos de contas, objetivos e dívidas continuam intactos.
- Tela com header fixo, seta de voltar, seletor Gasto/Receita/Transferência, valor destacado, descrição, conta/cartão, data, categoria, recorrência e botão Salvar.
- Teclado numérico próprio fixo de 240 dp, sem abrir o teclado numérico do sistema.
- Entrada em centavos como string, máximo de nove dígitos:
  - `4` → `R$ 0,04`
  - `3` → `R$ 0,43`
  - `00` → `R$ 43,00`
  - apagar → `R$ 4,30`
- Bottom sheet de categoria com nove opções e ícones vetoriais próprios:
  Alimentação, Transporte, Moradia, Saúde, Lazer, Educação, Compras, Serviços e Outros.
- Seleção de categoria fecha o sheet em um toque; toque no fundo cancela. O estado selecionado usa verde e borda interna.
- Seletor de conta/cartão com as origens existentes e fallback `Carteira`; permite informar outra conta.
- Seletor de data com Material DatePicker e atalhos Hoje/Ontem.
- Botão Salvar habilita somente quando valor > 0 e categoria foi escolhida.
- Descrição é opcional; quando vazia, a categoria é usada como nome do lançamento na Agenda.
- Sucesso ocorre somente depois da persistência no Room, com texto por tipo e ação `Ver lançamento`.
- `Ver lançamento` limpa o formulário e abre a Agenda no mês/ano da data salva.
- Rascunho usa `rememberSaveable`; voltar preserva o que foi digitado enquanto a camada estiver montada.
- Em orientação horizontal, o formulário fica rolável à esquerda e o teclado permanece fixo à direita.
- Inter 400–800 foi adicionada em `app/src/main/res/font/inter_*.ttf`; licença OFL em `app/src/main/assets/licenses/Inter-OFL.txt`.
- Ícones usam grid 24, traço 1,8 e tamanho visual de aproximadamente 21 dp.

## Persistência e regras financeiras

- `TransacaoEntity` recebeu `tipo` com valor padrão `Gasto`.
- Banco Room foi atualizado para versão 4 com `MIGRATION_3_4`, adicionando a coluna sem apagar registros existentes.
- `FinanceViewModel` ganhou `saveTransactionEntry` e continua usando `FinanceRepository` para persistir.
- `Receita` é tratada como entrada, `Transferencia` é neutra para os cálculos de gasto, e somente `Gasto` entra em orçamento/coach.
- Recorrência é projetada uma vez por mês para cálculo/Agenda, sem duplicar linhas no banco; dias 29–31 são ajustados para o último dia do mês.
- Agenda passou a exibir tipo/categoria/recorrência e cores diferentes para Receita e Transferência.
- `SafeToSpendCalculator`, `BudgetCalculator` e `BehaviorCoach` foram ajustados para respeitar o tipo.
- `CLAUDE.md` recebeu o registro desta refatoração e das pendências.

## Arquivos principais

- `app/src/main/java/com/finai/app/ui/components/TransactionEntryScreen.kt`
- `app/src/main/java/com/finai/app/domain/TransactionEntry.kt`
- `app/src/main/java/com/finai/app/data/local/entity/FinaiEntities.kt`
- `app/src/main/java/com/finai/app/data/local/FinaiDatabase.kt`
- `app/src/main/java/com/finai/app/state/FinanceViewModel.kt`
- `app/src/main/java/com/finai/app/FinaiApp.kt`
- `app/src/main/java/com/finai/app/ui/screens/agenda/AgendaScreen.kt`
- `app/src/test/java/com/finai/app/domain/TransactionEntryTest.kt`
- `app/src/androidTest/java/com/finai/app/TransactionEntryFlowTest.kt`

## Validação executada

Comandos principais, sempre usando `rtk` conforme `C:\Users\joaov\.codex\RTK.md`:

```text
rtk .\gradlew.bat assembleDebug testDebugUnitTest connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.finai.app.TransactionEntryFlowTest"
```

Resultado final: build verde.

- `assembleDebug`: passou.
- `testDebugUnitTest`: 100 testes, 0 falhas.
- `connectedDebugAndroidTest`: 5 testes de `TransactionEntryFlowTest`, 0 falhas.
- Emulador: Pixel 6 API 34.
- Dimensões exercitadas: aproximadamente 412×892 dp, 320×640 dp e 640×360 dp.
- Testes instrumentados cobrem Gasto, Receita, Transferência, Room após reabertura, Agenda, categoria, recorrência, rascunho e migração.
- Capturas visuais conferidas para a tela principal, bottom sheet e confirmação.

## Diferenças conhecidas

- O menu global de ações rápidas e o rodapé de navegação não foram redesenhados nesta sessão; a alteração começa quando `Lançar gasto` é aberto.
- Não há conta de destino no modelo atual. Transferência registra o tipo, valor, categoria e conta/cartão de origem; não foi inventada uma regra de débito/crédito.
- O blur de Compose é usado em Android 12+; em APIs 26–30 o scrim continua correto, mas o desfoque pode ser limitado pela plataforma.
- O calendário usa Material 3, em vez de uma implementação customizada pixel a pixel.
- Sombras, gradientes e glassmorphism são aproximações nativas do Compose, mantendo as cores e proporções da referência.
- Não foi possível validar em aparelho físico, somente no emulador.

## Estado do workspace

- O workspace já estava sujo antes desta sessão, com várias alterações de fases anteriores. Não fazer `reset`, `checkout` ou limpeza ampla.
- Não foi criado commit, conforme pedido do usuário.
- Há arquivos temporários em `tmp/` usados para inspeção visual e scripts auxiliares; podem ser removidos em uma sessão posterior se necessário, após confirmar que não são arquivos do usuário.

## Próximos passos sugeridos

1. Revisar o diff completo e separar eventuais alterações pré-existentes das desta sessão.
2. Testar manualmente no aparelho físico, especialmente navegação de volta, teclado, DatePicker, transferência e recorrência.
3. Decidir se a transferência deve ganhar uma conta de destino no modelo antes do lançamento.
4. Redesenhar o menu global `+` e o rodapé de vidro se a entrega precisar cobrir também os slides 4 e 11.
5. Não fazer commit sem autorização explícita do usuário.
