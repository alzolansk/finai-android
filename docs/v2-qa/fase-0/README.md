# QA · Fase 0 (categorias únicas, linha de lista, cenário de demonstração)

Comparação regularizada em 08/11/2026 após login do usuário no Claude. Referência real: página
"Protótipo v2" de "FinAI — Auditoria de design e IA", quadros Hoje/Agenda/Lançar e Estrutura.
A tentativa anterior sem autenticação permanece apenas como histórico no planning.

| Item do checklist | Igual | Diferente (por quê) | Print |
| --- | --- | --- | --- |
| Faixa de categorias com as 10, na ordem de uso recente | Nomes conferidos contra Lançar; faixa real rolada até Assinaturas/Outros, além de `CategoriasTest` e `EntryOptionsTest` | Ícones nativos em vez de siglas; sem histórico de gastos a ordem inicial é a canônica. Faixa de Falar/Escrever fica na fase 5 | `../fase-2/capturas/390-categorias-inicio.png`, `390-categorias-fim.png` |
| Linha da Agenda: avatar, selo "Pago", valor esmaecido, sem caixinha | Aparência conferida contra Hoje em Play, incluindo o selo ao tocar; `MoneyRow` agora usado na Hoje e testado com Room | Avatar circular nativo; subtítulo usa data/status, pois a conta não guarda Pix/débito. Agenda ainda aguarda integração da fase 4 | `../fase-2/capturas/390-hoje-pago.png` |
| Mesma categoria no "+", Limites, importação e prompt da IA | Todos leem `Categorias.all` | — | — |

**Cenário corrigido pela fonte visual:** academia R$ 110 em 12/11, internet R$ 120 em 15/11,
Picpay R$ 410 em 18/11 (5/12). Os valores presumidos de academia/condomínio foram removidos.
`DemoScenarioV2Test` confere a composição real, não apenas o total coincidente de R$ 1.960.
As metas também foram alinhadas aos saldos/alvos mostrados no artefato.

**Pendência delimitada:** comparação feita e diferenças registradas; a linha da Agenda ainda
não está regularizada na tela da Agenda. Não marcar essa integração como concluída antes da fase 4.
