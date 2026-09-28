# Fase 7 · Clareza e confiança: entrega

Os itens 1 a 12 estão implementados no branch **`v2-clareza`** (ver `CLAUDE.md`, seção
"Fase 7", e `planning.md` §9). O que resta é a entrega e o teste do usuário.

## Estado

- Room na **versão 10** (`MIGRATION_9_10`: renda principal + dia combinado da dívida).
- 188 testes JVM verdes; instrumentados: ver o relatório da sessão de 28/09/2026.
- APK `pessoal` da fase inteira gerado e enviado ao usuário nessa data.

## Avisos para o usuário (repetir se ele perguntar)

1. **Depois de instalar o APK da Fase 7, não dá para voltar ao APK do `main` sem perder os
   dados**: o banco sobe para a versão 10 e o downgrade apaga tudo
   (`fallbackToDestructiveMigrationOnDowngrade`). Para garantir, ele pode usar
   "Exportar dados" antes de qualquer troca.
2. Instala por cima do app atual sem perder nada (o `versionCode` é maior).
3. Baixado pelo navegador do celular, o arquivo pode vir como `.apk.zip`: renomear tirando o
   `.zip`, sem extrair.
4. Na primeira abertura, quem já usava o app **não** vê o assistente nem as dicas de novo
   (o assistente só aparece em instalação nova ou depois de "Apagar todos os dados"). Para
   ver as dicas: Configurações → Ajuda → "Rever tour guiado".

## Depois do teste

- Anotar o retorno dele em `CLAUDE.md` e resolver o que aparecer.
- Merge de `v2-clareza` no `main` feito em 28/09/2026 com o aval dele (APK v2, versionCode 42).

## Como testar neste ambiente

- JVM: `./gradlew testDebugUnitTest`. Instrumentados: `./gradlew connectedDebugAndroidTest`
  (desinstala o app do emulador ao terminar).
- Emulador: `~/AppData/Local/Android/Sdk/emulator/emulator.exe -avd Pixel_6_API_34
  -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect` em background; `adb` em
  `~/AppData/Local/Android/Sdk/platform-tools/adb.exe`; encerrar com `adb emu kill`.
- Screenshot: `adb emu screenrecord screenshot <arquivo.png>`. O emulador tem override
  1236×2676: toque pelo `uiautomator dump` (bounds), não pelas coordenadas da imagem.
- `adb push` no Git Bash precisa de `MSYS_NO_PATHCONV=1`.
- Tela pequena: `adb shell wm size 1024x2048` (≈ 320×640 dp na densidade 480), depois
  `wm size reset`.
