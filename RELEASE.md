# FinAI · Build de release e publicação

Fase 6 do `planning.md` (§9). Tudo que depende de uma credencial, conta ou
decisão sua está marcado com **[VOCÊ]** — nada disso foi inventado no código.

---

## 1. Estado do build de release

Já configurado em `app/build.gradle.kts`, nada a fazer:

- **R8 ligado** (`isMinifyEnabled = true`) + `isShrinkResources = true`.
  Regras em `app/proguard-rules.pro`, com o motivo de cada uma. APK de release
  sai em ~44 MB contra ~60 MB do debug (o grosso é o modelo de OCR do ML Kit,
  que vai empacotado de propósito para funcionar offline).
- **`BuildConfig.DEBUG`** gerado (`buildFeatures.buildConfig = true`), que é o
  que apaga os logs de diagnóstico em release (`util/FinaiLog.kt`).
- **Idiomas limitados** a `pt`, `pt-rBR` e `en` — o app é pt-BR e as dezenas de
  traduções que Compose/Material arrastam não são alcançáveis.
- **Assinatura** lida de `keystore.properties` (§2). Sem o arquivo, o build gera
  um APK não assinado em vez de falhar.
- **`lint` com `abortOnError = true`** para release.

Verificar a qualquer momento:

```bash
./gradlew assembleDebug testDebugUnitTest   # 102 testes JVM
./gradlew assembleRelease                   # R8 + shrink
./gradlew connectedDebugAndroidTest         # precisa de emulador/aparelho
```

## 2. [VOCÊ] Criar o keystore de assinatura

Uma vez só, e **guarde o arquivo e as senhas** — sem Play App Signing, perder o
keystore significa nunca mais poder atualizar o app publicado.

```bash
keytool -genkeypair -v \
  -keystore ~/chaves/finai-release.jks \
  -alias finai -keyalg RSA -keysize 4096 -validity 10000
```

Depois copie `keystore.properties.example` para `keystore.properties` (raiz do
projeto) e preencha os quatro campos. O arquivo está no `.gitignore`.

Recomendado: ativar **Play App Signing** na Play Console (é o padrão hoje). A
Google passa a guardar a chave de assinatura final e o seu keystore vira só a
chave de upload — que pode ser substituída se você a perder.

Conferir depois de gerar:

```bash
./gradlew bundleRelease
# app/build/outputs/bundle/release/app-release.aab  → é este arquivo que sobe
```

## 3. [VOCÊ] Nível de API alvo — **bloqueio de publicação**

Hoje o projeto está em `compileSdk`/`targetSdk` **34**. A Play Store exige que
apps novos e atualizações tenham como alvo uma API recente (a regra sobe todo
ano, no fim de agosto). **Confirme o nível exigido hoje em
https://developer.android.com/google/play/requirements/target-sdk antes de
submeter** — se for maior que 34, a submissão é recusada.

Isto não foi alterado nesta fase de propósito: subir `targetSdk` muda
*comportamento em tempo de execução* (edge-to-edge obrigatório a partir da 35,
alinhamento de página de 16 KB para bibliotecas nativas como a do ML Kit,
mudanças de serviço em primeiro plano), e o único emulador disponível aqui é
API 34 — não haveria como validar a mudança, só apostar nela.

Roteiro quando for fazer:

1. `sdkmanager "platforms;android-36" "system-images;android-36;google_apis;x86_64"`
   e criar um AVD nessa API.
2. Subir o AGP em `gradle/libs.versions.toml` (`agp = "8.3.1"` não conhece a
   API 36; 8.9+ conhece) e conferir o Gradle correspondente.
3. Subir `compileSdk`/`targetSdk` e o ML Kit para uma versão com libs alinhadas
   em 16 KB.
4. Revalidar no emulador novo: insets de todas as telas, seletor de arquivo da
   importação, notificações, e a rotina do WorkManager.

## 4. [VOCÊ] Permissão FOREGROUND_SERVICE

O APK declara `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` e
`ACCESS_NETWORK_STATE` — todas herdadas do WorkManager, nenhuma pedida em
runtime (ver o comentário no `AndroidManifest.xml`).

`FinanceCheckWorker` **não** usa trabalho em primeiro plano nem expedido, então
`FOREGROUND_SERVICE` é a única que o app realmente não exerce. Ela ficou porque
removê-la exige tirar junto o `SystemForegroundService` da própria biblioteca, o
que é um caminho não suportado e que só daria para validar em vários aparelhos.

Consequência prática: a Play Console pode pedir uma declaração de uso de serviço
em primeiro plano. Se quiser evitar, o caminho é:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" tools:node="remove" />
<service android:name="androidx.work.impl.foreground.SystemForegroundService" tools:node="remove" />
```

…e então **testar a rotina proativa de ponta a ponta** antes de publicar.

## 5. [VOCÊ] Guardar o `mapping.txt` de cada versão

Gerado em `app/build/outputs/mapping/release/mapping.txt`. É o que traduz uma
stack trace ofuscada da Play Console de volta para nomes reais. Faça upload dele
junto com o AAB (a Play Console aceita, ou o Gradle Play Publisher envia
sozinho) **e** guarde uma cópia por `versionCode` fora do repositório.

## 6. [VOCÊ] Ficha da Play Store

Os textos prontos (em pt-BR) e as respostas do formulário de segurança de dados
estão em `play-store/`:

- `play-store/ficha-loja.md` — título, descrição curta e completa, categoria,
  e a lista de imagens que você precisa produzir (ícone 512×512, banner
  1024×500, screenshots).
- `play-store/data-safety.md` — respostas do formulário **Data safety**, campo a
  campo, com a justificativa de cada uma.
- `play-store/politica-de-privacidade.md` — rascunho da política de
  privacidade. A Play exige uma **URL pública**; publique este texto em algum
  lugar (GitHub Pages serve) e cole a URL na Console.

O que só você pode fazer: criar a conta de desenvolvedor, preencher a
classificação de conteúdo (questionário), declarar público-alvo, e produzir as
imagens.

## 7. Checklist antes de submeter

- [ ] `./gradlew testDebugUnitTest` verde
- [ ] `./gradlew connectedDebugAndroidTest` verde **em aparelho físico**, não só
      emulador (`planning.md` §9 pede isso explicitamente)
- [ ] `./gradlew assembleRelease` e o APK instalado e navegado por completo
- [ ] `versionCode` incrementado em `app/build.gradle.kts`
- [ ] `targetSdk` no nível que a Play exige hoje (§3)
- [ ] `keystore.properties` preenchido e `bundleRelease` assinado
- [ ] `mapping.txt` guardado
- [ ] Política de privacidade publicada e a URL colada na Console
- [ ] Formulário de segurança de dados preenchido conforme `play-store/data-safety.md`
- [ ] Teste com uma chave de IA real de pelo menos um provedor (nunca foi feito
      até aqui — ver "AÇÃO MANUAL NECESSÁRIA" no relatório da Fase 6)
