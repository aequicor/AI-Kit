> **AI-Kit v3 testing sandbox.** This subdirectory is a Compose Multiplatform
> template that lives inside the AI-Kit repository so the kit can be regenerated
> and exercised end-to-end without a second repo. It is **not** an independent
> git repository — `kit-setup generate` writes here, and `/kit-do` git commits
> originating from this directory land in the parent AI-Kit history.
> Generated kit artifacts (`CLAUDE.md`, `.claude/`, `.aikit/manifest.yaml`,
> `.aikit/plans/`) are gitignored to keep the sandbox state opt-in.

This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

* [/shared](./shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

## M1 — PDF Viewer (implemented)

### What's in M1

- **Domain models** (`shared/commonMain`): `PdfDocumentId`, `PdfPage`, `PdfDocument`, `ViewportState` (scale range 25–800 %).
- **`PdfRenderer` expect/actual** (`shared`): opens documents, renders pages as `PdfPageImage` (raw ARGB/BGRA byte buffer), closes documents.
  - Android → `android.graphics.pdf.PdfRenderer` (API 21+, zero extra deps)
  - Desktop/JVM → Apache PDFBox 3.x (`Loader.loadPDF`)
  - iOS → PDFKit via K/N cinterop (`platform.PDFKit`)
  - JS/wasmJs → stub (`NotImplementedError`) — PDF.js deferred to M1-web
- **`FilePicker` expect/actual** (`composeApp`): SAF on Android, `JFileChooser` on Desktop, `UIDocumentPickerViewController` on iOS, stub on web.
- **`PdfViewerScreen`** (`composeApp/commonMain`): `LazyColumn` with virtualized pages, pinch-to-zoom + drag (`rememberTransformableState`), LRU bitmap cache (5 pages), FAB to open a file.
- **Koin DI**: `PdfRenderer` registered as `single` in `pdfModule`; `initKoin()` called from all platform entry points.
- **Tests**: `ViewportStateTest`, `PdfDocumentTest` (commonTest); `PdfRendererJvmTest` integration test (jvmTest, generates a PDF in-memory via PDFBox).
- **ADR-001**: library choices documented in [`docs/adr/ADR-001-pdf-rendering.md`](./docs/adr/ADR-001-pdf-rendering.md).

### Smoke-test instructions

**Desktop (JVM):**
```shell
.\gradlew.bat :composeApp:run          # Windows
./gradlew    :composeApp:run           # macOS/Linux
```
Click the **Open** FAB, pick a PDF, scroll and pinch-zoom (trackpad two-finger pinch or Ctrl+scroll).

**Android:**
```shell
.\gradlew.bat :composeApp:assembleDebug
```
Install the APK, tap **Open**, select a PDF from Files.

**iOS:** Open `/iosApp` in Xcode → `Product > Build` → run on simulator or device.

**Unit + JVM integration tests:**
```shell
.\gradlew.bat :shared:jvmTest          # Windows
./gradlew    :shared:jvmTest           # macOS/Linux
```

### Known limitations (M1)

- JS/wasmJs: PDF rendering not yet implemented (throws `NotImplementedError`). PDF.js integration is tracked as M1-web.
- Desktop Ctrl+scroll zoom: transformable gesture (pinch) works; dedicated scroll-wheel zoom handler deferred to M2.
- Bitmap cache is in-memory only; evicted on process restart.
- iOS file picker uses deprecated `documentTypes: ["com.adobe.pdf"]` UTI — will migrate to `UTType.pdf` (iOS 14+) in M2.

## M2 — Stylus Input & Annotations (implemented)

### What's in M2

- **Domain models** (`shared/commonMain`): `StrokePoint`, `StrokeId`, `Stroke`, `DrawingTool` (Brush/Eraser), `AnnotationLayer`.
- **Local persistence** via SQLDelight 2.3.2 + `kotlinx-serialization-json`:
  - `annotation` table: inline `strokesJson TEXT` per `(documentId, pageIndex)`.
  - `AnnotationRepository` interface + `AnnotationRepositoryImpl`.
  - `DatabaseDriverFactory` expect/actual: Android → `AndroidSqliteDriver`, JVM → `JdbcSqliteDriver`, iOS → `NativeSqliteDriver`, JS → stub.
- **Stylus input** (`composeApp`): `expect fun Modifier.stylusInput(onEvent)` per platform.
  - Android: raw `MotionEvent` via `nativeEvent`; `AXIS_TILT`+`AXIS_ORIENTATION` for real tilt; palm rejection (drops `TOOL_TYPE_FINGER` while stylus is active).
  - JVM Desktop: `PointerEventType` + `change.pressure`; touch events skipped.
  - iOS: accepts Apple Pencil (`PointerType.Stylus`) and touch; pressure from Compose API.
  - JS: stub in `webMain`.
- **Stroke rendering** (`composeApp/commonMain`):
  - `catmullRomPath()` — Catmull-Rom smoothing via cubic Bézier approximation.
  - `DrawScope.drawStroke()` — pressure-scaled width, `BlendMode.SrcOver` (Brush) / `BlendMode.Clear` (Eraser).
  - `PdfAnnotationOverlay` — two-layer Canvas: committed strokes (redraws on layer change) + live stroke (redraws per event).
- **`DrawingViewModel`** (`shared/commonMain`): `ViewModel` with undo/redo via Command pattern (`ArrayDeque`, max 50 entries); saves via injected `CoroutineDispatcher`.
- **`PdfViewerScreen` updated**: toolbar (Brush / Eraser toggle, Undo, Redo); `DrawablePdfPage` wraps each page with `PdfAnnotationOverlay + stylusInput`.
- **Koin DI** extended: `CoroutineDispatcher` (expect/actual `ioDispatcher`), `Database`, `AnnotationRepository`, `DrawingViewModel` via `viewModel {}`.
- **Tests**: `StrokeTest` (5), `DrawingViewModelTest` (8, with `UnconfinedTestDispatcher`), `StrokeSmoothingTest` (6 smoke tests) — all in commonTest.
- **ADR-002**: stroke storage rationale in [`docs/adr/ADR-002-stroke-storage.md`](./docs/adr/ADR-002-stroke-storage.md).

### Smoke-test instructions (M2)

**Desktop (JVM):**
```shell
.\gradlew.bat :composeApp:run          # Windows
./gradlew    :composeApp:run           # macOS/Linux
```
Open a PDF → draw with mouse (or graphics tablet) → undo/redo with toolbar buttons → reopen app and verify strokes persist.

**Android:**
```shell
.\gradlew.bat :composeApp:assembleDebug
```
Install APK, open a PDF, draw with stylus — palm rejection active. Verify undo/redo. Reopen app; strokes should reload.

**iOS:** Open `/iosApp` in Xcode → run on device with Apple Pencil. Verify Pencil and finger both draw; undo/redo functional.

**Unit tests:**
```shell
.\gradlew.bat :shared:jvmTest          # Windows
./gradlew    :shared:jvmTest           # macOS/Linux
```

### Known limitations (M2)

- **Multi-page annotation:** only the active drawing page shows its saved strokes; switching pages loses the previous page's in-memory layer. Full multi-page map is tracked for M3.
- **Timestamp:** `StrokePoint.timestamp` is always `0L` — velocity-based thickness deferred to M3.
- **JS/wasmJs:** SQLDelight has no wasmJs artifacts; web target is a compile-only stub.
- **iOS tilt:** Apple Pencil tilt not exposed via standard Compose API; `tiltX/Y` are `0f` on iOS.
- **Undo stack limit:** capped at 50 strokes; oldest entry evicted silently.

---

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
- for the Wasm target (faster, modern browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
    ```
- for the JS target (slower, supports older browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:jsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
    ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).