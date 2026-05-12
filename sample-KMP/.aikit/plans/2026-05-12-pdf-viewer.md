# Plan · pdf-viewer

**Task:** Современный PDF Viewer для KMP/Compose Multiplatform.
Адаптивная верстка под разные экраны, zoom, pan, несколько режимов просмотра,
корректный рендеринг на Desktop (primary) и Android. iOS — заглушка. Web — заглушка.

**Created:** 2026-05-12
**Plan id:** 2026-05-12-pdf-viewer

---

## Invariants

1. `no navigation library added` — переключение экранов управляется sealed-class state внутри App.kt; никаких Decompose/Voyager/androidx.navigation.
2. `minSdk 24 maintained` — никаких Android API выше 24 без явного `@RequiresApi`-guard и SDK-check.
3. `JS and wasmJs targets compile` — все `expect/actual` имеют stub-реализации для jsMain/wasmJsMain; таргеты не ломаются.
4. `Apache 2.0 dependencies only` — Apache PDFBox (Apache 2.0); никаких GPL/LGPL/проприетарных библиотек.
5. `existing tests still pass` — тестовые заглушки в composeApp и shared остаются зелёными.

---

## Steps

### Step 1 — Dependency additions

**Goal:** Добавить `kotlinx-coroutines-core` в commonMain и Apache PDFBox 3.x в jvmMain; обновить `libs.versions.toml`.

**DoD:** `./gradlew :composeApp:jvmJar` завершается без ошибок компиляции; PDFBox-классы доступны в desktopMain.

**Review:** light

**What would be wrong:** (n/a — light)

**Verify:** [compile]

**Expect:** green

**Shape:**
- files-glob: `gradle/libs.versions.toml,composeApp/build.gradle.kts`
- max-diff-lines: 35
- no-test-changes: true

**Assumptions:**
- Apache PDFBox 3.0.3 — последний стабильный релиз, доступен в Maven Central.
- `kotlinx-coroutines-core` версия совместима с Kotlin 2.3.21 (используется `1.10.2`, уже есть в `libs.versions.toml` для swing-варианта).

---

### Step 2 — PdfDocument expect/actual

**Goal:** Объявить `expect class PdfDocument` в commonMain с операциями `companion fun open(bytes: ByteArray): PdfDocument`, `val pageCount: Int`, `fun renderPage(pageIndex: Int, scale: Float): ImageBitmap`, `fun close()`. Реализовать actual для: desktopMain (PDFBox), androidMain (android.graphics.pdf.PdfRenderer), iosMain (stub — UnsupportedOperationException), jsMain и wasmJsMain (stub).

**DoD:** `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinAndroid` — оба зелёные; stub-таргеты тоже компилируются.

**Review:** standard

**What would be wrong:** androidMain actual использует API выше minSdk 24 без `@RequiresApi`; или desktopMain держит `PDDocument` открытым без try-with-resources / явного `close()`.

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- `ImageBitmap` из `androidx.compose.ui.graphics` доступен во всех Compose-таргетах.
- Android `PdfRenderer` принимает `ParcelFileDescriptor`; передача через временный файл допустима для первой итерации.

---

### Step 3 — PDF file loading & viewer state

**Goal:** `expect fun launchPdfPicker(onResult: (ByteArray?) -> Unit)` на каждой платформе (desktopMain — JFileChooser; androidMain — `ActivityResultContracts.OpenDocument`; ios/js/wasmJs — stub). Sealed class `PdfViewerState { Idle, Loading, Loaded(doc, currentPage), Error(message) }`. Composable `rememberPdfViewerState()` управляет переходами и вызывает `launchPdfPicker`.

**DoD:** На Desktop можно нажать кнопку "Open PDF", выбрать файл, документ открывается в состоянии `Loaded`; ошибка при битом файле переходит в `Error`.

**Review:** standard

**What would be wrong:** Android-реализация picker-а не обрабатывает `null` URI (пользователь нажал Back); или ByteArray читается на main thread без `withContext(Dispatchers.IO)`.

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- Android Composable получает доступ к `LocalContext` и `rememberLauncherForActivityResult` — стандартный Compose pattern; никаких новых зависимостей.
- JFileChooser на Desktop запускается через `withContext(Dispatchers.Main)` (Swing EDT).

