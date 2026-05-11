# AI-Kit v3.0.0-rc.1 — отчёт о качественном тестировании на sample-KMP

**Дата:** 2026-05-11
**Тестировщик:** автоматизированный прогон через Claude Opus 4.7
**Ветка AI-kit:** `v3-prototype_test2`
**Версия бинаря:** `kit-setup 3.0.0-rc.1`
**Целевой проект:** `sample-KMP` (Compose Multiplatform: android / ios / jvm / wasmJs / `:server` Ktor)
**Тестовое задание (сценарий пользователя):** мультиплатформенный PDF-редактор с рукописными аннотациями, LAN-синхронизацией и режимом проекции (полный брифинг приведён в исходном промпте — здесь не повторяем).

---

## 1. Объём тестирования

| Слой | Что проверено |
|---|---|
| Бинарь `kit-setup` | `--help`, `--version`, `schema --format json|human`, `verify` (позитив + 4 негативных кейса), `generate` (позитив + негатив) |
| Сгенерированные артефакты | `CLAUDE.md`, `.claude/agents/Main.md`, `.claude/agents/Researcher.md`, `.claude/commands/{kit,kit-do,kit-fix}.md`, `.claude/skills/summary-format/SKILL.md`, `.claude/prompts/explore-module.md`, `.claude/settings.json` |
| Контракт `manifest.yaml` | поля `manifest_version`/`project`/`stack`/`modules`/`targets`/`providers`/`models`/`prompt_dialects`/`target_adapters`/`agents`/`policies` |
| Пайплайн v3 | прогон Session 1 (`/kit <test-task>`) — Stage 1 (Context) + Stage 2 (Plan) — реальная попытка следовать протоколу Main.md |
| Sessions 2/3 | статический анализ протокола (без фактического коммитирования, см. §3.1) |
| Совместимость с Windows | Git Bash + PowerShell-ранер; пути с обратными слешами / MSYS-конверсия |

---

## 2. Результаты по бинарю `kit-setup`

### 2.1 Позитивные кейсы

| Команда | Результат | Exit | Комментарий |
|---|---|---|---|
| `kit-setup --help` | человекочитаемый usage с SUBCOMMANDS / EXIT CODES | 0 | формат стабильный |
| `kit-setup --version` | `3.0.0-rc.1` | 0 | соответствует `kit-setup/build.gradle.kts` |
| `kit-setup schema --format human` | таблица: 2 агента, 5 адаптеров, 12 профилей, 4 enum'а | 0 | удобно для отладки |
| `kit-setup schema --format json` | machine-readable JSON | 0 | стабильный контракт для оркестратора SETUP_PROMPT |
| `kit-setup verify .aikit/manifest.yaml` | `{"valid":true,"errors":[]}` | 0 | манифест проходит |
| `kit-setup generate .aikit/manifest.yaml` | `{"ok":true,"generated":[9 файлов]}` | 0 | все 9 артефактов на месте |

Сгенерированные пути совпадают с заявленными в SETUP_PROMPT.md §3.4 и в `target_adapters/claude-code/adapter.yaml`.

### 2.2 Негативные кейсы

| Сценарий | Код ошибки | Exit | Документировано |
|---|---|---|---|
| `verify` несуществующий относительный путь | `manifest_not_found` | 2 | да (`CLAUDE.md` § "JSON output codes") |
| `verify` неполный манифест (нет `targets`/`providers`/`models`/`agents`/`project.slug`) | `missing_required_key`, `missing_project_slug` (5 ошибок одним списком) | 1 | да |
| `verify` malformed YAML (незакрытый flow mapping) | `parse_failed` с указанием строки и фрагмента | 2 | да |
| `verify` ссылка на неизвестный `native_provider` | `unresolvable_model` (агент `Main` не может разрешить модель) | 1 | да — но сообщение косвенное (см. §5.4 ниже) |
| `generate` на невалидном манифесте | те же 5 ошибок без выполнения генерации | 1 | да |

