# Handoff da sessão — fluxo de criação de lançamentos

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
