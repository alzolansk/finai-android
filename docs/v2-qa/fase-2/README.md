# QA · Fase 2 — Hoje e De onde vem o livre

08/11/2026. Room v16; sem migração ou alteração no formulário de lançamento.

## Referência e comparação

Após o login feito pelo usuário, o [artefato autenticado](https://claude.ai/artifact/Tzb24N1BzGMR8buHwVAtL9#page-5634c5cea0db)
carregou a página Protótipo v2. Conferidos Hoje e Livre em Play, pagamento/reabertura da linha,
lista expandida dos quatro compromissos, Lançar, Agenda, Estrutura e regras de cálculo.
A hierarquia, o conteúdo e a ordem foram comparados com as capturas nativas; não é uma reprodução por pixel.

| Item do checklist | Igual | Diferente (por quê) | Print em `capturas/` |
| --- | --- | --- | --- |
| Hoje e data, livre, 7 dias, coach, metas | Ordem implementada; fechamento escondido até a fase 10 | Coach só aparece com histórico que passa da regra; o cenário não inventa pedidos | `390-hoje-dark.png` |
| Um número principal: R$ 1.220 e R$ 101/dia, 12 dias até 20/11 | Conferido na tela e em `DemoScenarioV2Test` | Com falta o valor principal é substituído, coberto pelo instrumentado | `390-hoje-dark.png` |
| Barra: vai sair R$ 1.960, margem R$ 300, livre R$ 1.220 | Três partes e legendas | Tokens nativos nos dois temas; legendas usam tokens de texto com contraste | `390-hoje-dark.png`, `390-hoje-light.png` |
| Em conta R$ 3.480, conferido há 2 dias e Por quê | Sim; abre a tela inteira de explicação | Sem número fictício de itens na Caixa: usa o ponto antigo até a fase 3 | `390-hoje-dark.png` |
| Posso comprar? e Conferir saldo | Os dois caminhos abrem as funções existentes/explicação | Simulador novo é fase 8 | `390-hoje-dark.png` |
| 7 dias com avatar, sem caixinha; selo Pago e valor esmaecido | Conferido contra o estado Pago do protótipo e no app com Room | Avatar circular nativo e data/status no subtítulo, sem inventar a forma de pagamento que a conta não guarda | `390-hoje-pago.png` |
| Pagar/reabrir recalcula saldo e mantém o livre | App completo e teste com Room | O HTML muda só o selo; o app recalcula: fatura paga deixa R$ 2.160 em conta e R$ 640 ainda a sair, com o mesmo livre R$ 1.220 | `390-hoje-pago.png` |
| Sem decisões, linha do ano ou segundo cartão de saldo | Removidos da composição da Hoje | Balanço continua disponível nas telas que já o exibem; novos destinos são fases 4/6/7 | `390-hoje-dark.png` |
| Gráfico com hoje claro, mínimo âmbar em 18/11 e entrada verde | Série do motor; mínimo R$ 1.520, entrada de R$ 2.400 em 20/11 | Barra da entrada é referência, fora da janela usada no cálculo. Fonte ampliada permite rolagem horizontal | `390-livre-dark.png` |
| Conta de cinco linhas e saídas expansíveis | 3.480 − 1.960 = 1.520 − 300 = 1.220; valores e datas exatos | Quando há entradas no período, explica a queda líquida até o mínimo. Se usa a margem, livre é limitado a zero; falta não soma a margem | `390-livre-dark.png`, `390-livre-movimentos.png` |
| Margem Sem/150/300/500/outro, persistida | Opções fixas e personalizada, DataStore e recálculo do mesmo motor | Texto usa R$ 300 fixos por decisão da §8, em lugar da sugestão dinâmica desenhada | `390-livre-banco.png` |
| Conferir com o banco no fim e nota A sobra não some | Formulário inline, diferença, confirmação, estado de salvamento e erro | Conciliação global; seleção indica onde registrar o ajuste, não um saldo individual | `390-livre-banco.png` |
| Categorias da fase 0 | Nomes conferidos no artefato e faixa real rolada até o fim | Ícones nativos e ordem por uso recente/canônica; faixa de voz/texto só na fase 5 | `390-categorias-inicio.png`, `390-categorias-fim.png` |

As comparações das fases anteriores estão também em [fase 0](../fase-0/README.md) e [fase 1](../fase-1/README.md).

## Evidência de execução

- `assembleDebug testDebugUnitTest assembleDebugAndroidTest`: compilados; **448 testes JVM verdes**.
  Incluem nove testes de `domain/HomeV2Test` (semana, fatura sem duplicar compra, virada do mês,
  limiar estrito de 1,6×, três meses, futuro excluído, sem histórico, dispensa e categoria de sistema)
  e os três testes do cenário debug, agora com composição e datas conferidas.
- `com.finai.app.HomeV2Test`: **4 instrumentados verdes**, repetidos na versão final no Pixel 6 API 34.
  Pagamento e reabertura com Room, estado semântico pago, falta
  substituindo livre, margem persistida e confirmação bancária, inclusive saldo zero.
- `HomeV2VisualTest`: **duas execuções finais verdes**, no app completo e cenário debug,
  em 390 × 844 dp/fonte 100% e 320 × 844 dp/fonte 150%, nos dois temas.
  Pagamento/reabertura, explicação, lista expandida, formulário e categorias até o fim.
  Capturas finais exportadas e conferidas; o sucesso do fluxo não aprova as ressalvas visuais abaixo.
- `assemblePessoal`: **verde**, minificação R8 e recursos reduzidos; APK de **16.789.674 bytes**
  em `app/build/outputs/apk/pessoal/app-pessoal.apk`, para o marco M1 (uso pessoal, arm64).
  SHA-256: `e7c1a3f9d5e1f07bfc5fa91d5c7b768843db4c52b4a58ecf149ef204f3c6a481`.
  Não instalado em celular físico nesta sessão. M1 no celular continua pendente.

## Repetir a conferência

1. Usar **somente emulador de teste**, na data 08/11/2026. Instalar os APKs debug e androidTest.
   O broadcast abaixo **substitui o cenário financeiro**, reseta a margem/dispensas e conclui
   onboarding/dicas no debug para viabilizar o teste; não existe no APK pessoal:

   ```text
   adb -s emulator-5554 shell am broadcast -a com.finai.app.DEBUG_LOAD_DEMO -n com.finai.app/.debug.DemoScenarioReceiver
   adb -s emulator-5554 shell wm size 780x1688
   adb -s emulator-5554 shell wm density 320
   adb -s emulator-5554 shell settings put system font_scale 1.0
   ```

2. Rodar os fluxos isolados e o smoke (o smoke é ignorado sem o argumento `qaName`):

   ```text
   adb -s emulator-5554 shell am instrument -w -e class com.finai.app.HomeV2Test com.finai.app.test/androidx.test.runner.AndroidJUnitRunner
   adb -s emulator-5554 shell am instrument -w -e class com.finai.app.HomeV2VisualTest -e qaName 390 com.finai.app.test/androidx.test.runner.AndroidJUnitRunner
   ```

3. Para 320 dp, usar `wm size 640x1688` e `font_scale 1.5`; repetir com `qaName 320-font150`.
   A conferência inicial usou 1170×2532/densidade 480; a final reduziu para 2 pixels por dp,
   mantendo o tamanho em dp e poupando memória no emulador.
   Capturas em `/sdcard/Android/data/com.finai.app/files/v2-qa/`. O swipe no smoke traz a linha
   dos 7 dias para cima da barra flutuante antes do toque físico.
4. Restaurar tamanho, densidade e fonte anteriores ao terminar. Neste ambiente os valores
   anteriores eram override 1236×2676, densidade 480 e fonte 1.0.

## Ressalvas para regularização

- Em 320 dp/fonte 150%, os cartões novos aumentam de altura e os botões quebram linha; a
  **barra de navegação antiga trunca rótulos**. O `MoneyRow` pago também comprime o título
  até reticências e reduz o subtítulo quando avatar, selo e valor disputam a largura
  (`320-font150-hoje-pago-linha.png`); o conteúdo completo permanece na semântica.
  Rever o arranjo da linha e a adaptação da navegação/fase 11;
  não tratar essa configuração como acessibilidade final aprovada.
- TalkBack **real**, celular físico, APIs antigas e suíte instrumentada completa não foram
  validados. A semântica de estado foi verificada pelo teste, o que não substitui escuta/navegação com TalkBack.
- Caixa ainda é o painel antigo e a navegação mantém Objetivos/Dívidas. Ver gastos abre Limites,
  Planos abre Objetivos; fases 3/6/7 completarão os destinos.
- Coach da tela usa a regra nova e dispensa em DataStore; worker continua com a regra antiga.
  Memória compartilhada do assistente é fase 9. Mostrar dispensados em Objetivos ainda limpa o
  conjunto compartilhado, incluindo coach.
- O histórico de pedidos e despesas para cobertura da reserva não foi inventado no cenário.
  Por isso não há coach de delivery fixo, cobertura de 55 dias nem recomendação fixa de R$ 600
  no Chile: o plano atual prioriza Picpay pelos juros de 4,2%. A fase 7 tratará o plano novo.
  A conciliação do cenário usa âncora sintética de R$ 3.480 e data de conferência em 06/11;
  não reconstitui o histórico de R$ 3.515/−R$ 35 mostrado no quadro de regras.
- Sem renda recorrente, permanece o fallback mensal, explicitado na explicação. Com renda,
  continua a decisão anterior: falta só com saldo previsto negativo; usar parte da margem
  deixa livre zero. A conta do banco é global, como já era.
- O primeiro disparo Gradle dos instrumentados falhou sem executar testes porque o emulador
  ficou offline. Uma repetição do smoke com fonte ampliada mostrou que o toque do teste podia
  cair sobre a barra flutuante e o link Por quê podia ficar sob o cabeçalho após rolar; o roteiro
  passou a trazer a linha ao meio, voltar ao topo para abrir a explicação e esperar o fluxo do
  Room/a nova tela. Build minificado e emulador devem rodar em sequência nesta máquina
  por pressão de memória. Não confundir essas tentativas com evidência de aprovação.

Estado e escopo posteriores: [planning-v2.md](../../../planning-v2.md).