Все негативные кейсы дают **структурированный JSON** с полями `path`, `code`, `message`, `hint` (где применимо). Exit-коды чёткие: 0 — успех, 1 — невалидный манифест (включая `unresolvable_model`, `missing_*`), 2 — runtime/usage (`manifest_not_found`, `parse_failed`).

### 2.3 Замечания по бинарю

- **Корректная обработка путей через Git Bash на Windows.** При передаче `/does/not/exist.yaml` MSYS преобразует путь в `C:/Users/…/exist.yaml` — бинарь воспринимает преобразованный путь и возвращает `manifest_not_found`. Поведение детерминированное, но в `--help` не упомянуто.
- **JSON-ответ всегда на stdout, всегда одна строка.** `--help` это не объясняет — `STDOUT FORMAT` имеет смысл задокументировать.
- **Стабильность кодов.** Все коды из §2.2 — часть публичного агентского контракта (см. `CLAUDE.md` репозитория-родителя). Любое переименование = breaking change.

---

## 3. Прогон Session 1 (`/kit <test-task>`) — симуляция

### 3.1 Замечания по среде

⚠ **Критическое для тестового стенда:** `sample-KMP/` **не является самостоятельным git-репозиторием** (нет `.git/`, `git rev-parse --show-toplevel` возвращает родительский `AI-kit`). Все git-команды протокола v3 (`git commit`, `git log --grep`, `git reset --soft`, `git push --force-with-lease`) сработают над **родительским репозиторием AI-kit**, не над sample-KMP. В рамках теста я **намеренно не выполнял commit/push** — артефакт плана записан в `.aikit/plans/2026-05-11-m1-pdf-viewer.md` без коммита.

Это **не дефект v3-кита**, а свойство тестового сэмпла. Рекомендация — превратить sample-KMP в самостоятельный репозиторий (или явно задокументировать «test sample, не для боевого прогона»). Иначе любой реальный прогон `/kit-do` запишет шаги в историю AI-kit.

### 3.2 Stage 1 (Context) — формат CONTEXT SUMMARY

Я выполнил Stage 1 вручную, без диспатча `Researcher` subagent: subagent определён в `.claude/agents/Researcher.md`, но он привязан к Claude Code-сессии внутри sample-KMP, а тест запускается из родительского AI-kit. Вместо диспатча — селективный набор `Read` по корневым файлам сборки и source set'ам. Результирующий CONTEXT SUMMARY:

```
## CONTEXT SUMMARY · m1-pdf-viewer

**Read:**
- build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
- composeApp/build.gradle.kts, composeApp/src/{commonMain,jvmMain,webMain,androidMain,iosMain}/kotlin/io/aequicor
- shared/build.gradle.kts, shared/src/{commonMain,jvmMain,androidMain,iosMain,jsMain,wasmJsMain}/kotlin/io/aequicor
- server/build.gradle.kts, server/src/main/kotlin/io/aequicor/Application.kt
- README.md, .aikit/manifest.yaml, CLAUDE.md

**Key findings:**
- Compose Multiplatform 1.10.3, Kotlin 2.3.21, AGP 8.11.2, Material3 1.10.0-alpha05, Ktor 3.4.3.
- Модули: :composeApp (KMP, source set'ы android/ios/jvm/web), :shared (android/ios/js/jvm/wasmJs/common), :server (JVM Ktor). Отдельных androidApp/desktopApp/iosApp/webApp Gradle-модулей нет.
- shared/commonMain.dependencies пуст; в libs.versions.toml нет Koin, SQLDelight, kotlinx-serialization, Kermit, kotlinx-datetime.
- minSdk 24 (ТЗ требовал 26 — расхождение).
- composeApp/webMain — общий source set для js + wasmJs; в :shared они разделены.
- forbidden_patterns манифеста уже покрывают: blocking I/O в Compose, GlobalScope.launch, !!, platform-API в commonMain, Thread.sleep в suspend.

**Constraints discovered:**
- Material3 на alpha (1.10.0-alpha05) — потенциальный риск на горизонте M8.
- iOS-приложение собирается через сторонний Xcode-проект iosApp/iosApp.xcodeproj, не через Gradle-таргет.
- composeApp:webMain единый — придётся либо разделять на js/wasmJs source set'ы, либо делать платформенные actuals только в :shared.

**Out of scope (intentionally):**
- Server-модуль (Ktor) — M1 покрывает только Compose-таргеты, сервер вступает с M5.
- Реализация PencilKit/Stylus API — M2.
- Изучение PDF.js workers, шиммы CORS — поднимем при подходе к Step 5.

Reply `ok` to proceed to plan, or correct context with: "<adjustment>"
```