---

### Step 4 — Single-page viewer with zoom / pan

**Goal:** Composable `PdfPageCanvas(page: ImageBitmap, modifier: Modifier)` отображает страницу через `Image` внутри `Box`. `rememberTransformableState` обеспечивает pinch-zoom (0.5x–5x) и pan; трансформации применяются через `graphicsLayer`. Async-рендеринг страниц через `LaunchedEffect` + `Dispatchers.Default` с per-page LRU-кешем на 5 страниц. Навигационный тулбар: кнопки ←/→, поле ввода номера страницы, счётчик `N / total`.

**DoD:** На Desktop: открыть многостраничный PDF, zoom pinch/колесо мыши работает, drag перемещает страницу, кнопки навигации меняют страницу без зависания UI.

**Review:** standard

**What would be wrong:** Рендеринг страниц происходит на main thread (PDFBox — CPU-bound), блокируя Compose frame loop; или масштаб не сбрасывается при переходе на другую страницу.

**Verify:** [compile, test]

**Expect:** green

**Assumptions:**
- Колесо мыши на Desktop обрабатывается через `Modifier.pointerInput` + `PointerEventType.Scroll` — доступно в Compose for Desktop.
- LRU-кеш реализуется как `LinkedHashMap` в `remember`; нет дополнительных зависимостей.

---

### Step 5 — View modes

**Goal:** `enum class ViewMode { SinglePage, ContinuousScroll, TwoPage }`. `ContinuousScrollViewer`: `LazyColumn`, каждый item рендерит страницу лениво по `key(pageIndex)`. `TwoPageViewer` (только Desktop/wide): `Row` из двух `PdfPageCanvas`. Toolbar получает `ViewMode`-toggle (SegmentedButton или Icon-кнопки). Текущий `pageIndex` синхронизируется при переключении режимов.

**DoD:** Переключение между тремя режимами сохраняет номер страницы; `ContinuousScroll` не рендерит все страницы сразу (только видимые + ±1).

**Review:** standard

**What would be wrong:** `ContinuousScrollViewer` передаёт в `LazyColumn` `items(pageCount) { renderPage(it) }` без `key` — Compose потеряет item identity и будет перерендеривать все страницы при скролле.

**Verify:** [compile, test]

**Expect:** green

**Assumptions:**
- `TwoPage` показывает чётную + нечётную страницу; первая страница — одна (обложка). Нет требований к книжному разворачиванию.

---

### Step 6 — Adaptive layout, thumbnail panel, App.kt integration

**Goal:** Responsive layout: при ширине ≥ 900dp — постоянная левая панель миниатюр (120dp, `LazyColumn` с маленькими `PdfPageCanvas` scale 0.15); при ширине < 900dp — fullscreen viewer + FAB для открытия файла. Панель миниатюр рендерит страницы лениво по видимости. Заменить placeholder в `App.kt` на `PdfViewerScreen`; экран Idle показывает drag-and-drop зону (Desktop) + кнопку "Open file".

**DoD:** На Desktop при окне > 900dp видна боковая панель миниатюр; при сужении — скрывается. Drag-and-drop PDF файла на Desktop открывает документ.

**Review:** standard

**What would be wrong:** Thumbnail-панель рендерит все миниатюры при открытии документа (100+ страниц → OOM), вместо lazy render по видимости в `LazyColumn`.

**Verify:** [compile, test]

**Expect:** green

**Assumptions:**
- Drag-and-drop через `Modifier.onExternalDrag` (Compose for Desktop API, доступно с CMP 1.6+; версия проекта 1.10.3 — OK).
- `BoxWithConstraints` или `WindowSizeClass` для определения breakpoint 900dp.

---

## Out of plan (deferred)

- Текстовый поиск внутри PDF
- Аннотирование / разметка
- iOS полноценная реализация (PDFKit)
- Web (JS/wasmJs) через PDF.js
- URL-источники / сеть
- Печать документа
