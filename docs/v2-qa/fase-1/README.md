# QA · Fase 1 (motor de saldo)

Protótipo v2 **inacessível** em 08/11/2026; a referência foram os números do `planning-v2.md`.
Cenário: `DemoScenarioV2` (debug), emulador Pixel 6 API 34 em 08/11/2026.

| Item do checklist | Igual | Diferente (por quê) | Print |
| --- | --- | --- | --- |
| Início mostra R$ 1.220 livre | Sim | — | `inicio-demo.png` |
| Rótulo "até o adiantamento · 20/11", 12 dias | Sim | — | `inicio-demo.png` |
| R$ 3.480 em conta hoje | Sim | — | `inicio-demo.png` |
| Menor saldo R$ 1.520 em 18/11 | Sim | — | `inicio-demo.png` |
| R$ 101 por dia | Corrigido (a tela mostrava R$ 102; agora arredonda para baixo) | Não reconferido na tela depois da correção | — |
| Agenda "A seguir" e chat com o mesmo livre | Pelo código (`DemoScenarioV2Test.everyScreenGetsTheSameFree`) | Não conferido na tela | — |
