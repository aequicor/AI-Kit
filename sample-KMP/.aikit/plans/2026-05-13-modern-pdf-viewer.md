# Plan · modern-pdf-viewer · 2026-05-13

## Task

Современный PDF viewer для локальных файлов. Desktop-first (JVM + PDFBox), вторичная поддержка Android (PdfRenderer) и iOS (PDFKit stub).
Фичи: открытие файла, постраничная навигация, непрерывный скролл, zoom, thumbnail-sidebar, полнотекстовый поиск с подсветкой, TOC/закладки, тёмная тема, keyboard shortcuts.

## Invariants

1. Только `androidx.compose.material3` — никаких импортов из `androidx.compose.material` без суффикса `3`
2. `minSdk = 24` и `compileSdk = 36` не меняются
3. Модуль `:server` не затрагивается ни в одном шаге
4. Весь общий UI и бизнес-код — в `commonMain`; платформо-специфичный — строго через `expect/actual`
5. Модуль `:shared` не затрагивается

## Steps

### Step 1 — PDF abstraction layer + dependencies

**Goal:** Добавить PDFBox в jvmMain, создать `expect/actual PdfDocument` и `FilePicker` для jvm/android/ios

**DoD:** `./gradlew :composeApp:compileKotlinJvm` и `./gradlew :composeApp:compileDebugKotlin` завершаются без ошибок; все `expect` имеют `actual` реализации

**Review:** standard

**What would be wrong:** реализация PDFBox-класса протекает в `commonMain` (нарушение `expect/actual`-границы); или `PdfDocument` захватывает ресурсы без `Closeable`/`close()`

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- PDFBox 3.x доступен в Maven Central как `org.apache.pdfbox:pdfbox`
- `iosMain` получает stub-`actual`, возвращающий пустые данные (реальный PDFKit binding — за рамками MVP)
- `FilePicker` на Desktop — `javax.swing.JFileChooser` или Compose `FileDialog`

---

### Step 2 — PdfViewerScreen: открытие файла и рендеринг первой страницы

**Goal:** Заменить starter-шаблон в `App.kt` на `PdfViewerScreen`; кнопка "Open PDF" → FilePicker → рендер первой страницы как `ImageBitmap` в Composable

**DoD:** На Desktop: нажать "Open PDF" → выбрать файл → страница отображается, масштабированная по ширине окна; `PdfViewerViewModel` держит состояние через `StateFlow`

**Review:** standard

**What would be wrong:** рендеринг страницы вызывается в Main thread, блокируя UI — должен выполняться в `viewModelScope` с `Dispatchers.Default`

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- `androidx.lifecycle.viewmodelCompose` из `commonDependencies` совместим с Desktop JVM в CMP 1.10.3
- На Android FilePicker реализован через `rememberLauncherForActivityResult` + `Intent.ACTION_OPEN_DOCUMENT`

---

### Step 3 — Постраничная навигация и непрерывный скролл

**Goal:** `LazyColumn` с рендером всех страниц (по одной, lazy); toolbar с кнопками prev/next, счётчиком (X / N), полем goto-page

**DoD:** Все страницы PDF прокручиваются вертикально; prev/next и goto-page переключают видимую страницу через `LazyListState.animateScrollToItem`

**Review:** standard

**What would be wrong:** рендеринг всех страниц сразу при открытии документа (не по видимости) — OOM на документах > 50 страниц; или `goto-page` не валидирует диапазон [1..total]

**Verify:** [compile]

**Expect:** green

---

### Step 4 — Zoom controls

**Goal:** Кнопки `+` / `−` / reset и toggle fit-to-width в toolbar; Android — `transformable` modifier для pinch-to-zoom

**DoD:** Масштаб изменяется в диапазоне [0.25f .. 4.0f]; fit-to-width подстраивает scale так, чтобы ширина страницы = ширине viewport; масштаб сохраняется при смене страниц

**Review:** standard

