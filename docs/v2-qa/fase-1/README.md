# QA · Fase 1 (motor de saldo)

Protótipo v2 **acessível após login** em 08/11/2026. Comparação regularizada contra Hoje/Livre/Agenda
e regras 1–3, vistos no artefato autenticado. A captura antiga `inicio-demo.png` é histórica,
anterior à correção do cenário e à nova Hoje; evidência atual na pasta da fase 2.
Cenário: `DemoScenarioV2` (debug), emulador Pixel 6 API 34 em 08/11/2026.

| Item do checklist | Igual | Diferente (por quê) | Print |
| --- | --- | --- | --- |
| Início mostra R$ 1.220 livre | Sim, contra o artefato real e o teste do cenário corrigido | — | `../fase-2/capturas/390-hoje-dark.png` |
| Rótulo "até o adiantamento · 20/11", 12 dias | Sim | — | `../fase-2/capturas/390-hoje-dark.png` |
| R$ 3.480 em conta hoje | Sim | — | `../fase-2/capturas/390-hoje-dark.png` |
| Menor saldo R$ 1.520 em 18/11 | Sim | — | `../fase-2/capturas/390-livre-dark.png` |
| R$ 101 por dia | Sim, recapturado após a correção do arredondamento | — | `../fase-2/capturas/390-hoje-dark.png` |
| Agenda "A seguir" e chat com o mesmo livre | Pelo código (`DemoScenarioV2Test.everyScreenGetsTheSameFree`) | Não conferido na tela | — |

**Correção importante:** o total já batia, mas sua composição era presumida. Agora os quatro
movimentos são os da lista expandida do protótipo: 1.320 + 110 + 120 + 410 = R$ 1.960. Picpay
está dentro da janela, em 18/11, e não em 05/12. `DemoScenarioV2Test` confere valores e datas.

**Diferenças de regra preservadas:** a decisão posterior da §8 usa margem fixa de R$ 300;
o quadro sugeria quatro dias de gasto. Usar a margem sem ficar negativo continua com livre zero,
sem falta (decisão já documentada na fase 1). Conciliação e saldo são globais, sem saldo por conta.
Isso não foi apresentado como igualdade literal com todas as regras desenhadas.
