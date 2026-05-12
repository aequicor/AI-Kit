# Plan: M3 — Палитра инструментов
**id:** `2026-05-12-m3-tool-palette`
**created:** 2026-05-12
**milestone:** M3 — Палитра инструментов
**depends-on:** `2026-05-12-m2-stylus-input`

## Goal
Полная палитра: маркер (полупрозрачный), объектный и пиксельный ластик, выбор цвета, выбор толщины, переключение страниц, миниатюры страниц.

## Steps

### Step 1 — Расширить `DrawingTool` + доменные модели
**Commit:** `feat(shared/domain): DrawingTool — Marker + ObjectEraser + PixelEraser`

- `DrawingTool.Marker(widthDp, color, alpha: Float = 0.5f)` — `BlendMode.Multiply` при рисовании
- `DrawingTool.ObjectEraser` — удаляет целые `Stroke`-объекты по hit-test
- `DrawingTool.PixelEraser(widthDp)` — рисует маской `BlendMode.Clear`
- `DrawingTool.Brush` получает `profiles: List<BrushProfile>` (calligraphy, felt, ballpoint)
- `data class BrushProfile(val name: String, val pressureCurve: FloatArray, val tiltCurve: FloatArray)`

---

### Step 2 — Логика ластика + future-compatible tombstone схема
**Commit:** `feat(shared): eraser logic + tombstones table (M6-ready schema)`

- Новая таблица в SQLDelight (вводится **сейчас**, чтобы M6 не ломал схему):
  ```sql
  -- shared/src/commonMain/sqldelight/io/aequicor/pdf/data/Tombstone.sq
  CREATE TABLE tombstones (
      stroke_id TEXT PRIMARY KEY,
      document_id TEXT NOT NULL,
      page_index INTEGER NOT NULL,
      logical_clock INTEGER NOT NULL DEFAULT 0,  -- 0 для локальных удалений в M3, M6 заполнит Lamport clock
      deleted_at INTEGER NOT NULL
  );
  ```
- `ObjectEraser`: при `endStroke` проверяет пересечение bbox → вставляет строку в `tombstones` с `logical_clock = 0`
- `Stroke` модель **не** получает поле `isDeleted` — удаление = наличие в `tombstones` таблице
- `AnnotationRepository.getLayer()` фильтрует штрихи с tombstone'ом (LEFT JOIN)
- `PixelEraser`: отдельный `Stroke` с `BlendMode.Clear` — вектор, не растр
- Undo: undo объектного ластика удаляет строки из `tombstones`; undo пиксельного удаляет Clear-stroke. Undo-стек хранит снимки операций.

> Архитектурное решение: tombstone-таблица вводится в M3, но используется локально. В M6 эта же таблица получает реальные Lamport-значения вместо `0`, и реплицируется по сети. Это избегает миграции схемы.

---

### Step 3 — Маркер рендеринг
**Commit:** `feat(composeApp): marker rendering — semi-transparent BlendMode.Multiply`

- В `StrokeRenderer.drawStroke()`: ветка `DrawingTool.Marker` использует `Paint.blendMode = BlendMode.Multiply` и `alpha = tool.alpha`
- Isolated `Canvas.saveLayer` чтобы перекрывающиеся штрихи одного маркера не темнели друг на друге в пределах одного штриха

---

### Step 4 — UI: ToolPalette composable
**Commit:** `feat(composeApp): ToolPalette composable — tool switcher, color picker, thickness`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/toolbar/ToolPalette.kt`
  — горизонтальный `Row` с иконками инструментов, активный подсвечен
  — `ColorPickerButton` → открывает `ColorPickerDialog` (HSV wheel + hex input)
  — `ThicknessSlider` → `Slider(value, range = 1f..30f)`
  — `BrushProfilePicker` → `DropdownMenu` с профилями кисти
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/toolbar/ColorPickerDialog.kt`
  — HSV color wheel через `Canvas` + hex `TextField`; результат — `Long` (ARGB)

---

### Step 5 — Миниатюры страниц + навигация
**Commit:** `feat(composeApp): page thumbnails sidebar + jump-to-page`

Files:
- `composeApp/src/commonMain/kotlin/io/aequicor/pdf/ui/PageThumbnailsPanel.kt`
  — боковая панель (collapsible): `LazyColumn` с `PdfPageThumbnail` — рендерит страницу при 150px ширине
  — клик → `LazyListState.animateScrollToItem(page)`
  — текущая страница подсвечена рамкой
- `shared/src/commonMain/kotlin/io/aequicor/pdf/domain/PageNavigationUseCase.kt`
  — `fun goToPage(index: Int)`, `fun nextPage()`, `fun prevPage()` + `StateFlow<Int>`
- Thumbnails кэшируются в `LruCache<Int, ImageBitmap>(20)` — отдельный кэш от основного рендера

---

### Step 6 — Переключатель страниц (page picker dialog)
**Commit:** `feat(composeApp): jump-to-page dialog`

- `PagePickerDialog`: `TextField` (числовой ввод) + `Slider`, кнопки ±1 страница
- Вызывается из топбара по иконке или `Ctrl+G` (desktop shortcut)

---

### Step 7 — Keyboard shortcuts (Desktop)
**Commit:** `feat(composeApp/jvm): keyboard shortcuts — undo/redo, tool switch, zoom`

- `Modifier.onKeyEvent` в root composable на JVM
- `Ctrl+Z` / `Ctrl+Y` → undo/redo
- `B` / `M` / `E` → Brush / Marker / Eraser
- `Ctrl+G` → page picker
- `[` / `]` → thickness –/+ 1dp

---

### Step 8 — Unit-тесты
**Commit:** `test(shared): eraser logic + tool selection tests`

- `ObjectEraserTest` — пересечение bbox, tombstone, undo восстанавливает
- `PixelEraserTest` — Clear-stroke добавляется, undo убирает
- `PageNavigationUseCaseTest` — boundary conditions (первая/последняя страница)

---

### Step 9 — Docs
**Commit:** `docs: ADR-003 eraser strategy + README M3 status`

- `docs/adr/ADR-003-eraser-strategy.md` — обоснование векторного PixelEraser vs растра
- `README.md` — M3 секция

## Definition of Done (M3)
- [ ] Все инструменты переключаются, настройки (цвет, толщина) сохраняются между страницами
- [ ] Объектный ластик удаляет штрихи, undo возвращает
- [ ] Маркер полупрозрачен, не темнеет внутри одного штриха
- [ ] Миниатюры загружаются, клик навигирует
- [ ] Keyboard shortcuts работают на Desktop
- [ ] Тесты зелёные