**Самопроверка по формату:** все 5 секций (`Read`, `Key findings`, `Constraints discovered`, `Out of scope`, реплика-приглашение) присутствуют, нет нарратива, факты со ссылками на платформы. Формат **рабочий**.

### 3.3 Stage 2 (Plan) — PLAN SUMMARY

После предполагаемого `ok` я выполнил Stage 2 и записал план в `.aikit/plans/2026-05-11-m1-pdf-viewer.md` (6 шагов, каждый runnable + bounded + committable, DoD на одну строку, ассумпции явные). PLAN SUMMARY:

```
## PLAN SUMMARY · m1-pdf-viewer · plan `2026-05-11-m1-pdf-viewer`

Saved to: .aikit/plans/2026-05-11-m1-pdf-viewer.md

**Steps (6 total):**
1. shared-каркас и зависимости — `./gradlew :shared:compileKotlinMetadata` зелёный + PageRefTest проходит
2. JVM actual через PDFBox + Compose Desktop вьюер — `./gradlew :composeApp:run` открывает sample.pdf, Ctrl-wheel меняет масштаб
3. Android actual через android.graphics.pdf.PdfRenderer — `assembleDebug` зелёный + первая страница на эмуляторе
4. iOS actual через PDFKit — `iosSimulatorArm64Test` зелёный + первая страница в Xcode-симуляторе
5. Web actual через PDF.js (wasmJs) — `wasmJsBrowserDevelopmentRun` показывает первую страницу
6. Общий вьюер: виртуализация + zoom + jump-to-page — на JVM Desktop 50-страничный PDF плавно скроллится, ZoomController unit-test

**Key assumptions:**
- minSdk 24 остаётся (системный PdfRenderer достаточен для M1)
- PDFKit cinterop в Compose 1.10 работает «из коробки»; если нет — Step 4 будет дополнен через /kit-fix
- pdfjs-dist 4.x подходит для wasmJs; js-таргет declared deferred
- Профилирование fps — на M8, в M1 хватит визуальной проверки

**Out of plan (deferred):**
- M2: рукописный ввод, давление, palm rejection
- M3–M4: палитра, экспорт-flatten
- M5–M7: сервер, синхронизация, проекция
- M8: производительность, accessibility, релизные сборки

Open a new session and run:
> /kit-do 2026-05-11-m1-pdf-viewer
```

**Самопроверка:** формат соблюдён, шаги атомарные и тестируемые, ассумпции выписаны. План записан в `.aikit/plans/`, но **не коммитнут** (см. §3.1 — sample-KMP не свой репозиторий).

### 3.4 Что показал прогон Session 1

