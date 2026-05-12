# Plan: M1 — PDF Viewer Scaffold
**id:** `2026-05-12-m1-pdf-viewer`
**created:** 2026-05-12
**milestone:** M1 — Каркас и просмотр PDF

## Goal
Реализовать открытие, рендеринг, зум (25–800%) и скролл PDF-файлов на всех таргетах (`composeApp`: Android, Desktop/JVM, iOS, JS/wasmJs). Никакого рукописного ввода — только просмотр.

## Context
- Проект: `sample-KMP` (`composeApp` + `shared` + `server`), пакет `io.aequicor`
- Kotlin 2.3.21, Compose Multiplatform 1.10.3, Ktor 3.4.3
- `shared` имеет source sets: `commonMain`, `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`
- `composeApp` имеет те же source sets плюс UI entry points

## Steps

### Step 1 — Domain models in `shared/commonMain`
**Commit:** `feat(shared/domain): PDF domain models — PdfDocumentId, PdfPage, PdfDocument, ViewportState`

Files to create:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/PdfDocumentId.kt`
  — `@JvmInline value class PdfDocumentId(val value: String)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/PdfPage.kt`
  — `data class PdfPage(val index: Int, val widthPx: Int, val heightPx: Int)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/PdfDocument.kt`
  — `data class PdfDocument(val id: PdfDocumentId, val pageCount: Int, val pages: List<PdfPage>)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/ViewportState.kt`
  — `data class ViewportState(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f, val currentPage: Int = 0)`
  — constants `MIN_SCALE = 0.25f`, `MAX_SCALE = 8f`

No platform dependencies. Pure Kotlin.

---

### Step 2 — `expect`/`actual` PdfRenderer abstraction + dependency catalog entries
**Commit:** `feat(shared): PdfRenderer expect/actual skeleton + catalog deps`

Files to create/modify:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/PdfPageImage.kt`
  — `data class PdfPageImage(val widthPx: Int, val heightPx: Int, val pixels: ByteArray)` — platform-agnostic ARGB_8888 буфер (row-major, 4 байта на пиксель). Конвертация в `ImageBitmap` — в `composeApp`.
- `shared/src/commonMain/kotlin/io/aequicor/pdf/PdfRenderer.kt`
  ```kotlin
  expect class PdfRenderer {
      fun openDocument(path: String): PdfDocument
      fun renderPage(docId: PdfDocumentId, pageIndex: Int, widthPx: Int, heightPx: Int): PdfPageImage
      fun closeDocument(docId: PdfDocumentId)
  }
  ```
  > Решение: возвращаем `PdfPageImage` (pure Kotlin), а не `ImageBitmap`. Это сохраняет `shared` свободным от Compose UI зависимости и соблюдает требование "domain = pure Kotlin". Конвертация `PdfPageImage → ImageBitmap` через `Bitmap.createBitmap(...).asImageBitmap()` (Android), `Image.makeRaster(...).toComposeImageBitmap()` (Desktop/iOS через Skiko), `ImageBitmap` через Canvas API (JS).
- `shared/src/androidMain/kotlin/io/aequicor/pdf/PdfRenderer.android.kt` — `actual class PdfRenderer { /* TODO step 3 */ }`
- `shared/src/jvmMain/kotlin/io/aequicor/pdf/PdfRenderer.jvm.kt` — `actual class PdfRenderer { /* TODO step 4 */ }`
- `shared/src/iosMain/kotlin/io/aequicor/pdf/PdfRenderer.ios.kt` — `actual class PdfRenderer { /* TODO step 5 */ }`
- `shared/src/jsMain/kotlin/io/aequicor/pdf/PdfRenderer.js.kt` — stub actual
- `shared/src/wasmJsMain/kotlin/io/aequicor/pdf/PdfRenderer.wasmJs.kt` — stub actual

Catalog additions (`gradle/libs.versions.toml`):
```toml
[versions]
pdfbox = "3.0.3"

[libraries]
pdfbox = { module = "org.apache.pdfbox:pdfbox", version.ref = "pdfbox" }
```

`shared/build.gradle.kts` — add `pdfbox` to `jvmMain.dependencies`.

> `ImageBitmap` = `androidx.compose.ui.graphics.ImageBitmap` (Compose Multiplatform — доступен в commonMain через compose.ui).

---

### Step 3 — Android actual: `android.graphics.pdf.PdfRenderer`
**Commit:** `feat(shared/android): PdfRenderer via android.graphics.pdf.PdfRenderer`

File: `shared/src/androidMain/kotlin/io/aequicor/pdf/PdfRenderer.android.kt`

