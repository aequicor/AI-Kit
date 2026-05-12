# M1 — PDF Viewer Skeleton

**Created:** 2026-05-12
**Branch:** main
**Source task:** M1 из плана PDF-редактора — каркас и просмотр PDF: open/render/zoom/scroll на всех
таргетах (Android, Desktop/JVM, iOS). Web-таргеты исключены. File picker с M1. Koin с M1.

## Context (digest)

- Шаблон: один `composeApp` (sourcesets androidMain/jvmMain/iosMain), отдельные `shared` (KMP) и `server` (Ktor JVM-only).
- Kotlin 2.3.21, Compose MP 1.10.3, AGP 8.11.2, Ktor 3.4.3, coroutines 1.10.2.
- `shared/commonMain` пустой (заглушки Greeting/Platform); нет Koin, SQLDelight, serialization.
- `android.minSdk = 24` в шаблоне → поднимаем до 26.
- Web-таргеты (`js`, `wasmJs`) в `composeApp` и `shared` убираем полностью.
- PDF-стек по платформам: Android → `android.graphics.pdf.PdfRenderer` (API 21+, встроенный);
  Desktop → Apache PDFBox 3.x (JVM, mature, no native deps); iOS → PDFKit через cinterop (системный).
- Архитектура: domain/port в `shared/commonMain` — чистый Kotlin без платформенных типов;
  adapters в platform source-sets; ViewModel + UI в `composeApp`.
- iOS-сборка требует macOS-хоста; Steps на iOS помечены Assumption.

## Invariants

- `shared/commonMain` не импортирует platform-специфичных типов (android.*, java.*, compose.*).
- PDF-адаптеры живут только в platform source-sets; никакого platform-кода в `commonMain`.
- `shared` компилируется без warnings на androidTarget, jvm, iosArm64, iosSimulatorArm64.
- Новые public API в `shared/domain` покрыты unit-тестами в `commonTest`.
- `server/` не изменяется в M1.

## Steps

### Step 1 — config-baseline
- **Goal:** убрать js/wasmJs targets из `composeApp` и `shared`; поднять minSdk до 26; удалить
  placeholder-код (Greeting, Constants, старый Platform); упростить `App.kt` и `server/Application.kt`;
  добавить `.aikit/state/` в `.gitignore`.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :shared:compileKotlinJvm` — exit 0.
- **Review:** heavy
- **What would be wrong:** удаление web targets оставляет src/jsMain и src/wasmJsMain директории
  с Platform-файлами, которые при случайном включении target обратно попадут в сборку без actual-реализаций.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :shared:compileKotlinJvm"`
- **Expect:** green
- **Shape:**
    - **files-glob:** `**/{build.gradle.kts,settings.gradle.kts,libs.versions.toml,.gitignore,App.kt,Application.kt,Platform*.kt,Greeting.kt,Constants.kt,strings.xml}`
    - **max-diff-lines:** 120
    - **no-test-changes:** false

### Step 2 — domain-layer
- **Goal:** создать domain layer в `shared`:
  `domain/model/` — `PdfDocument`, `PdfPage`, `PdfPageSize`;
  `domain/port/` — `PdfRenderPort` (suspend openDocument/renderPage/closeDocument — ByteArray ARGB8888);
  `domain/usecase/` — `OpenDocumentUseCase`;
  `commonTest` — unit-тесты моделей и use-case с fake-port.
- **DoD:** `./gradlew.bat :shared:jvmTest` — exit 0, все тесты зелёные.
- **Review:** standard
- **What would be wrong:** `PdfRenderPort` принимает или возвращает android/jvm/compose-типы
  (ImageBitmap, Bitmap, File) — нарушение инварианта изоляции commonMain.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :shared:compileKotlinJvm"`
- **Expect:** green

### Step 3 — deps-koin-kermit
- **Goal:** добавить в `libs.versions.toml` + build.gradle.kts: Koin 4.x (koin-core, koin-compose,
  koin-compose-viewmodel), Kermit 2.x. В `shared/commonMain`: koin-core, kermit.
  В `composeApp/commonMain`: koin-compose, koin-compose-viewmodel, kermit.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :shared:jvmTest` — exit 0.
- **Review:** heavy
- **What would be wrong:** koin-compose-viewmodel версии, не совместимой с lifecycle-viewmodel-compose
  1.x → DuplicateClass или LinkageError на Android runtime.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :shared:jvmTest"`
