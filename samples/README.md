# Faturas de exemplo (Fase 4 · importação)

Quatro arquivos com **o mesmo conteúdo** em formatos diferentes, para testar a
tela "Importar fatura" sem precisar usar uma fatura real:

| Arquivo | Caminho no app | O que exercita |
|---|---|---|
| `fatura-exemplo.csv` | Planilha | CSV com `;`, cabeçalho em pt-BR, valores `189,90` |
| `fatura-exemplo.xlsx` | Planilha | .xlsx real (ZIP de XML), lido sem Apache POI |
| `fatura-exemplo.pdf` | PDF | PDF digital → rasterizado pelo `PdfRenderer` → OCR do ML Kit |
| `fatura-exemplo.png` | Foto | OCR do ML Kit direto na imagem |

Os 6 lançamentos cobrem de propósito os casos difíceis:

1. `SUPERMERCADO EXTRA LOJA 2233` — classificado por regra local (Alimentação);
2. `NETFLIX.COM` — assinatura recorrente detectada localmente;
3. `MAGAZINE LUIZA PARC 03/10` — parcela, que sai da descrição e vira metadado;
4. `UBER *TRIP SAO PAULO` — prefixo de adquirente (Transporte);
5. `ESTORNO COMPRA DUPLICADA` — valor negativo (`89,90-`);
6. `ZZ COMERCIO 4477 SAO PAULO` — **ambíguo**: nenhuma regra local reconhece, é o
   único que vai para a IA (e, sem chave configurada, fica marcado como "a revisar").

## Como testar no aparelho

1. Copie os arquivos para o celular (USB, Drive, WhatsApp para si mesmo…).
2. No app: FAB `+` → **Importar fatura** → PDF / Planilha / Foto.
3. Importe **duas vezes** o mesmo arquivo para ver a detecção de duplicata: na
   segunda vez todos os 6 entram sinalizados e desmarcados.

No emulador: `adb push samples/fatura-exemplo.csv /sdcard/Download/`.

Estes mesmos arquivos são a entrada do teste instrumentado
`SampleStatementsInstrumentedTest` (`app/build.gradle.kts` aponta os assets de
`androidTest` para esta pasta), então eles não podem divergir do que o app
espera sem quebrar o build.