Implementation:
- `openDocument` → `android.graphics.pdf.PdfRenderer(ParcelFileDescriptor.open(File(path), READ_ONLY))`, build `PdfDocument` from page count + page width/height
- `renderPage` → `renderer.openPage(pageIndex)`, render to `Bitmap(ARGB_8888)`, скопировать пиксели в `IntArray` → упаковать в `ByteArray` → `PdfPageImage`
- `closeDocument` → close the native renderer
- Thread safety: `@GuardedBy` внутри, вызовы с `Dispatchers.IO`

No new dependencies (API 26+ covers `android.graphics.pdf.PdfRenderer`).

---

### Step 4 — JVM (Desktop) actual: Apache PDFBox
**Commit:** `feat(shared/jvm): PdfRenderer via Apache PDFBox`

File: `shared/src/jvmMain/kotlin/io/aequicor/pdf/PdfRenderer.jvm.kt`

Implementation:
- `openDocument` → `PDDocument.load(File(path))`, extract page count, page sizes via `PDPage.mediaBox`
- `renderPage` → `PDFRenderer(pdDocument).renderImageWithDPI(pageIndex, dpi, ImageType.ARGB)` → `BufferedImage` → `getRGB(0,0,w,h,IntArray,0,w)` → `ByteArray` → `PdfPageImage`
- DPI calculation: `dpi = LocalDensity.current.density * 72f` передаётся из UI слоя (M1 Step 8 пробрасывает density через параметр)
- `closeDocument` → `pdDocument.close()`

Dependency: `libs.pdfbox` (already added to catalog in step 2).

---

### Step 5 — iOS actual: PDFKit via cinterop
**Commit:** `feat(shared/ios): PdfRenderer via PDFKit cinterop`

Files:
- `shared/src/iosMain/kotlin/io/aequicor/pdf/PdfRenderer.ios.kt`

Implementation:
- `openDocument` → `PDFDocument(url: NSURL.fileURLWithPath(path))`, iterate pages for sizes
- `renderPage` → `PDFPage.thumbnail(of: CGSize, for: .mediaBox)` → `UIImage` → `CGImage` → `CFData` pixel buffer → `PdfPageImage`
- PDFKit is part of iOS SDK — no extra cinterop file needed, it's available via `platform.PDFKit.*`

Note: `platform.PDFKit` is available in Kotlin/Native iOS targets out of the box — no custom `.def` file required.

---

### Step 6 — JS + wasmJs stubs (PDF.js deferred)
**Commit:** `feat(shared/js-wasmJs): PdfRenderer stubs — PDF.js integration deferred`

Files:
- `shared/src/jsMain/kotlin/io/aequicor/pdf/PdfRenderer.js.kt`
- `shared/src/wasmJsMain/kotlin/io/aequicor/pdf/PdfRenderer.wasmJs.kt`

Both throw `NotImplementedError("PDF.js integration: use web file input + Canvas, see M1-web ADR")`. This unblocks compilation on all targets while PDF.js wiring is left to a dedicated sub-step.

---

### Step 7 — FilePicker `expect`/`actual` in `composeApp`
**Commit:** `feat(composeApp): FilePicker expect/actual for all platforms`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/FilePicker.kt`
  — `expect fun rememberFilePicker(onResult: (path: String?) -> Unit): () -> Unit`
- `composeApp/src/androidMain/kotlin/io/aequicor/pdf/ui/FilePicker.android.kt`
  — Storage Access Framework: `ActivityResultContracts.OpenDocument`, filter `application/pdf`
- `composeApp/src/jvmMain/kotlin/io/aequicor/pdf/ui/FilePicker.jvm.kt`
  — `JFileChooser` на background thread, result via callback
- `composeApp/src/iosMain/kotlin/io/aequicor/pdf/ui/FilePicker.ios.kt`
  — `UIDocumentPickerViewController` через SwiftUI interop (`ComposeUIViewController` wrapper)
- `composeApp/src/jsMain/kotlin/io/aequicor/pdf/ui/FilePicker.js.kt`
  — `<input type="file" accept=".pdf">` via JS interop, return `URL.createObjectURL`
- `composeApp/src/wasmJsMain/kotlin/io/aequicor/pdf/ui/FilePicker.wasmJs.kt`
  — same pattern via `kotlinx-browser`

---

### Step 7b — Минимальный Koin bootstrap
**Commit:** `feat(shared): minimal Koin setup — PdfRenderer in DI`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/di/PdfModule.kt`
  — `val pdfModule = module { single { PdfRenderer() } }`
- `shared/src/commonMain/kotlin/io/aequicor/AppDi.kt`
  — `fun initKoin(vararg platformModules: Module) = startKoin { modules(pdfModule, *platformModules) }`
- Каждая platform entry point вызывает `initKoin()` (Android `Application.onCreate`, JVM `main`, iOS `MainViewController`, JS/wasmJs `main`)

