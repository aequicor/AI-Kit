# Plan: M4 — Экспорт PDF
**id:** `2026-05-12-m4-pdf-export`
**created:** 2026-05-12
**milestone:** M4 — Flatten аннотаций в PDF
**depends-on:** `2026-05-12-m3-tool-palette`

## Goal
«Вжечь» векторные аннотации в PDF (flatten) и сохранить копию. Платформы: JVM Desktop (PDFBox), Android (iText Android / PDFBox Android), iOS (PDFKit), Web (pdf-lib.js).

## Steps

### Step 1 — `expect`/`actual` PdfExporter в `shared`
**Commit:** `feat(shared): PdfExporter expect/actual skeleton`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/PdfExporter.kt`
  ```kotlin
  expect class PdfExporter {
      suspend fun export(
          sourcePath: String,
          layer: AnnotationLayer,
          outputPath: String
      ): Result<Unit>
  }
  ```
- Stubs для всех platform source sets

---

### Step 2 — Stroke → PDF vector renderer (общий)
**Commit:** `feat(shared/domain): StrokeToPdfConverter — converts strokes to platform-agnostic draw commands`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/PdfDrawCommand.kt`
  — sealed class: `MoveTo(x,y)`, `LineTo(x,y)`, `CubicTo(...)`, `SetColor(argb)`, `SetWidth(dp)`, `SetAlpha(f)`, `SetBlendMode(mode)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/StrokeToPdfConverter.kt`
  — `fun convert(stroke: Stroke, pageHeightPt: Float): List<PdfDrawCommand>` — пересчёт из экранных координат (px) в PDF-точки (pt), Catmull-Rom → Bézier команды

---

### Step 3 — JVM actual: Apache PDFBox
**Commit:** `feat(shared/jvm): PdfExporter via Apache PDFBox`

File: `shared/src/jvmMain/kotlin/io/aequicor/pdf/PdfExporter.jvm.kt`

- `PDDocument.load(sourcePath)` → для каждой страницы загружает `AnnotationLayer`
- `PDPageContentStream(APPEND)` → транслирует `PdfDrawCommand` в PDFBox `drawLine`/`curveTo`/`setStrokingColor`/`setLineWidth`
- Сохраняет в `outputPath`
- Запускается в `Dispatchers.IO`

---

### Step 4 — Android actual: PDFBox Android port
**Commit:** `feat(shared/android): PdfExporter via PdfDocument + Canvas (Android 26+)`

Android approach: `android.graphics.pdf.PdfRenderer` не поддерживает запись. Вместо этого:
- Рендерим каждую страницу PDF в `Bitmap` через `PdfRenderer` (из M1)
- Рисуем штрихи поверх `Bitmap` через `Canvas` (то же, что экранный рендер)
- Собираем новый PDF через `android.graphics.pdf.PdfDocument` (API 19+, пишет растр)
- Сохраняем через `PdfDocument.writeTo(FileOutputStream)`

Limitation задокументирована в ADR: растровый экспорт на Android vs векторный на Desktop/iOS.

Catalog additions:
```toml
# уже есть от M1 — android.graphics.pdf.* встроен
```

---

### Step 5 — iOS actual: PDFKit + CoreGraphics
**Commit:** `feat(shared/ios): PdfExporter via PDFKit + UIGraphicsPDFRenderer`

File: `shared/src/iosMain/kotlin/io/aequicor/pdf/PdfExporter.ios.kt`

- `PDFDocument(url:)` → для каждой `PDFPage`
- `UIGraphicsPDFRenderer` рисует поверх PDF-страницы через CoreGraphics:
  - `CGContextSetStrokeColorWithColor`, `CGContextAddCurve`, `CGContextStrokePath`
  - Прозрачность через `CGContextSetAlpha`
- Результат — новый `PDFDocument`, сохраняется в `outputPath`

---

### Step 6 — JS/wasmJs actual: pdf-lib.js
**Commit:** `feat(shared/js): PdfExporter via pdf-lib (JS interop)`

File: `shared/src/jsMain/kotlin/io/aequicor/pdf/PdfExporter.js.kt`

- `@JsModule("pdf-lib")` обёртка над `PDFDocument.load()`, `page.drawLine()`, `PDFDocument.save()`
- `StrokeToPdfConverter` → pdf-lib draw calls
- Сохранение: `download()` через `URL.createObjectURL(Blob)` в браузере
- npm dep добавляется в `webpack.config.js` / `package.json`: `"pdf-lib": "^1.17.1"`

---

### Step 7 — ExportViewModel + UI
**Commit:** `feat(composeApp): ExportViewModel + export dialog UI`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/presentation/ExportViewModel.kt`
  — `suspend fun exportCurrent(): Result<String>` — вызывает `PdfExporter`, возвращает путь
  — `StateFlow<ExportState>` (Idle / InProgress / Success / Error)
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/ExportButton.kt`
  — кнопка в топбаре «Экспорт» → показывает `LinearProgressIndicator` → `Snackbar` с результатом
- `expect fun saveFilePicker(defaultName: String, onResult: (path: String?) -> Unit): () -> Unit`
  — `actual` для каждой платформы (SAF / JFileChooser / UIDocumentPickerViewController / download)

---

### Step 8 — Unit-тесты
**Commit:** `test(shared): StrokeToPdfConverter + ExportViewModel tests`

- `StrokeToPdfConverterTest` — координатное преобразование, Bézier команды для 3+ точек
- `ExportViewModelTest` — Success/Error state transitions (mock PdfExporter)

---

### Step 9 — Docs
**Commit:** `docs: ADR-004 export strategy + README M4 status`

- `docs/adr/ADR-004-pdf-export.md` — растровый vs векторный экспорт, почему Android растровый, будущий путь к iText/PDFBox-Android для векторного
- `README.md` — M4 секция

## Definition of Done (M4)
- [ ] Desktop: экспортированный PDF открывается в сторонней читалке, аннотации видны и векторные
- [ ] Android: экспортированный PDF содержит аннотации (растровые — документировано)
- [ ] iOS: компилируется, экспорт функционирует
- [ ] Web: скачивается PDF с аннотациями
- [ ] Тесты зелёные

## Open questions
1. Нужен ли векторный экспорт на Android уже в M4 (требует iText Android ~2MB AAR), или растровый достаточен?
2. DPI растрового экспорта на Android: 300 DPI (print quality) или 144 DPI (screen)?