**What would be wrong:** zoom реализован через `scale` на `ImageBitmap` без пересчёта разрешения рендеринга — видна пикселизация на zoom > 1.0; или zoom-state сбрасывается при recomposition

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- Рендеринг PDFBox принимает целевые размеры в пикселях: при zoom умножаем базовый dpi на scale factor
- На Desktop pinch-to-zoom через трекпад передаётся как `onScrollEvent` — обрабатывается отдельно от кнопочного zoom

---

### Step 5 — Thumbnail sidebar

**Goal:** Сворачиваемая левая панель с `LazyColumn` thumbnail-изображений страниц; клик по thumbnail → прокрутка к странице; текущая страница подсвечена рамкой

**DoD:** Кнопка-иконка в toolbar открывает/закрывает sidebar; thumbnails рендерятся lazy при появлении в viewport; активная страница выделена цветом `MaterialTheme.colorScheme.primary`

**Review:** standard

**What would be wrong:** thumbnail-рендеринг стартует для всех страниц при открытии sidebar (не lazy) — lag на больших документах; или sidebar не анимирован (`AnimatedVisibility`)

**Verify:** [compile]

**Expect:** green

---

### Step 6 — Полнотекстовый поиск с highlight overlay

**Goal:** Поисковая строка (Ctrl+F / кнопка в toolbar); PDFBox извлекает текст + координаты слов; overlay-прямоугольники на совпадениях; next/prev match navigation

**DoD:** Ctrl+F открывает search bar; совпадения подсвечены жёлтым overlay на странице (Desktop, JVM PDFBox); кнопки ←→ листают совпадения; ESC закрывает; Android/iOS — список совпадений без визуального overlay

**Review:** heavy

**What would be wrong:** координаты из `PDFTextStripper` в PDF-пространстве (origin снизу-слева, Y инвертирован) не конвертируются в Compose-пространство (origin сверху-слева) — прямоугольники смещены; или поиск блокирует Main thread

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- PDFBox `PDFTextStripper` с `setSortByPosition(true)` предоставляет позиции через `TextPosition`
- Coordinate transform: `composeY = pageHeightPt - pdfY - wordHeightPt` (PDF → Compose)
- Android/iOS search возвращает список `(pageIndex, matchText)` без координат

---

### Step 7 — TOC / Закладки панель

**Goal:** Вторая вкладка в sidebar — дерево оглавления из PDF `PDOutlineNode`; клик → прокрутка к странице; placeholder "No bookmarks" если outline пуст

**DoD:** Документ с outline показывает дерево с отступами; клик по записи вызывает `LazyListState.scrollToItem(pageIndex)`; документ без outline показывает заглушку

**Review:** standard

**What would be wrong:** рекурсивный обход outline без ограничения глубины — `StackOverflowError` на патологических документах (depth cap = 10); или destination page не разрешается из `PDPageDestination`

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- PDFBox `PDDocumentOutline` + `PDOutlineItem` предоставляют bookmark tree
- iOS/Android — TOC-вкладка скрыта (показывается только на Desktop)

---

### Step 8 — Тёмная тема + UX polish

**Goal:** Переключатель light/dark темы в toolbar; Desktop keyboard shortcuts (←→ навигация, +− zoom, Ctrl+F поиск, F fullscreen); drag-and-drop PDF-файла на Desktop

**DoD:** Тема переключается без перезапуска приложения; F скрывает/показывает toolbar (fullscreen); DnD `onDragAndDropEvent` открывает PDF; shortcuts не срабатывают, если фокус в текстовом поле

**Review:** standard

**What would be wrong:** `KeyEvent` перехватывается на уровне Window без проверки `TextFieldFocusRequester` — shortcuts активируются при наборе текста в search/goto-page полях

**Verify:** [compile]

**Expect:** green

**Assumptions:**
- Desktop DnD через `Modifier.dragAndDropTarget` (CMP 1.7+, доступно в 1.10.3)
- Keyboard shortcuts через `onPreviewKeyEvent` на корневом `Box` с `focusRequester`; текстовые поля потребляют key events до всплытия