| Аспект | Оценка | Деталь |
|---|---|---|
| Читабельность Main.md | хорошо | 17 КБ, структура по сессиям с таблицей сверху, все Hard rules выписаны явно |
| Формат CONTEXT/PLAN SUMMARY | хорошо | секции прозрачные, легко проверить self-correctness |
| Применимость к KMP-проекту | хорошо | формат не привязан к стеку, форбиддены сами просочились в `<forbidden>` хвоста Main.md и в CLAUDE.md |
| Размер плана | риск | для большого ТЗ (PDF-редактор: 8 milestone'ов) каждый milestone — отдельная Session 1. Плана на «всю фичу разом» не выйдет — это by design v3, но **в SETUP_PROMPT/README стоит подчеркнуть явно** |
| Контракт `--resume` | непроверен в прогоне | Session 2 reads `git log <plan-commit>..HEAD` — без коммита в sample-KMP проверить нельзя |
| Дискаверабельность Researcher | средне | агент определён, но в команде `/kit` нет триггера «если Researcher есть — дёрни его». Логика «если subagent доступен» лежит в Main.md и зависит от ранера |

---

## 4. Статический анализ Session 2/3 (без коммитов)

Я не выполнял реальный прогон `/kit-do` / `/kit-fix` (см. §3.1). Проверил протоколы построчным чтением `.claude/agents/Main.md` и команд.

### 4.1 Session 2 (`/kit-do`)

| Контракт | Состояние |
|---|---|
| Rehydration после каждого AWAIT (`git log <last_known>..HEAD` + `git show`) | определён в Main.md и в `kit-do.md`. Жёсткое правило «never silently proceed when external commits are present/absent» — однозначно |
| Paste-validation FIX SUMMARY | хорошо описан, прямо запрещает доверять тексту блока — только хеш + `git show`. |
| Squash в Stage 4 | base = `<plan-commit>~1`. **Риск:** если план-коммит — первый на ветке, `~1` уходит на ветку-родитель (обычно master); squash сольёт текущую ветку с master без явного предупреждения. Не блокер, но мне как пользователю хотелось бы видеть подтверждение «base = <hash>, на котором висит <branch>» перед reset --soft |
| Push safety | три rule'а: never bare `--force`, never push without explicit reply, never push к master/main. Все прописаны явно |
| Тесты в Stage 4 | команда из манифеста (`./gradlew allTests`). При первом запуске Gradle тянет ~500 MB зависимостей — рекомендую тайм-аут / явное предупреждение пользователю |

### 4.2 Session 3 (`/kit-fix`)

| Контракт | Состояние |
|---|---|
| Поиск плана по `git log --grep="kit: plan for"` начиная от `<commit-hash>~` | корректно — гарантия что fix относится к плану, в рамках которого совершён target-коммит |
| Single-step rule | явно: `STOP. Output: "This fix needs more than one step…"`. Запрещает silent expansion |
| Dirty tree guard | при `git status` грязный — STOP. **Это** правильно: defect isolation требует чистого дерева |
| FIX SUMMARY формат с anchor-хешем нового коммита | анкер обязателен, paste-back-валидация в Session 2 это использует |

### 4.3 Skill `summary-format`

`.claude/skills/summary-format/SKILL.md` определяет 4 шейпа (CONTEXT/PLAN/STEP/FIX) и 8 common rules. Согласован с шаблонами в `Main.md § Artifacts`. Запреты (no narrative, no emojis, mandatory Uncertain) повторяют output style Main.md — **сознательное дублирование** для людей, читающих только skill.

---

## 5. Найденные дефекты и улучшения

### 5.1 Критические (блокеры)
*Нет.*

### 5.2 Высокие (нужно править до релиза)

| ID | Слой | Описание |
|---|---|---|
| H1 | sample-KMP | `sample-KMP/` не является git-репозиторием. При первом же реальном прогоне `/kit-do` коммиты уйдут в **AI-kit**. Нужно либо `git init` внутри sample-KMP, либо явная пометка «не для боевого прогона» в README. |
| H2 | sample-KMP | Папка `.aikit/bin/` пуста — бинаря нет. SETUP_PROMPT §3.1 декларирует загрузку, но в текущем sample-KMP она не выполнена. Тест использовал `kit-setup/build/bin/mingwX64/releaseExecutable/kit-setup.exe` из родителя. |
| H3 | docs / SETUP_PROMPT | «каждый milestone большого ТЗ = отдельный `/kit`-цикл» — нигде явно не сказано. Пользователь, увидев M1–M8, может рассчитывать на single-plan. Стоит добавить раздел «Когда дробить задачу на несколько `/kit`-сессий». |

### 5.3 Средние

| ID | Слой | Описание |
|---|---|---|
| M1 | Main.md Stage 4 | `git reset --soft <plan-commit>~1`: если план-коммит — первый коммит ветки, `~1` указывает на родителя ветки. Squash сольёт ветку с родительской. Рекомендация — добавить проверку и явное подтверждение base hash перед reset. |
| M2 | kit-setup CLI | `kit-setup --help` не упоминает, что output `verify`/`generate` — всегда одна строка JSON, stderr/exit-code семантика разная. Добавить «STDOUT FORMAT» секцию. |
| M3 | manifest schema | Если в `target.native_provider` указан несуществующий провайдер, бинарь возвращает `unresolvable_model` с фразой «agent cannot resolve model for target». Это технически правильно, но не подсказывает корень — корень в неизвестном `native_provider`. Добавить отдельный код `unknown_provider_reference` или хотя бы упомянуть его в hint. |
| M4 | `.claude/prompts/explore-module.md` | user-prompt сгенерирован, но ни Main.md, ни Researcher.md на него не ссылаются. Это **опциональный** ручной шаблон — стоит сказать об этом в SETUP_PROMPT §3.5 hand-off, иначе файл выглядит осиротевшим. |

### 5.4 Низкие (косметика)

| ID | Слой | Описание |
|---|---|---|
| L1 | Main.md | секция `<forbidden>` дублирует `forbidden_patterns` из `CLAUDE.md`. Сознательно — Main-agent видит их в своём prompt'е; но если список длинный, агенту будет «двойное» сканирование. Можно вынести в один источник через include. |
| L2 | Main.md frontmatter | `tools: "Read, Edit, Write, Glob, Grep, Bash"` — строка, не список. Для Claude Code это норма, но другим target_adapters (Cursor/OpenCode) может быть удобнее YAML-список. Стоит проверить cross-adapter consistency. |
| L3 | tests command | `test_command: "./gradlew allTests"` в манифесте. Первый запуск Gradle тянет много зависимостей; в Stage 4 без явного indicator'а пользователь может подумать что зависло. Документировать или добавить hint в Main.md Stage 4 Step 1. |
| L4 | docs | README sample-KMP не упоминает, что это «kit testing sandbox» — он генерический, скопирован из CMP-template. Стоит добавить заголовок «AI-Kit v3 testing sandbox». |
| L5 | composeApp/webMain | source set единый для js+wasmJs (нет `jsMain`/`wasmJsMain` в composeApp). Это шаблонное решение KMP, но для PDF-задачи с разными pdf.js-импортами потребует разделения. Не дефект кита — но риск, который агент должен заранее увидеть (в нашем плане он отмечен в Step 5 ассумпциях). |

### 5.5 Что зачёт-в-плюс

- **Контракт ошибок чёткий.** Все JSON-коды (`manifest_not_found`, `parse_failed`, `missing_required_key`, `unresolvable_model`, `missing_project_slug`) выдаются стабильно с `path`/`message`/`hint`.
- **Exit-коды правильные.** 0 — успех, 1 — невалидный (verify), 2 — runtime (missing file, parse fail). Соответствует документации.
- **Идемпотентность generate.** Повторный запуск перезаписывает файлы без warning'ов — поведение детерминированное.
- **CLAUDE.md содержит forbidden_patterns** в правильном формате с заголовком `## forbidden_patterns`.
- **`KIT_LANG=ru` в `.claude/settings.json`** — runtime detected, сохранён.
- **Бан на `--no-verify`, `git push --force` без `--with-lease`, push на master/main** — явный, в трёх местах (Main.md Ban list, kit-do.md Hard rules, Push safety).
- **Researcher subagent правильно ограничен** — `Read, Glob, Grep, WebFetch`, нет `Edit/Write/Bash`. Single-purpose, single round-trip.
- **PLAN/STEP/FIX SUMMARY формат** — каждая секция отвечает на конкретный вопрос человека-ревьюера (что сделано, что не сделано, в чём не уверен, как проверить рукой).

---

## 6. Сравнительная оценка vs v2 (если применимо)

| Метрика | v2.6 (последний релиз) | v3.0.0-rc.1 |
|---|---|---|
| Команд | 23 | 3 |
| Скиллов | 10 | 1 |
| Агентов | 8 (Main, BugFixer, Architect, CodeWriter, Verifier, Researcher, +2) | 2 (Main, Researcher) |
| Размер Main.md prompt | ~80 КБ (по памяти из v3-design.md) | 17 КБ |
| Сложность Stage-машины | многоэтапная, с режимами Verifier'а (11 mode) | 4 стадии × 3 сессии |
| Human-in-the-loop | по запросу | обязателен после каждого step-commit |

v3 **существенно проще** и **прозрачнее**. Цена — пользователь должен явно гнать `/kit-do` после `/kit`, не получает «автоматического сквозного» прогона.

---

## 7. Артефакты теста (что осталось в репозитории)

| Путь | Назначение |
|---|---|
| `.aikit/manifest.yaml` | исходный манифест sample-KMP (без правок) |
| `.aikit/plans/2026-05-11-m1-pdf-viewer.md` | план M1, созданный в рамках симуляции Session 1 (**без коммита**) |
| `.claude/agents/Main.md`, `Researcher.md` | сгенерированы `kit-setup generate`, верифицированы |
| `.claude/commands/{kit,kit-do,kit-fix}.md` | сгенерированы, формат frontmatter + workflow OK |
| `.claude/skills/summary-format/SKILL.md` | сгенерирован, согласован с Main.md § Artifacts |
| `.claude/prompts/explore-module.md` | сгенерирован, но не используется в Main/Researcher — см. M4 |
| `CLAUDE.md` | сгенерирован, содержит forbidden_patterns в правильном формате |
| `.claude/settings.json` | `KIT_LANG=ru` зафиксирован |
| **`TEST_REPORT.md`** | **этот файл** |

Тестовые мусорные манифесты (`bad-manifest.yaml`, `broken-yaml.yaml`, `bad-model.yaml`) были созданы и удалены — дерево чистое.

---

## 8. Вердикт

**AI-Kit v3.0.0-rc.1 функционально готов к rc-релизу** на сценариях, аналогичных sample-KMP. Бинарь `kit-setup` стабильно работает по позитивным и негативным кейсам, сгенерированные prompt'ы прозрачные и согласованные, протокол 3-сессий внутренне непротиворечив.

**До общедоступного релиза (3.0.0)** стоит закрыть:

1. **H1** — sample-KMP как самостоятельный git-репозиторий (или явный «test sandbox» маркер).
2. **H2** — автозагрузка бинаря в `.aikit/bin/` фактически не произошла — проверить SETUP_PROMPT pipeline или зафиксировать инструкцию вручную.
3. **H3** — гайд «когда дробить ТЗ на несколько `/kit`-сессий» в README/SETUP_PROMPT.
4. **M1** — fail-safe для squash при первом плане-коммите ветки.
5. **M3** — отдельный код ошибки или hint для unknown provider reference.

Средние/низкие пункты — на 3.0.1 / 3.1.0.

---

## 9. Resolution log (2026-05-11, post-review)

После уточнения «sample-KMP — это test sandbox внутри одного гит-репозитория» и сборки на ветке `v3-prototype`:

| ID | Status | Fix |
|---|---|---|
| H1 | **closed — by design** | sample-KMP намеренно живёт внутри AI-kit, не отдельный репозиторий. Маркер добавлен в [sample-KMP/README.md](README.md) header. Зафиксировано в memory (`sample-kmp-sandbox-design.md`). |
| H2 | **closed — by design** | Для in-repo sandbox бинарь живёт по пути `kit-setup/build/bin/<host>/releaseExecutable/kit-setup[.exe]` родительского репозитория, `.aikit/bin/` остаётся пустой. SETUP_PROMPT-инструкция распространяется только на downstream-пользовательские проекты. |
| H3 | **fixed** | В [SETUP_PROMPT.md](../SETUP_PROMPT.md) §3.5 hand-off добавлен абзац «One /kit per atomic deliverable, not per epic» с правилом «один milestone — один план — один `/kit-do`». |
| M1 | **fixed** | В `Main.md` Stage 4 добавлен шаг 3 «Probe squash base»: `git rev-parse --verify <plan-commit>~1` перед reset; диагностика для root-commit'а; нота про integration-branch, если `BASE` reachable от `origin/master|main`. Изменение в [kit-setup/templates/prompts/Main.md](../kit-setup/templates/prompts/Main.md), пробрасывается в `sample-KMP/.claude/agents/Main.md`. |
| M2 | **fixed** | В [kit-setup/src/commonMain/kotlin/com/aikit/setup/cli/Help.kt](../kit-setup/src/commonMain/kotlin/com/aikit/setup/cli/Help.kt) добавлена секция «STDOUT FORMAT (for orchestrating agents)» с описанием JSON-формы каждой подкоманды и явным утверждением «Diagnostics never go to stderr». |
| M3 | **fixed** | Добавлена новая validation rule [TargetProviderExistsRule](../kit-setup/src/commonMain/kotlin/com/aikit/setup/validation/rules/TargetProviderExistsRule.kt) с кодом `unknown_native_provider`. Зарегистрирована в `Rules.kt` ПЕРЕД `ResolvableModelsRule`, чтобы агент видел корень проблемы первым. Покрыта 3 тестами в `StructuralRulesTest`. Проверено на манифесте `native_provider: nonexistent-provider` → теперь возвращается обе ошибки, `unknown_native_provider` первым. |
| M4 | **fixed** | Hand-off-абзац в `SETUP_PROMPT.md` §3.5 теперь явно описывает `.claude/prompts/` как «manual helpers you can paste into a chat when needed — the Main agent does NOT invoke them automatically». |
| L3 | **fixed** | В `Main.md` Stage 4 шаг 1 (Run tests) теперь обязывает агента произнести команду verbatim и для Gradle/Maven/Bazel добавить «First run may download dependencies (multiple minutes) — this is expected, not a hang.». |
| L4 | **fixed** | В [sample-KMP/README.md](README.md) добавлен верхний блок «AI-Kit v3 testing sandbox» с указанием git-родителя и gitignored-набора. |
| L1/L2/L5 | deferred | Дублирование forbidden_patterns между CLAUDE.md и Main.md `<forbidden>` — by design (агент видит запреты в своём prompt'е). `tools` как строка vs список — внутренний контракт Claude Code, не требует унификации. `composeApp/webMain` single source set — свойство шаблона CMP, не дефект кита. |

**Перепрогон smoke-тестов после правок:**

- `cd kit-setup && ./gradlew mingwX64Test linkReleaseExecutableMingwX64` — BUILD SUCCESSFUL за 59 с, все тесты (включая 3 новых для `TargetProviderExistsRule`) зелёные.
- `kit-setup --version` → `3.0.0-rc.1` (без бампа — закрываются rc-замечания, не feature drop).
- `kit-setup --help` — секция STDOUT FORMAT присутствует.
- `kit-setup verify .aikit/manifest.yaml` → `{"valid":true,"errors":[]}`, exit 0.
- `kit-setup verify .aikit/bad-model.yaml` (с `native_provider: nonexistent-provider`) → теперь два диагностических кода `unknown_native_provider` (новый) + `unresolvable_model` (старый), пользователю сразу виден корень. Тестовый манифест удалён.
- `kit-setup generate .aikit/manifest.yaml` → `{"ok":true,"generated":[9 файлов]}`, sample-KMP кит перегенерирован, новый `Main.md` содержит обновлённые шаги.

**Итог:** к 3.0.0 готово. Никаких блокеров от первого прохода тестирования не осталось.