- **Expect:** green
- **Shape:**
    - **files-glob:** `**/{libs.versions.toml,build.gradle.kts}`
    - **max-diff-lines:** 60
    - **no-test-changes:** true

### Step 4 — android-pdf-adapter
- **Goal:** реализовать `AndroidPdfRenderAdapter : PdfRenderPort` в `composeApp/androidMain`
  через `android.graphics.pdf.PdfRenderer`; file picker через
  `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`; открытие/рендеринг
  на `Dispatchers.IO`; закрыть PdfRenderer.Page сразу после рендеринга.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug` — exit 0.
- **Review:** standard
- **What would be wrong:** `PdfRenderer` или `PdfRenderer.Page` открываются в Main thread / UI
  coroutine scope → ANR на файлах > 5 МБ.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug"`
- **Expect:** green

### Step 5 — desktop-pdf-adapter
- **Goal:** реализовать `JvmPdfRenderAdapter : PdfRenderPort` в `composeApp/jvmMain`
  через Apache PDFBox 3.x (добавить jvmMain dependency); file picker через
  `javax.swing.JFileChooser` + `withContext(Dispatchers.IO)`.
  Добавить `pdfbox` в libs.versions.toml + jvmMain.dependencies.
- **DoD:** `./gradlew.bat :composeApp:compileKotlinJvm` — exit 0.
- **Review:** standard
- **What would be wrong:** `PDDocument` не закрывается в finally-блоке → file handle leak при
  повторном открытии; рендеринг PDFRenderer.renderImage вызывается на UI thread.
- **Verify:** `shell: "./gradlew.bat :composeApp:compileKotlinJvm"`
- **Expect:** green
- **Assumptions:** PDFBox 3.x в jvmMain.dependencies — только Desktop-таргет, не попадает в Android classpath.

### Step 6 — ios-pdf-adapter
- **Goal:** реализовать `IosPdfRenderAdapter : PdfRenderPort` в `composeApp/iosMain`
  через PDFKit (системный framework, доступен через Kotlin/Native cinterop без дополнительных
  interop-файлов — `platform.PDFKit.*`); document picker через `UIDocumentPickerViewController`
  wrapped в `ComposeUIViewController` callback.
- **DoD:** Assumption: requires macOS host.
  На macOS: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` — exit 0.
- **Review:** standard
- **What would be wrong:** `PDFPage.render(to:with:)` вызывается на Main thread Kotlin-side
  без `withContext(Dispatchers.Default)` → jank; CGContext не освобождается → memory leak.
- **Verify:** `shell: "./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64"`
- **Expect:** green
- **Assumptions:** шаг верифицируется только на macOS-хосте. На Windows — compile-check пропускается,
  шаг считается выполненным по code review.

### Step 7 — viewer-ui-vm
- **Goal:** создать `PdfViewerViewModel` (StateFlow<ViewerState>, openDocument, загрузка страниц
  LazyList-aware: рендерить только visible ± 1); `PdfViewerScreen` (LazyColumn of `PdfPageView`
  с Canvas + ImageBitmap, `Modifier.transformable` для pinch-to-zoom 25%–800%, кнопка «Открыть файл»
  вызывает platform file picker); Koin-модуль `pdfModule` с adapter-binding по платформе;
  заменить `App()` на `PdfViewerScreen`.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm` — exit 0;
  вручную: открыть PDF на Android и Desktop, скроллить и зумировать.
- **Review:** standard
- **What would be wrong:** рендеринг всех страниц PDF при открытии вместо lazy-по-мере-прокрутки
  → OOM на документах > 50 страниц; zoom state сбрасывается при recomposition из-за missing
  `remember { mutableStateOf() }`.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm"`
- **Expect:** green

## Out of scope

- Рукописный ввод, штрихи, аннотации (M2).
- Поиск по тексту, миниатюры, jump-to-page (M3).
- Flatten/экспорт PDF (M4).
- Сетевая синхронизация, mDNS, Ktor-клиент (M5–M7).
- Web-таргеты (исключены по решению).
- Замена `android.graphics.pdf.PdfRenderer` на Pdfium (M8, опционально).
- SQLDelight, kotlinx-serialization (M2+).
- Перестройка `composeApp` на 4 отдельных Gradle-модуля.
