# Plan: M2 — Рукописный ввод
**id:** `2026-05-12-m2-stylus-input`
**created:** 2026-05-12
**milestone:** M2 — Рукописный ввод
**depends-on:** `2026-05-12-m1-pdf-viewer`

## Goal
Кисть с pressure/tilt sensitivity, palm rejection, undo/redo, векторное хранение штрихов локально (SQLDelight). Порядок: Android + Desktop → iOS → Web stub.

## Steps

### Step 1 — Доменные модели штрихов в `shared/commonMain`
**Commit:** `feat(shared/domain): stroke domain models — StrokePoint, Stroke, DrawingTool`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/StrokePoint.kt`
  — `data class StrokePoint(val x: Float, val y: Float, val pressure: Float, val tiltX: Float, val tiltY: Float, val timestamp: Long)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/DrawingTool.kt`
  — `sealed class DrawingTool { data class Brush(val widthDp: Float, val color: Long) : DrawingTool() }`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/Stroke.kt`
  — `data class Stroke(val id: StrokeId, val tool: DrawingTool, val points: List<StrokePoint>)`
  — `@JvmInline value class StrokeId(val value: String)`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/AnnotationLayer.kt`
  — `data class AnnotationLayer(val documentId: PdfDocumentId, val pageIndex: Int, val strokes: List<Stroke>)`

---

### Step 2 — SQLDelight schema + repository interface
**Commit:** `feat(shared/data): SQLDelight schema for documents + annotations`

Files:
- `shared/src/commonMain/sqldelight/io/aequicor/pdf/data/Document.sq` — таблица документов (id, path, name, lastOpened)
- `shared/src/commonMain/sqldelight/io/aequicor/pdf/data/Annotation.sq` — таблица метаданных аннотаций (documentId, pageIndex, strokeCount, filePath)
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/repository/AnnotationRepository.kt`
  — `interface AnnotationRepository { suspend fun getLayer(docId, page): AnnotationLayer; suspend fun saveLayer(layer: AnnotationLayer) }`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/AnnotationRepositoryImpl.kt` — реализация поверх SQLDelight + JSON-файлов для точек штрихов

Catalog additions:
```toml
[versions]
sqldelight = "2.0.2"

[libraries]
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-web-worker-driver = { module = "app.cash.sqldelight:web-worker-driver", version.ref = "sqldelight" }

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

---

### Step 3 — Расширить Koin модуль из M1 + SQLDelight drivers per platform
**Commit:** `feat(shared): extend pdfModule with AnnotationRepository + SQLDelight drivers`

> Koin уже инициализирован в M1 Step 7b. Здесь только расширяем `pdfModule`.

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/di/PdfModule.kt` (modify) — добавить `single<AnnotationRepository> { AnnotationRepositoryImpl(get(), get()) }` + `single<Database> { Database(get()) }`
- `shared/src/commonMain/kotlin/io/aequicor/pdf/data/DatabaseDriverFactory.kt` — `expect class DatabaseDriverFactory { fun create(): SqlDriver }`
- `shared/src/androidMain/.../DatabaseDriverFactory.android.kt` — `AndroidSqliteDriver(Database.Schema, context, "pdfkit.db")`
- `shared/src/jvmMain/.../DatabaseDriverFactory.jvm.kt` — `JdbcSqliteDriver("jdbc:sqlite:pdfkit.db")`
- `shared/src/iosMain/.../DatabaseDriverFactory.ios.kt` — `NativeSqliteDriver(Database.Schema, "pdfkit.db")`
- `shared/src/jsMain/.../DatabaseDriverFactory.js.kt` + `wasmJsMain` — `WebWorkerDriver(Worker(...))`

---

### Step 4 — `expect`/`actual` StylusInputSource + Android actual
**Commit:** `feat(composeApp/android): stylus input — pressure, tilt, palm rejection`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/input/StylusEvent.kt`
  — `data class StylusEvent(val x: Float, val y: Float, val pressure: Float, val tiltX: Float, val tiltY: Float, val type: EventType)` где `EventType = DOWN | MOVE | UP`
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/input/StylusInputHandler.kt`
  — `expect fun Modifier.stylusInput(onEvent: (StylusEvent) -> Unit): Modifier`
- `composeApp/src/androidMain/kotlin/io/aequicor/pdf/ui/input/StylusInputHandler.android.kt`
  — `actual` через `Modifier.pointerInput(Unit) { awaitPointerEventScope { ... } }` + доступ к raw `MotionEvent` через `PointerInputChange.toMotionEventOrNull()` (Compose 1.6+ API).
  — Поля: `TOOL_TYPE_STYLUS`, `getPressure()`, `getAxisValue(AXIS_TILT)`, `getOrientation()`
  — Palm rejection: игнорировать `TOOL_TYPE_FINGER` когда в текущем gesture stream есть `TOOL_TYPE_STYLUS`
  — Альтернатива `pointerInteropFilter` рассмотрена и отклонена (помечен `@ExperimentalComposeUiApi`, упаковывает в Android-specific API внутри Compose layer)
- `composeApp/src/jvmMain/kotlin/io/aequicor/pdf/ui/input/StylusInputHandler.jvm.kt`
  — `actual` через `Modifier.pointerInput` + `PointerEventType`, `PointerEvent.pressure`, `PointerEvent.toolType`
- `composeApp/src/iosMain/kotlin/io/aequicor/pdf/ui/input/StylusInputHandler.ios.kt`
  — `actual` через Compose `Modifier.pointerInput`, Apple Pencil через `UITouch.type == .stylus` interop
- `composeApp/src/jsMain/kotlin/io/aequicor/pdf/ui/input/StylusInputHandler.js.kt`
  — `actual` через Pointer Events API: `pointerType == "pen"`, `pressure`, `tiltX/Y`
- `composeApp/src/wasmJsMain/…` — аналогично js

---

### Step 5 — Stroke rendering + Catmull-Rom smoothing
**Commit:** `feat(composeApp): stroke Canvas rendering with Catmull-Rom smoothing`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/stroke/StrokeSmoothing.kt`
  — `fun catmullRomPath(points: List<StrokePoint>): Path` — Catmull-Rom через Bézier-аппроксимацию; минимум 2 точки
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/stroke/StrokeRenderer.kt`
  — `fun DrawScope.drawStroke(stroke: Stroke)` — рисует `Path` с переменной шириной по `pressure`; `BlendMode.SrcOver` для кисти
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/PdfAnnotationOverlay.kt`
  — `@Composable fun PdfAnnotationOverlay(layer: AnnotationLayer, activeStroke: List<StrokePoint>?)`
  — два слоя Canvas: нижний — сохранённые штрихи (перерисовка только по `layer` change), верхний — текущий активный штрих (предиктивный overlay, перерисовка на каждое событие)

