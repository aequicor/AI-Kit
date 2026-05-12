# Plan · m1-pdf-viewer

**Created:** 2026-05-11  
**Session 1 by:** simulated prогон через Claude Sonnet 4.6 (AI-Kit v3.0.0 test)  
**Task:** M1 — Каркас и просмотр PDF (из тест-задания: PDF-редактор с аннотациями)

---

## Context snapshot

```
Read:
- build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
- composeApp/build.gradle.kts
- shared/build.gradle.kts
- .aikit/manifest.yaml, CLAUDE.md

Key findings:
- Kotlin 2.3.21, CMP 1.10.3, AGP 8.11.2, Ktor 3.4.3, Material3 1.10.0-alpha05 (alpha!)
- Модули: :composeApp (android/ios/jvm/js/wasmJs), :shared (android/ios/jvm/js/wasmJs), :server (Ktor JVM)
- minSdk 24 в libs.versions.toml (ТЗ требует 26 — откладываем на M8 или уточняем с владельцем)
- shared/commonMain.dependencies пуст — нет Koin, SQLDelight, kotlinx-serialization, Kermit
- Нет expect/actual PDF-классов, нет PDF-библиотек в TOML
- -Xexpect-actual-classes не включён в shared/build.gradle.kts (нужен для Kotlin 2.x)
- composeApp:webMain — единый source set для js+wasmJs
```

---

## Steps (6 total)

### Step 1 — Foundation: зависимости + expect/actual флаг

**Goal:** добавить в `libs.versions.toml` PDF-библиотеки для JVM (PDFBox), включить `-Xexpect-actual-classes` в `shared/build.gradle.kts`, добавить Koin + kotlinx.serialization + kotlinx-datetime заглушками для дальнейших шагов.

**DoD:** `./gradlew :shared:compileKotlinJvm` зелёный, `./gradlew :shared:compileCommonMainKotlinMetadata` без beta-warning по KT-61573.

**Assumptions:**
- PDFBox 3.x поддерживает Kotlin JVM без отдельного Android-биндинга; Android использует `android.graphics.pdf.PdfRenderer` напрямую
- Для wasmJs используем PDF.js через `@JsExport` wrapper; для js — аналогично

---

### Step 2 — Domain models в shared:commonMain

**Goal:** `expect class PdfDocument`, `expect class PdfPageRenderer`, доменные типы `DocumentRef`, `PageRef(docId, pageIndex)`, `PdfPageBitmap(width, height, pixels)` в `shared/src/commonMain`.

**DoD:** `./gradlew :shared:compileCommonMainKotlinMetadata` зелёный; `PageRefTest` расширен до 3 test-cases покрывающих eq/hash/toString.

**Assumptions:**
- `PdfPageBitmap` хранит `IntArray` пикселей — cross-platform representation без platform bitmap types
- `expect class PdfDocument` имеет `openOrNull(path: String): PdfDocument?` + `pageCount: Int` + `close()`

---

### Step 3 — JVM actual (PDFBox) + Compose Desktop viewer

**Goal:** `actual class PdfDocument`, `actual class PdfPageRenderer` в `shared/src/jvmMain` через PDFBox; `PdfViewerScreen` в `composeApp/src/jvmMain` с базовым скроллом страниц.

**DoD:** `./gradlew :composeApp:run` запускает приложение; открытие `sample.pdf` из текущей директории рендерит первую страницу.

**Assumptions:**
- PDFBox рендерит в `BufferedImage` → конвертируем в `IntArray` → `ImageBitmap` для Compose
- Desktop viewer — простой `LazyColumn` со страницами без zoom на этом шаге (zoom — Step 6)

---

### Step 4 — Android actual (android.graphics.pdf.PdfRenderer)

**Goal:** `actual class PdfDocument`, `actual class PdfPageRenderer` в `shared/src/androidMain`; `PdfViewerScreen` в `composeApp/src/androidMain`.

**DoD:** `./gradlew :composeApp:assembleDebug` зелёный; визуальный smoke-test на эмуляторе API 24+ — первая страница отображается.

**Assumptions:**
- `android.graphics.pdf.PdfRenderer` доступен с API 21; minSdk=24 достаточен
- PDF открывается через `FileDescriptor` из `ContentResolver` — файл-пикер добавляем на M3

---

### Step 5 — iOS actual (PDFKit cinterop stub)

**Goal:** `actual class PdfDocument`, `actual class PdfPageRenderer` в `shared/src/iosMain` через PDFKit cinterop; iOS app отображает первую страницу.

**DoD:** `./gradlew :shared:compileKotlinIosArm64` зелёный; Xcode-симулятор (iosSimulatorArm64) показывает первую страницу.

**Assumptions:**
- PDFKit доступен в iOS 11+; cinterop через `cinterops { val PdfKit { defFile = ... } }` в shared/build.gradle.kts
- Если cinterop не настраивается без Mac-агента — stub `actual class` с `TODO("iOS cinterop: requires Xcode build")` + явная ассумпция в DoD

---

### Step 6 — Zoom + виртуализация + общий viewer-composable

**Goal:** `ZoomState` (pinch-to-zoom, Ctrl+wheel) в `shared:commonMain`; `VirtualizedPdfViewer` с видимые±1 страница буфером; jump-to-page; интеграция во все таргеты.

**DoD:** На JVM Desktop: 50-страничный PDF плавно скроллится (визуальная проверка), `ZoomControllerTest` проходит в `commonTest`.

**Assumptions:**
- Zoom реализуется через `TransformableState` в Compose; платформенная специфика only для Desktop Ctrl+wheel
- Web (wasmJs) — PDF.js placeholder в этом шаге остаётся stub, полная имплементация откладывается

---

## Key assumptions (сводка)

- minSdk остаётся 24 для M1; поднять до 26 (по требованию ТЗ) — отдельный шаг перед M8
- PDFKit cinterop для iOS требует Mac-хоста; без него Step 5 завершается stub
- Material3 alpha05 — может потребовать обходных решений на M8; приемлемо для M1–M7
- Web (wasmJs/js) PDF.js — полная интеграция откладывается до M4 при экспорте

## Out of scope (deferred)

- M2: рукописный ввод, давление, palm rejection, undo/redo
- M3: палитра инструментов, миниатюры, поиск по тексту
- M4: flatten-экспорт в PDF
- M5–M7: сервер, mDNS, WebSocket-синхронизация, режим проекции
- M8: производительность, accessibility, релизные сборки, minSdk=26
