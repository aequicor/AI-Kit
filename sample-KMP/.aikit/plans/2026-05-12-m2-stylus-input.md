# M2 — Рукописный ввод

**Created:** 2026-05-12
**Branch:** feature/m2-stylus-input
**Source task:** M2 — кисть, давление, undo/redo, локальное сохранение штрихов.
Сначала Android + Desktop, затем iOS. Web исключён.

## Context (digest)

- Строится поверх M1: viewer уже работает, Koin подключён, domain/port слой в shared.
- Штрихи — векторные объекты: `{tool, color, points: [{x,y,pressure,tilt,t}], width}`.
- Хранение: SQLDelight (индекс документов) + JSON-файлы штрихов в app-specific storage.
- Сглаживание: Catmull-Rom поверх сырых точек; predictive-overlay отдельным слоем.
- Palm rejection: Android — фильтр `TOOL_TYPE_FINGER` когда активен стилус; iOS — `UITouch.type`.
- kotlinx.serialization нужна с M2 для сериализации Stroke в JSON.
- SQLDelight 2.x — KMP, работает на android/jvm/ios (через native driver).

## Invariants

- Stroke-domain (модели, port, use-cases) — чистый Kotlin в `shared/commonMain`.
- Сырые координаты штриха нормализованы в PDF-координатное пространство (не в экранные пиксели).
- Рендеринг overlay — только видимые страницы; не перерисовывать PDF при каждом event.
- Новые public API в `shared` покрыты unit-тестами в `commonTest`.
- `server/` не изменяется.

## Steps

### Step 1 — stroke-domain
- **Goal:** `shared/domain/model/` — `Stroke`, `StrokePoint`, `StrokeTool` (BRUSH/MARKER/ERASER);
  `shared/domain/port/` — `StrokeRepository` (loadStrokes, saveStroke, deleteStroke, clearPage);
  `shared/domain/usecase/` — `AddStrokeUseCase`, `UndoLastStrokeUseCase`;
  `commonTest` — unit-тесты use-cases с fake repository.
- **DoD:** `./gradlew.bat :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** `StrokePoint` хранит экранные координаты вместо PDF-normalized (0..1 относительно размера страницы) — при зуме штрихи съедут.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :shared:compileKotlinJvm"`
- **Expect:** green

### Step 2 — deps-sqldelight-serialization
- **Goal:** добавить в `libs.versions.toml`: SQLDelight 2.x (plugin + runtime), kotlinx-serialization;
  в `shared/build.gradle.kts` — plugin, commonMain deps; в `composeApp` — platform drivers
  (androidDriver, sqliteDriver/jvm, nativeDriver/ios). Добавить kotlinx-serialization plugin.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :shared:jvmTest` exit 0.
- **Review:** heavy
- **What would be wrong:** SQLDelight native driver для iOS тянет Kotlin/Native linkage; конфликт
  если driver version не совпадает с SQLDelight runtime version.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :shared:jvmTest"`
- **Expect:** green
- **Shape:**
    - **files-glob:** `**/{libs.versions.toml,build.gradle.kts,settings.gradle.kts}`
    - **max-diff-lines:** 80
    - **no-test-changes:** true

### Step 3 — stroke-storage
- **Goal:** SQLDelight schema `PdfDocument.sq` (documents table); JSON-файл штрихов через
  `StrokeJsonStore` (kotlinx.serialization); `StrokeRepositoryImpl` в `shared/data/`;
  `DatabaseDriverFactory` expect/actual для android/jvm/ios; Koin module update.
- **DoD:** `./gradlew.bat :shared:generateCommonMainDatabaseInterface :shared:jvmTest` зелёный.
- **Review:** standard
- **What would be wrong:** `DatabaseDriverFactory` — actual для ios объявлен в `iosMain` shared, но
  SQLDelight native driver требует `NativeSqliteDriver` — не путать с jvm-driver.
- **Verify:** `shell: "./gradlew.bat :shared:generateCommonMainDatabaseInterface :shared:jvmTest"`
- **Expect:** green