---

### Step 6 — DrawingViewModel + undo/redo
**Commit:** `feat(shared/presentation): DrawingViewModel — undo/redo stack`

Files:
- `shared/src/commonMain/kotlin/io/aequicor/pdf/presentation/DrawingViewModel.kt`
  — `class DrawingViewModel(private val repo: AnnotationRepository) : ViewModel()`
  — `StateFlow<AnnotationLayer>`, `StateFlow<List<StrokePoint>>` (текущий штрих)
  — `fun beginStroke(tool)`, `fun addPoint(event: StylusEvent)`, `fun endStroke()` — записывают в undo stack
  — `fun undo()`, `fun redo()` — работают через `ArrayDeque<Command>` (Command pattern)
  — сохранение вызывает `repo.saveLayer()` в `Dispatchers.IO`

---

### Step 7 — Интеграция в `PdfViewerScreen`
**Commit:** `feat(composeApp): integrate drawing overlay into PdfViewerScreen`

- `PdfViewerScreen` получает `DrawingViewModel` через Koin
- Поверх каждой `PdfPage` рендерится `PdfAnnotationOverlay`
- Инструментальная панель (toolbar): только кисть + ластик (полная палитра — в M3)
- `Modifier.stylusInput` навешивается на overlay, события направляются в `DrawingViewModel`
- Кнопки undo/redo в топбаре

---

### Step 8 — Unit-тесты
**Commit:** `test(shared): StrokeSmoothing + DrawingViewModel unit tests`

Files:
- `shared/src/commonTest/kotlin/io/aequicor/pdf/domain/StrokeTest.kt` — модели
- `shared/src/commonTest/kotlin/io/aequicor/pdf/presentation/DrawingViewModelTest.kt` — undo/redo, endStroke сохраняет в repo (mock)
- `shared/src/commonTest/kotlin/io/aequicor/pdf/ui/stroke/StrokeSmoothingTest.kt` — catmullRomPath для 2, 3, 10 точек

---

### Step 9 — Docs
**Commit:** `docs: ADR-002 stroke storage format + README M2 status`

- `docs/adr/ADR-002-stroke-storage.md` — обоснование JSON vs Protobuf (JSON выбран для M2 как простой, Protobuf можно добавить позже), SQLDelight для индекса
- `README.md` — M2 секция

## Definition of Done (M2)
- [ ] `./gradlew :shared:jvmTest` — все тесты зелёные
- [ ] Android: рисование стилусом/пальцем, palm rejection работает, undo/redo
- [ ] Desktop: рисование мышью/планшетом, undo/redo
- [ ] iOS: компилируется, Pencil input функционирует
- [ ] JS/wasmJs: компилируется (stub)
- [ ] Smoke-test: Android + Desktop (два устройства)

## Open questions
1. Нужен ли velocity → thickness (скорость → толщина) уже в M2, или только pressure/tilt?
2. Максимальный размер undo-стека — 50 штрихов или неограниченный?
