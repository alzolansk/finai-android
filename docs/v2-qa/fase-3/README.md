# FinAI v2 · Fase 3 · Caixa

Data: 08/11/2026. Referência acessada antes de implementar:
[Protótipo v2](https://claude.ai/code/artifact/da97f6eb-cc62-4dac-9696-e7932729f96a#page-5634c5cea0db).
O navegador autenticado redirecionou ao artefato “FinAI — Auditoria de design e IA”, mesmo UUID,
página Protótipo v2. Conferidos o canvas, `v2/Caixa` em Play e a regra 8 de `v2/Logicas`.

## Implementação

- Room 16→17: apenas `CREATE TABLE avisos`; schema 17 gerado pelo KSP, cadeia completa preservada.
  Estado, referência monetária, visto, resolvido, dispensado e adiamento ficam no aparelho.
- IDs de evento estáveis por conta/ocorrência, parcela, categoria/mês, sugestão ou decisão de meta.
  Piorar significa atingir o limite, passar a atraso ou variar **mais de** 20% da referência da dispensa.
  Não reabre a cada mudança do texto nem a cada novo dia de atraso. Decisões permanentes e “Já sei”
  não reabrem por variação de valor.
- Primeira leitura migra as antigas dispensas do DataStore. Marcador no Room impede remigração
  depois de “Desfazer tudo”. Sem valor antigo disponível, a primeira observação estabelece a referência.
- `InboxCatalog` e `InboxRules` são compartilhados entre a tela e `FinanceCheckWorker`.
  Contas vencidas de meses anteriores continuam acionáveis. Pontos de metas saem de Objetivos.
- Caixa é rota própria, com voltar, três grupos coloridos, contagem de pendentes, estado vazio e rodapé.
  Cabeçalho de todas as telas usa Caixa; número acessível e ponto vêm dos novos não vistos no Room.
  Abrir a Caixa marca vistos; mostrar o quadro de captura também marca aquela sugestão vista.
- Captura: Salvar confirma diretamente apenas com dados completos e sem possível duplicata;
  faltando informação, abre a revisão existente. Editar abre a instância preenchida do formulário.
  Ignorar, Salvar e Desfazer continuam transacionais; desfazer/reativar uma sugestão reabre seu estado.
  Testes/simulações continuam sem gravar movimento financeiro.
- Pagar usa a mesma operação da Agenda. Parcela de dívida continua abrindo sua folha antes do pagamento.
  Lembrar no dia adia até o vencimento; se hoje/vencida, até amanhã. O app reavalia ao retomar e
  na virada do dia; worker reavalia quando o Android o executar.
- Worker usa a regra nova de coach e respeita “Já sei”, dispensa, resolução e adiamento.
  Dedupe diário continua; só marca envio quando a notificação foi publicada com permissão.
  Intent de notificação financeira abre a Caixa. Dispensar/adiar/pagar cancela o lote já entregue.
- Backup leva os estados, sem capturas de teste; restaurar/limpar inclui a tabela nova.
  A sincronização evita persistir snapshots intermediários de tabelas durante restauração/limpeza.

## Validação

- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`: passaram.
- **461 testes JVM, zero falhas**: 12 novos de regras/catálogo e um de backup com estados da Caixa.
- **14 instrumentados direcionados passaram** no Pixel 6 API 34: `InboxV2Test` (3),
  `Migration16To17Test` (1), `Migration15To16Test` (1), `FinaiDatabaseMigrationTest` (2),
  `CaptureRepositoryTest` (6), `FinanceRepositoryResetTest` (1).
- **Dois smokes do app completo passaram**, em 390×844 dp/fonte 100% e 320×844 dp/fonte 150%,
  ambos nos temas claro/escuro. Exercitam intent de notificação financeira com Activity já aberta,
  três grupos, salvar/desfazer/ignorar sugestão simulada, pagar fatura, dispensar limite,
  recriar Activity sem reaparecer ponto para visto/aviso dispensado e esvaziar a Caixa.
- **14 capturas exportadas** para `capturas/`. Conferidos hierarquia, cores, ações, rodapé e vazio.
  Fonte ampliada empilha ações em largura inteira; os rótulos mantêm palavras inteiras.
- Registros: `instrumentados.txt`, `smoke-390.txt`, `smoke-320-font150.txt`.
  Não são resultados da suíte instrumentada completa.
- A primeira execução em 320 dp falhou no roteiro ao procurar rodapé ainda não composto pela
  LazyColumn. O teste passou a rolar pela lista até o nó. A revisão de capturas também encontrou
  palavras quebradas nos botões lado a lado; a UI foi corrigida e os smokes repetidos.

## Diferenças e regularização posterior

1. Gastos/Planos são fases 6–7. “Ver gastos” ainda abre Limites; metas e dívidas abrem destinos atuais.
   Os cálculos/conteúdo de metas ainda são os existentes; a fase 7 troca o modelo de planejamento.
2. A tela usa os botões verdes e o cabeçalho do app. Valor fica numa linha própria para preservar
   leitura com fonte grande. Editar e Dispensar também ficam explícitos como ações de texto;
   as duas ações principais empilham com fonte ≥ 130% ou espaço insuficiente.
   Não é reprodução por pixel; mantém ordem, agrupamento, cores e ações do protótipo.
3. Notificação do Android não é alarme de horário exato: adiamento reaparece na data ao abrir o app;
   entrega em segundo plano depende da execução do WorkManager e da permissão do aparelho.
4. “Já sei” continua em DataStore, com estado na Caixa e regra compartilhada com o worker.
   Memória consultável pelo assistente é fase 9. “Desfazer tudo” da Caixa não limpa essa memória.
5. Identificadores antigos com título não tinham referência monetária gravada. A migração conserva
   a dispensa e usa o valor da primeira observação como referência, sem inventar o valor histórico.
6. Teste em celular, TalkBack real, APIs antigas, banco real, entrega periódica de 24 horas,
   modelos de IA reais e suíte instrumentada completa **não executados** nesta fase.
   Sem novos modelos/provedores, permissões ou chamadas de IA na tela.
7. O APK pessoal da fase 2 não representa esta fase enquanto `assemblePessoal` não for refeito.
   Marco M2 só se encerra após fases 3–5 e uso no celular. M1 também segue pendente.

## Repetir no emulador

Nunca usar o broadcast de demonstração no celular com dados reais: ele substitui dados de teste.

```text
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug testDebugUnitTest assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am broadcast -a com.finai.app.DEBUG_LOAD_DEMO -n com.finai.app/.debug.DemoScenarioReceiver
adb -s emulator-5554 shell am instrument -w -e class com.finai.app.InboxV2VisualTest -e qaName 390 com.finai.app.test/androidx.test.runner.AndroidJUnitRunner
```

Data do emulador: 08/11/2026. Para 390 dp, tamanho 780×1688/densidade 320/fonte 1.0;
para 320 dp, tamanho 640×1688/densidade 320/fonte 1.5. Recarregar demonstração entre execuções.
O smoke acrescenta uma sugestão **simulada**, gasto e limite de QA; não é captura real de banco.
Capturas em `/sdcard/Android/data/com.finai.app/files/v2-qa/fase-3/`, exportadas para `capturas/`.
Restaurar configuração anterior ao terminar (nesta sessão: 1236×2676, densidade 480, fonte 1.0).