### Step 4 — android-stylus-input
- **Goal:** `AndroidStylusInputHandler` в `composeApp/androidMain`: перехват `PointerInputChange`
  через `Modifier.pointerInteropFilter` + raw MotionEvent, извлечение `pressure`, `orientation`,
  `tiltX/Y`; palm rejection (фильтр `TOOL_TYPE_FINGER` при активном `TOOL_TYPE_STYLUS`);
  исторические точки батча (`getHistoricalX/Y/Pressure`); emit в `StrokeEventFlow`.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug` exit 0.
- **Review:** standard
- **What would be wrong:** batched historical points не включаются — теряем точки между frame'ами,
  штрих выглядит дёрганым; palm rejection срабатывает только в первом event батча.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug"`
- **Expect:** green

### Step 5 — desktop-stylus-input
- **Goal:** `JvmStylusInputHandler` в `composeApp/jvmMain` через Compose `PointerInputScope`
  с `PointerEventType.Press/Move/Release` и `PointerInputChange.pressure`; fallback на mouse
  (pressure=1.0f) для мышей без давления; tilt через AWT `MouseEvent` extra axis если доступен.
- **DoD:** `./gradlew.bat :composeApp:compileKotlinJvm` exit 0.
- **Review:** standard
- **What would be wrong:** `pressure` из Compose pointer events всегда 0.0 на некоторых Windows
  драйверах — нужен fallback to 1.0 чтобы штрих не был невидимым.
- **Verify:** `shell: "./gradlew.bat :composeApp:compileKotlinJvm"`
- **Expect:** green

### Step 6 — ios-stylus-input
- **Goal:** `IosStylusInputHandler` в `composeApp/iosMain` через Compose `Modifier.pointerInput`;
  Apple Pencil detection через `PointerType` (Compose 1.7+); palm rejection — игнорировать
  `PointerType.Touch` когда `PointerType.Stylus` активен; force (pressure) и azimuth из
  `PointerInputChange`.
- **DoD:** Assumption: requires macOS.
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` exit 0.
- **Review:** standard
- **What would be wrong:** Compose MP pointer type для Apple Pencil может не различать Stylus/Touch
  в версии 1.10.x — потребуется UIKit interop через `UITouch.type` как fallback.
- **Verify:** `shell: "./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64"`
- **Expect:** green
- **Assumptions:** macOS host required. Проверить совместимость `PointerType.Stylus` с CMP 1.10.3.

### Step 7 — stroke-rendering-overlay
- **Goal:** `StrokeOverlayCanvas` — отдельный Composable поверх `PdfPageView`; рендеринг
  сохранённых штрихов через `Canvas.drawPath` + Catmull-Rom сплайн; predictive preview —
  последний in-progress штрих в отдельном `Canvas` overlay чтобы не инвалидировать весь слой;
  нормализация координат из PDF-space в экранные px с учётом текущего зума.
- **DoD:** `./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm` exit 0.
- **Review:** standard
- **What would be wrong:** при каждом PointerEvent вызывается `invalidate()` всего composable-дерева
  включая PDF-рендер → 60fps не достигается; Catmull-Rom вычисляется не инкрементально.
- **Verify:** `shell: "./gradlew.bat :composeApp:assembleDebug :composeApp:compileKotlinJvm"`
- **Expect:** green

### Step 8 — undo-redo
- **Goal:** `UndoRedoStack<Command>` в `shared/domain/` (max 50 steps); `AddStrokeCommand`,
  `DeleteStrokeCommand`; `UndoRedoViewModel` с `canUndo`/`canRedo` StateFlow;
  кнопки Undo/Redo в toolbar; persistence undo-stack — нет (только in-memory, сброс при
  закрытии документа); unit-тесты для stack-логики.
- **DoD:** `./gradlew.bat :shared:jvmTest :composeApp:assembleDebug` зелёный.
- **Review:** standard
- **What would be wrong:** undo-stack держит сильные ссылки на Stroke objects → heap bloat при
  длинных сессиях; stack не ограничен по размеру.
- **Verify:** `shell: "./gradlew.bat :shared:jvmTest :composeApp:assembleDebug"`
- **Expect:** green

## Out of scope

- Маркер, ластик, палитра цветов (M3).
- Flatten/экспорт (M4).
- Синхронизация штрихов по сети (M6).
- PencilKit как альтернатива (M8, опционально).