Catalog additions:
```toml
koin-core = { module = "io.insert-koin:koin-core", version = "4.0.0" }
koin-compose = { module = "io.insert-koin:koin-compose", version = "4.0.0" }
```

Цель: зафиксировать DI на старте, чтобы M2+ не пришлось рефакторить direct instantiation. `PdfPageImage → ImageBitmap` конвертер также живёт в DI как `single<PdfImageDecoder>` (имплементация в `composeApp` per platform).

---

### Step 8 — `PdfViewerScreen` in `composeApp/commonMain`
**Commit:** `feat(composeApp): PdfViewerScreen — virtualized pages + zoom/pan`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/PdfViewerScreen.kt`

Key design:
- `LazyColumn` with items for each `PdfPage`, only visible ± 1 pages rendered (virtualization via `LazyListState.firstVisibleItemIndex`)
- `AsyncPdfPageImage` composable: renders page bitmap on `Dispatchers.Default`, shows placeholder while loading, caches last 5 pages via `remember` with `LruCache`
- Zoom + pan via `rememberTransformableState` + `Modifier.transformable` (pinch-to-zoom, drag), clamped to `[0.25f, 8f]`
- Desktop: `Modifier.pointerInput(Unit) { detectTransformGestures }` with Ctrl+scroll for zoom
- `FloatingActionButton` for file open (calls `filePicker()`)
- Jump-to-page: `LazyListState.animateScrollToItem(page)`
- Wire into `App.kt`: replace existing `Greeting` button with `PdfViewerScreen()`

---

### Step 9 — Unit tests for domain models and renderer contract
**Commit:** `test(shared): domain model unit tests + PdfRenderer contract tests`

Files:
- `shared/src/commonTest/kotlin/io/aequicor/pdf/domain/ViewportStateTest.kt`
  — scale clamp, offset arithmetic
- `shared/src/commonTest/kotlin/io/aequicor/pdf/domain/PdfDocumentTest.kt`
  — construction, page lookup
- `shared/src/jvmTest/kotlin/io/aequicor/pdf/PdfRendererJvmTest.kt`
  — integration test with bundled sample PDF, asserts page count, non-null bitmap, correct dimensions

Test resource: `shared/src/jvmTest/resources/sample.pdf` — minimal 1-page PDF (generated programmatically in test setup via PDFBox itself).

---

### Step 10 — ADR-001 + README M1 section
**Commit:** `docs: ADR-001 PDF rendering library choices + README M1 status`

Files:
- `docs/adr/ADR-001-pdf-rendering.md` — records library choices per platform and rationale
- `README.md` — add M1 section: what's implemented, smoke-test instructions, known limitations (JS/wasmJs PDF.js deferred)

**ADR-001 rationale summary:**
| Platform | Library | Reason |
|----------|---------|--------|
| Android | `android.graphics.pdf.PdfRenderer` (API 26+) | Built-in, zero dep, minSdk 26 matches |
| JVM Desktop | Apache PDFBox 3.x | Pure Java, no native deps, permissive license (Apache 2.0), renders text sharply |
| iOS | PDFKit (system framework) | Zero-cost, best quality on Apple hardware, available via `platform.PDFKit` in K/N |
| JS/wasmJs | PDF.js (stub for M1) | Only option for browser; deferred because requires JS interop scaffolding |

**Rejected alternatives:**
- Pdfium on Android: better quality but requires native `.so` redistribution, adds APK size; revisit if quality is insufficient
- PDF.js via JCEF on Desktop: JCEF adds ~100 MB, PDFBox is simpler
- iText/PdfBox on iOS: not available for K/N, PDFKit is the only viable option

## Definition of Done (M1)
- [ ] `./gradlew :shared:jvmTest` passes (domain + renderer tests green)
- [ ] `./gradlew :composeApp:assembleDebug` builds without warnings in `shared`
- [ ] `./gradlew :composeApp:jvmJar` runs desktop app, PDF opens, zoom/scroll works
- [ ] iOS build compiles (Xcode `Product > Build`) — actual PDF rendering functional
- [ ] `./gradlew :composeApp:jsBrowserDevelopmentRun` compiles (stub throws, no crash on load)
- [ ] Smoke-test report: Android device + Desktop (two platforms minimum)
- [ ] No regressions: existing `Greeting` test still passes

## Open questions (to resolve before or during execution)
1. **JS/wasmJs PDF.js**: should it be wired in M1 or deferred to M2? (current plan: stub in M1, real implementation в M1-web patch)
2. **iOS file picker**: SwiftUI `UIDocumentPickerViewController` interop — acceptable, or prefer Compose `rememberLauncherForActivityResult` equivalent on iOS?
3. **Bitmap cache size**: LRU of 5 pages hardcoded — OK for M1?
4. **DPI for Desktop rendering**: 144 DPI (2x) default, or derive from screen density?
