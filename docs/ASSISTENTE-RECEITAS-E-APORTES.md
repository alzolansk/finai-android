# Receitas previstas e aporte por data

Correção dos relatos de 14/11/2026. O cartão mensal com “mín.” e “est.” mostrava menores saldos do mês; esses valores não eram uma resposta a quanto aportar em 15/01/2027.

## Pedidos atendidos

- “Preciso dividir o décimo terceiro em duas vezes, a primeira parcela vai cair 30/11”: procura a receita avulsa futura em lançamentos e contas a receber, inclusive dezembro. Propõe duas parcelas preservando o total cadastrado. Se há dois registros plausíveis, pergunta qual. Se só foi informada a primeira data, propõe a data original na segunda, explicitamente. A proposta usa metade em cada parcela (eventual centavo na segunda) e pede confirmação. Não calcula descontos de décimo terceiro.
- “A segunda vai cair 22/12”: corrige a proposta e pede confirmação do conjunto atualizado. “A primeira é de R$ 1.000”: recalcula o restante a partir do total cadastrado.
- “Onde está cadastrado o décimo terceiro?”: consulta os meses seguintes e mantém a referência encontrada. “Recebeu sim, está pro dia 20/12” também consulta pela data quando não há referência anterior.
- “Corrija para 22/12”: com uma receita futura em foco, altera o registro existente. Uma data que já está correta não cria lançamento.
- “Seguindo as tendências atuais, quanto vou poder colocar na reserva dia 15/01/2027”: consulta local específica `capacidade_aporte`, sem escrita nem chamada de IA. Bônus, comissão, restituição, freela e reembolso também podem ser localizados/divididos/adiados quando são receitas avulsas futuras existentes.

## Regra do aporte

O cálculo usa a projeção diária comum (`CashFlowProjection`). Projeta da data atual até o fim do terceiro mês seguinte à data do aporte, limitado ao horizonte do motor (24 meses). Calcula `max(0, menor saldo da data do aporte até o fim do horizonte − margem)`, com a provisão variável já usada pelo app. Portanto uma conta que vence depois do aporte limita o valor que pode ser retirado do caixa. Se o caixa já fica negativo antes da data pedida, a resposta pede cobrir essa falta antes de recomendar um aporte.

O cartão informa valor, data, saldo naquele dia, margem, menor saldo posterior e horizonte. Com menos de dois meses de gastos variáveis, mostra um teto incompleto e informa que não é recomendação. Receitas futuras precisam se confirmar. Outros aportes planejados não registrados não são descontados, e dinheiro já guardado em metas não é contado como caixa. É estimativa condicionada aos registros, não garantia. Datas passadas ou fora do horizonte pedem esclarecimento; uma data sem ano de planejamento, como 15/01 em novembro, se refere ao próximo janeiro.

## Persistência e conversa

Não houve alteração de schema ou migração. A divisão usa duas `RowChange` no executor transacional existente: altera o registro original e cria somente o restante, com recibo, idempotência e Desfazer. A proposta prende o tipo, ID e versão do registro original; mudança externa invalida a confirmação. Salário recorrente, receita recebida e renda principal dividida continuam nos seus caminhos próprios.

Os catálogos continuam disponíveis após uma consulta, permitindo consultar antes de agir. O prompt orienta que o assistente está no próprio app. O orquestrador tenta reparar respostas que mandam “pedir ao app”, suprime essas instruções se persistirem e não aceita texto da IA afirmando ter gravado sem passar pelo executor. Isso não substitui a avaliação com provedores reais.

## Verificação

- Executados: suíte JVM completa (`testDebugUnitTest`), **651 testes, zero falhas/erros/ignorados**; `AssistantIncomeActionsTest` no Pixel 6 API 34, **2 testes verdes**, após instalar o debug recompilado. `git diff --check` limpo.
- Builds finais: `assembleDebug`, `assemblePessoal` e `assembleDebugAndroidTest` concluídos. APK pessoal: `app/build/outputs/apk/pessoal/app-pessoal.apk`. Não houve commit nem teste com provedores reais ou celular físico.
- Regressões JVM: `AssistantRequestsRegressionTest`, além das suítes existentes do assistente. Dados sintéticos, incluindo os textos dos relatos, preservação de centavos, ambiguidade, correção, fonte alterada, falta de histórico, falta anterior e gasto depois do aporte.
- Room: `AssistantIncomeActionsTest`, com persistência após reabrir, repetição da chave de operação, conflito de fonte alterada e Desfazer de lançamentos e contas a receber.
- Limites: a divisão é em duas parcelas de uma receita futura já cadastrada. Valores/datas fora desse caso não são improvisados. UI completa, acessibilidade, celular físico e interpretação com provedores reais exigem validação própria.
