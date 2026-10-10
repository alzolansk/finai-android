<div align="center">

<img src="https://api.iconify.design/lucide/wallet.svg?color=%2310b981" width="64" height="64">

# FinAI

### O planejador do seu dinheiro até a próxima entrada.

**Quanto posso gastar? O que vence primeiro? Por que o app chegou a esse valor?**
Três perguntas. Uma tela. Resposta certa, na hora.

<br>

![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-v10-10B981?style=for-the-badge)
![Testes](https://img.shields.io/badge/testes%20JVM-286%20verdes-10B981?style=for-the-badge)
![Custo](https://img.shields.io/badge/custo%20de%20opera%C3%A7%C3%A3o-R%24%200%2C00-18181B?style=for-the-badge)

</div>

---

## <img src="https://api.iconify.design/lucide/sparkles.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; O que é o FinAI

O FinAI é um assistente financeiro pessoal **nativo para Android** que olha para o seu dinheiro como ele realmente se comporta: em ciclos, de uma entrada até a próxima. Ele junta contas, faturas, dívidas, metas e limites num único fluxo e entrega um número em que dá para confiar: **quanto está livre até o salário**, já descontado tudo que vai vencer no caminho.

Por trás disso existe uma ideia simples e radical:

> **A IA cuida da linguagem. O cálculo é local, determinístico e seu.**

Nenhum número do app sai de um modelo de linguagem. Saldo seguro, juros, ordem de ataque das dívidas, progresso de metas, status de conta: tudo é Kotlin puro, testado, rodando no aparelho. A IA entra só para explicar, conversar e redigir. Resultado: contas que não erram, privacidade de verdade e um app que cabe inteiro no free tier.

---

## <img src="https://api.iconify.design/lucide/layout-grid.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Funcionalidades

| | Tela | O que entrega |
|---|---|---|
| <img src="https://api.iconify.design/lucide/house.svg?color=%2310b981" width="20" height="20" align="top"> | **Início** | A situação do momento em uma frase e um valor: o **livre até o salário**, com o "Pode gastar hoje" como apoio e um toque em **"Entenda este valor"** para ver cada centavo da conta. Depois, próximos vencimentos, decisões sugeridas, objetivos e a linha do tempo do ano. |
| <img src="https://api.iconify.design/lucide/calendar-days.svg?color=%2310b981" width="20" height="20" align="top"> | **Agenda** | Contas a pagar e a receber com status vivo (pago, pendente, atrasado, vence hoje, previsto). Visão **Mensal** pelo vencimento e visão **Sequencial**, que percorre os ciclos entre salários mostrando o saldo depois de cada movimento. |
| <img src="https://api.iconify.design/lucide/target.svg?color=%2310b981" width="20" height="20" align="top"> | **Objetivos** | Capacidade de poupança calculada, progresso de cada meta, selo de ritmo e a leitura da IA sobre conflitos e oportunidades (como direcionar um bônus). |
| <img src="https://api.iconify.design/lucide/trending-down.svg?color=%2310b981" width="20" height="20" align="top"> | **Dívidas** | Total em aberto, juros por mês, previsão de quitação e **ordem de ataque pelo custo do juro**. Roteiro de negociação passo a passo e a opção de **ensaiar a ligação** no chat. |
| <img src="https://api.iconify.design/lucide/chart-pie.svg?color=%2310b981" width="20" height="20" align="top"> | **Limites** | Orçamento por categoria com projeção de fechamento do mês e detecção de assinaturas pouco usadas. |
| <img src="https://api.iconify.design/lucide/receipt.svg?color=%2310b981" width="20" height="20" align="top"> | **Importar fatura** | PDF, planilha ou foto. OCR **no próprio aparelho**, parsing determinístico, categorização, detecção de parcelas, assinaturas e duplicatas, com revisão antes de entrar na Agenda. O arquivo original nunca sai do celular. |
| <img src="https://api.iconify.design/lucide/shopping-cart.svg?color=%2310b981" width="20" height="20" align="top"> | **Posso comprar?** | Simulador com veredito claro (cabe, cabe mas custa tempo, não cabe) e o efeito da compra na folga do mês, nas metas e na reserva de emergência. |
| <img src="https://api.iconify.design/lucide/message-circle.svg?color=%2310b981" width="20" height="20" align="top"> | **Assistente** | Chat que fala dos **seus** números. Pergunte "posso comprar uma cadeira de R$ 1.000 em 4x?" e o app extrai a intenção, pergunta o que faltar, simula ciclo a ciclo e só então deixa a IA redigir. Se a resposta citar um valor fora da simulação, ela é descartada. |
| <img src="https://api.iconify.design/lucide/bell.svg?color=%2310b981" width="20" height="20" align="top"> | **Notificações e coach** | Rotina diária que recalcula tudo localmente e só avisa quando algo relevante acontece: fatura vencendo, categoria no limite, entrada extra, padrões de gasto. |

---

## <img src="https://api.iconify.design/lucide/shield-check.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Arquitetura de confiança

```
┌───────────────────────────────────────────────────────────────┐
│                         Jetpack Compose                        │
│        Início · Agenda · Objetivos · Dívidas · Limites         │
│          Importar · Simulador · Assistente · Avisos            │
└───────────────────────────────┬───────────────────────────────┘
                                │  StateFlow (MVVM)
┌───────────────────────────────▼───────────────────────────────┐
│                     FinanceViewModel                           │
└──────────────┬────────────────────────────────┬───────────────┘
               │                                │
┌──────────────▼──────────────┐   ┌─────────────▼───────────────┐
│   domain/  (Kotlin puro)    │   │   data/ai/  AiRouter        │
│  SafeToSpend · PayCycle     │   │  Gemini → Groq → OpenRouter │
│  CashFlowTimeline · Debts   │   │     → Mistral → Cerebras    │
│  Goals · Budgets · Alerts   │   │  só redige o que já foi     │
│  importer/ · scenario/      │   │  calculado no aparelho      │
└──────────────┬──────────────┘   └─────────────────────────────┘
               │
┌──────────────▼──────────────┐
│  Room (SQLite) · DataStore  │
│  EncryptedSharedPreferences │
│  a fonte de verdade é local │
└─────────────────────────────┘
```

### <img src="https://api.iconify.design/lucide/shuffle.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Cinco provedores, zero reais

O roteador de IA tenta cada provedor gratuito em ordem, controla a cota diária de cada um localmente, faz cache de respostas repetidas e, se todos estiverem esgotados, **o app continua funcionando**: os números seguem calculados e só o texto explicativo avisa que volta amanhã.

- **Gemini** como principal, inclusive para visão na importação
- **Groq**, **OpenRouter** (`:free`), **Mistral** e **Cerebras** como fallback de texto
- **Trava de custo zero**: cada adaptador fixa um modelo gratuito e o roteador nunca troca para um tier pago

### <img src="https://api.iconify.design/lucide/lock.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Privacidade por padrão

- Lançamentos, saldos, dívidas e faturas ficam **só no aparelho**
- Prompts minimizados: nada de documento original, nome completo, CPF ou número de conta
- Chaves de API no **Android Keystore**, nunca em texto puro
- R8/ProGuard ligado, tráfego sem criptografia bloqueado, backup automático desligado
- **Exportar e restaurar dados** num arquivo que você controla

Detalhes completos em [`PRIVACY.md`](PRIVACY.md).

---

## <img src="https://api.iconify.design/lucide/wrench.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 1.9 |
| UI | Jetpack Compose (BOM 2024.02), Material 3, Navigation Compose |
| Persistência | Room 2.6 (KSP) com migrações reais até a versão 10, DataStore |
| Segurança | EncryptedSharedPreferences, Android Keystore, R8 |
| Background | WorkManager |
| OCR | ML Kit Text Recognition (on-device) |
| IA | Gemini, Groq, OpenRouter, Mistral, Cerebras (todos no plano gratuito) |
| Alvo | `minSdk 26`, `targetSdk 34`, pacote `com.finai.app` |

---

## <img src="https://api.iconify.design/lucide/terminal.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Rodando o projeto

```bash
# APK de desenvolvimento
./gradlew assembleDebug

# APK pessoal: release minificado, só arm64, ~16 MB
./gradlew assemblePessoal

# Testes de unidade (JVM)
./gradlew testDebugUnitTest

# Testes instrumentados (emulador ou aparelho)
./gradlew connectedDebugAndroidTest
```

Instale com `adb install` e configure as chaves dos provedores de IA dentro do próprio app, em Configurações. Nenhuma chave vai no código.

---

## <img src="https://api.iconify.design/lucide/map.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Jornada

| Fase | Entrega | |
|---|---|---|
| 0 | Fundamentos: projeto, design system e todas as telas navegáveis | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 1 | MVP sem IA: entrada manual e todos os cálculos determinísticos | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 2 | Camada de IA com Gemini: chat, leituras, veredito, roteiro | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 3 | Multi-provedor, cota local, cache e degradação graciosa | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 4 | Importação de fatura com OCR on-device | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 5 | Notificações proativas e coach comportamental | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 6 | Endurecimento: ofuscação, chaves protegidas, migrações, privacidade | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |
| 7 | Clareza e confiança: veredito único, "Entenda este valor", Home por urgência, contraste ≥ 4,5:1, onboarding mínimo, exportação de dados | <img src="https://api.iconify.design/lucide/circle-check.svg?color=%2310b981" width="18" height="18" align="top"> |

**Próximos horizontes:** previsão por data com "e se eu adiar ou antecipar", simulação ligada ao prazo das metas, renda variável e múltiplos recebimentos.

---

## <img src="https://api.iconify.design/lucide/book-open.svg?color=%2310b981" width="22" height="22" align="top">&nbsp; Documentação

- [`planning.md`](planning.md): requisitos, arquitetura, estratégia de IA e roadmap
- [`CLAUDE.md`](CLAUDE.md): status detalhado e convenções do repositório
- [`PRIVACY.md`](PRIVACY.md): o que sai do aparelho e o que fica
- [`RELEASE.md`](RELEASE.md) e [`play-store/`](play-store/): referência para uma eventual publicação
- [`project/`](project/) e [`chats/`](chats/): protótipo original e transcript de design

---

<div align="center">

**FinAI** · feito para uso pessoal, com cálculo honesto e IA que só fala quando tem algo verdadeiro para dizer.


</div>
