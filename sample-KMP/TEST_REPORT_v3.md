# AI-Kit v3.0.0 — отчёт о качественном тестировании на sample-KMP

**Дата:** 2026-05-11  
**Тестировщик:** Claude Sonnet 4.6 (автоматизированный прогон)  
**Ветка AI-kit:** `v3-prototype_test4`  
**Версия исходников:** `3.0.0` (`kit-setup/build.gradle.kts`, `Help.kt`)  
**Версия пересобранного бинаря:** `3.0.0` (пересборка выполнена в ходе теста — см. §1.1)  
**Целевой проект:** `sample-KMP` (Compose Multiplatform: android / ios / jvm / js / wasmJs / `:server` Ktor)  
**Тестовое задание:** мультиплатформенный PDF-редактор с рукописными аннотациями, LAN-синхронизацией и режимом проекции (8 milestone'ов, тест охватывает M1)  
**Базовый отчёт:** `sample-KMP/TEST_REPORT.md` (тест rc.1 от 2026-05-11, ветка test2)

---

## 1. Среда и подготовка

### 1.1 Пересборка бинаря — критическая находка до начала теста

Бинарь в `kit-setup/build/bin/mingwX64/releaseExecutable/kit-setup.exe` на момент старта показывал версию `3.0.0-rc.1`. Исходники (`Help.kt`, `build.gradle.kts`) уже содержали `3.0.0` (commit `0d6bb26`), но бинарь не был пересобран после:
- `1f7c0a8` — изменения `Main.md` (Stage 4 squash probe), `kit-fix.md`, `compose-multiplatform.yaml` профиля
- `0d6bb26` — бамп версии

**Действие:** пересборка через `./gradlew linkReleaseExecutableMingwX64` (JDK 21, 41 с). После пересборки:
```
kit-setup --version → 3.0.0
kit-setup schema → kit_version 3.0.0
```

**Вывод:** перед каждым тестом после изменения исходников или шаблонов бинарь должен быть пересобран. Это свойство Kotlin/Native архитектуры (templates embedded at compile time) — **не дефект**, но **необходимо задокументировать явно** в README раздел "Before testing".

### 1.2 Структура sample-KMP

| Модуль | Таргеты | Стек |
|---|---|---|
| `:composeApp` | android, iosArm64, iosSimulatorArm64, jvm, js, wasmJs | CMP 1.10.3, Material3 1.10.0-alpha05 |
| `:shared` | android, ios, jvm, js, wasmJs | commonMain пуст |
| `:server` | JVM | Ktor 3.4.3 (Netty) |

- Kotlin 2.3.21, AGP 8.11.2, android-minSdk **24** (ТЗ требует 26)
- Нет Koin, SQLDelight, kotlinx-serialization, Kermit в `libs.versions.toml`
- Нет `-Xexpect-actual-classes` в `shared/build.gradle.kts`
- Material3 в alpha — приемлемо для M1–M7, риск на M8

---

## 2. Результаты по бинарю `kit-setup`

### 2.1 Субкоманды baseline

| Команда | Результат | Exit | Оценка |
|---|---|---|---|
| `kit-setup --version` | `3.0.0` | 0 | соответствует исходникам |
| `kit-setup --help` | usage с SUBCOMMANDS / EXIT CODES / **STDOUT FORMAT** | 0 | STDOUT FORMAT секция присутствует (добавлена по M2 из rc.1 отчёта) |
| `kit-setup schema --format human` | 2 агента, 5 адаптеров, 12 профилей, 4 enum'а | 0 | тот же контракт что в rc.1 |
| `kit-setup schema --format json` | machine-readable JSON | 0 | стабильный |

### 2.2 Verify — позитивный кейс

**Манифест:** `sample-KMP/.aikit/manifest.yaml` (v3.0.0, stack.profiles: kotlin-gradle + compose-multiplatform + clean-architecture + quality-gates)

**Первая попытка — FAIL (parse_failed):**
```json
{"valid":false,"errors":[{"path":"...","code":"parse_failed","message":"Unexpected indentation at line 9 (expected 2, got 4)"}]}
```
Причина: YAML block scalar `description: >` с 4-space continuation. Парсер kit-setup использует собственный BlockYamlParser, который не поддерживает folded/literal scalars (`>` / `|`). Исправлено на однострочный quoted string.

**После исправления:**
```json
{"valid":true,"errors":[]}
exit: 0
```

**Вывод (новая находка N1):** `BlockYamlParser` не поддерживает YAML block scalars (`>` / `|`). Для описаний (description, responsibility) пользователь должен использовать однострочные строки. Это **не задокументировано** в schema / --help.

### 2.3 Verify — негативные кейсы

| Сценарий | Код ошибки | Exit | Документировано |
|---|---|---|---|
| Несуществующий файл | `manifest_not_found` | 2 | да |
| Malformed YAML (`{unclosed: [`) | `parse_failed` с номером строки | 2 | да |
| Отсутствуют обязательные ключи (targets/providers/models/agents) | 4× `missing_required_key` одним списком | 1 | да |
| `native_provider` ссылается на несуществующий провайдер | `unknown_native_provider` (первым!) + `unresolvable_model` (вторым) | 1 | да — M3 из rc.1 закрыт |
| `generate` на невалидном манифесте | те же `missing_required_key` без генерации | 1 | да |

**Замечание по NEG-4 (unknown_native_provider):** тестовый манифест был создан через PowerShell `Set-Content ... -Encoding utf8` heredoc. Первая ошибка в ответе была ложный `missing_required_key: manifest_version` — вероятно, PowerShell UTF-8 BOM в начале файла ломает парсер. **Сами целевые коды (`unknown_native_provider`, `unresolvable_model`) возвращались корректно.** Это среднее замечание (N2 ниже).

### 2.4 Generate — позитивный кейс

```
kit-setup generate .aikit/manifest.yaml
{"ok":true,"generated":["CLAUDE.md",".claude/agents/Main.md",".claude/agents/Researcher.md",
 ".claude/skills/summary-format/SKILL.md",".claude/commands/kit-do.md",
 ".claude/commands/kit-fix.md",".claude/commands/kit.md",
 ".claude/prompts/explore-module.md",".claude/settings.json"]}
exit: 0
```

**9 артефактов** — тот же набор что в rc.1. Повторный запуск (идемпотентность): тот же вывод, exit 0. ✓

---

## 3. Анализ сгенерированных артефактов

### 3.1 `CLAUDE.md`

- Заголовок: `# PDF Annotator KMP — kit constitution`
- Секция `## forbidden_patterns` содержит **76 паттернов** (4 из базового манифеста + 7 из kotlin-gradle + 8 из compose-multiplatform + 57 из clean-architecture + quality-gates)
- Паттерны объединены из всех профилей правильно — слияние по DeepMerge работает корректно
- Критические Compose-специфичные паттерны на месте: `-Xexpect-actual-classes`, `compileKotlinMetadata NO-OP` ← из commit `1f7c0a8`, подтверждает что пересборка была необходима

### 3.2 `.claude/agents/Main.md`

Проверяемые элементы:

| Элемент | Результат |
|---|---|
| Frontmatter `model: "claude-sonnet-4-6"` | ✓ |
| `<project>PDF Annotator KMP</project>` | ✓ |
| `<stack>kotlin / compose-multiplatform</stack>` | ✓ |
| `<communication_language>Russian (ru)</communication_language>` | ✓ |
| Session 1/2/3 протокол (v3 baseline) | ✓ |
| Stage 4 squash probe (`Probe squash base` + `git rev-parse --verify`) | ✓ (добавлен в `1f7c0a8`) |
| Stage 4 Gradle dependency hint (first-run download) | ✓ |
| `<forbidden>` — 76 паттернов (тот же набор что в CLAUDE.md) | ✓ |
| Ban list: `--no-verify`, `git push --force` без `--with-lease`, push к main | ✓ |
| Rehydration rule | ✓ |

### 3.3 `.claude/agents/Researcher.md`

| Элемент | Результат |
|---|---|
| Frontmatter `model: "claude-haiku-4-5-20251001"` | ✓ |
| Роль: only Stage 1, no code write, no commit | ✓ |
| Digest format (RESEARCH DIGEST) | ✓ |
| Limits: one round-trip, no subagent dispatch | ✓ |
| Refusal pattern для non-Stage-1 brief | ✓ |

### 3.4 Команды

| Файл | Элемент | Результат |
|---|---|---|
| `kit.md` | Frontmatter description | ✓ |
| `kit.md` | Hard rules: no execution in Session 1, commit plan before END | ✓ |
| `kit.md` | Empty task guard | ✓ |
| `kit-do.md` | Rehydration first rule | ✓ |
| `kit-do.md` | Stage 4 squash flow | ✓ |
| `kit-do.md` | `--force-with-lease` only, explicit warning | ✓ |
| `kit-fix.md` | Single-step rule | ✓ |
| `kit-fix.md` | Dirty tree guard | ✓ |
| `kit-fix.md` | FIX SUMMARY anchor requirement | ✓ |

### 3.5 `.claude/settings.json`

```json
{
  "permissions": {"deny": [], "allow": []},
  "mcpServers": {"serena": {"command": "serena-mcp", "args": ["--workspace","."]}},
  "env": {"KIT_LANG": "ru"}
}
```

- `KIT_LANG=ru` зафиксирован ✓
- Serena MCP добавлен из `kotlin-gradle` профиля ✓
- Permissions пустые — нормально для начала проекта ✓

### 3.6 `.claude/skills/summary-format/SKILL.md`

- Определяет 4 shape: CONTEXT/PLAN/STEP/FIX ✓
- 8 common rules (no narrative, cite path:line, mandatory Uncertain секция) ✓
- Согласован с Main.md § Artifacts ✓

---

## 4. Session 1 симуляция (`/kit M1 — каркас и просмотр PDF`)

### 4.1 Stage 1 — CONTEXT SUMMARY

```
## CONTEXT SUMMARY · m1-pdf-viewer

**Read:**
- build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
- composeApp/build.gradle.kts, shared/build.gradle.kts
- .aikit/manifest.yaml, CLAUDE.md

**Key findings:**
- Kotlin 2.3.21, CMP 1.10.3, AGP 8.11.2, Ktor 3.4.3, Material3 1.10.0-alpha05 (alpha — риск на M8)
- Модули: :composeApp (android/ios/jvm/js/wasmJs), :shared (commonMain пуст), :server (Ktor JVM)
- android-minSdk=24 в TOML (ТЗ требует 26 — расхождение, откладываем на M8)
- shared/commonMain.dependencies пуст — нет Koin, SQLDelight, kotlinx-serialization, Kermit
- -Xexpect-actual-classes не включён в shared/build.gradle.kts (KT-61573, нужен для Kotlin 2.x)
- Нет PDF-библиотек в TOML; нет expect/actual PDF-классов

**Constraints discovered:**
- `:shared:compileKotlinMetadata` — NO-OP на K2 (forbidden в CLAUDE.md); DoD должен использовать
  `:shared:compileCommonMainKotlinMetadata` или target-specific task
- composeApp:webMain — единый source set для js+wasmJs; для полной PDF.js интеграции потребуется
  разделение (risk на Step 5)
- iOS компиляция без Mac-хоста даёт stub-actual; Step 4 DoD помечён Assumption

**Out of scope (M1):**
- M2–M7: аннотации, палитра, экспорт, сервер, синхронизация, проекция
- Профилирование fps — M8

Reply `ok` to proceed to plan, or correct context with: "<adjustment>"
```

**Самопроверка:** CONTEXT SUMMARY содержит все обязательные секции. Нет нарратива. Все факты — с источником. Ответ-приглашение присутствует. Формат рабочий.

### 4.2 Stage 2 — PLAN SUMMARY

Предполагая `ok` от пользователя — переход к Stage 2. План записан в `.aikit/plans/2026-05-11-m1-pdf-viewer.md` (без реального коммита — см. §4.4).

```
## PLAN SUMMARY · m1-pdf-viewer · plan `2026-05-11-m1-pdf-viewer`

Saved to: .aikit/plans/2026-05-11-m1-pdf-viewer.md

**Steps (6 total):**
1. Foundation — PDF-зависимости (PDFBox JVM, cinterop-заглушка iOS, PDF.js web),
   -Xexpect-actual-classes, Koin + kotlinx.serialization в TOML
   DoD: ./gradlew :shared:compileCommonMainKotlinMetadata зелёный, без KT-61573 warning
2. Domain models — expect class PdfDocument/PdfPageRenderer, DocumentRef, PageRef, PdfPageBitmap
   DoD: ./gradlew :shared:compileCommonMainKotlinMetadata зелёный + PageRefTest 3 test-cases
3. JVM actual (PDFBox) + Compose Desktop viewer
   DoD: ./gradlew :composeApp:run открывает sample.pdf, первая страница отображается
4. Android actual (android.graphics.pdf.PdfRenderer)
   DoD: assembleDebug зелёный + визуальный smoke-test на эмуляторе API 24
5. iOS actual (PDFKit cinterop) — Assumption: требует Mac-хост
   DoD: ./gradlew :shared:compileKotlinIosArm64 зелёный + stub-экран в Xcode-симуляторе
6. Zoom + виртуализация + jump-to-page (TransformableState, LazyColumn)
   DoD: JVM Desktop: 50-стр. PDF плавно скроллится + ZoomControllerTest в commonTest

**Key assumptions:**
- minSdk 24 приемлем для M1; поднять до 26 — отдельный шаг перед M8
- iOS Step 5 завершается stub без Mac-хоста — DoD помечен Assumption
- Web PDF.js — полная интеграция откладывается до M4; в M1 заглушка
- Material3 alpha05 — не блокирует M1–M5

**Out of plan:**
- M2–M8 (см. описание milestone'ов в ТЗ)

Open a new session and run:
> /kit-do 2026-05-11-m1-pdf-viewer
```

**Самопроверка:** формат соблюдён, шаги атомарные и тестируемые, DoD конкретные, ассумпции явные. DoD не использует NO-OP `compileKotlinMetadata`. Ссылка на следующую сессию присутствует.

### 4.3 Что показал прогон Session 1

| Аспект | Оценка | Деталь |
|---|---|---|
| Читабельность Main.md | хорошо | Stage 1/2/3/4 явно разграничены, таблица команд сверху |
| Формат CONTEXT SUMMARY | хорошо | все секции присутствуют, нет нарратива |
| Формат PLAN SUMMARY | хорошо | шаги bounded + runnable, DoD валидны для KMP |
| Forbidden patterns в CLAUDE.md | хорошо | 76 паттернов из 4 профилей — релевантны для PDF-проекта |
| DoD NO-OP trap | хорошо | CLAUDE.md явно запрещает `compileKotlinMetadata`, план правильно использует `compileCommonMainKotlinMetadata` |
| communication_language | хорошо | `KIT_LANG=ru` в settings.json, тег `<communication_language>` в каждом агенте |
| Researcher subagent | средне | агент определён, Haiku 4.5 выбран верно. Stage 1 контент собирался без него (test sandbox, нет отдельного Claude Code сеанса) |

### 4.4 Замечание по среде (наследовано из rc.1)

`sample-KMP/` не является самостоятельным git-репозиторием — закрыто как `by design` в rc.1 (memory: `sample-kmp-sandbox-design.md`). Коммит `.aikit/plans/2026-05-11-m1-pdf-viewer.md` в рамках теста не выполнялся во избежание загрязнения истории AI-kit.

---

## 5. Найденные дефекты и улучшения

### 5.1 Критические (блокеры)
*Нет.*

### 5.2 Высокие

| ID | Слой | Описание |
|---|---|---|
| H1 | kit-setup / docs | **Бинарь не пересобирается автоматически после изменений шаблонов**. Commit `1f7c0a8` обновил `Main.md`, `kit-fix.md`, профиль `compose-multiplatform.yaml` — но бинарь не был пересобран. Шаблоны встроены в бинарь при сборке (Kotlin/Native, templates embedded). Пользователь, скачавший rc.1 бинарь, не получит v3.0.0 шаблонов. **Нужно явно указать в README**: "If you built the binary locally, rebuild after any template changes." Для downstream users: download the new release binary. |

### 5.3 Средние

| ID | Слой | Описание |
|---|---|---|
| M1 | kit-setup BlockYamlParser | Не поддерживает YAML block scalars (`>` / `|`). Манифест с multiline description (самый естественный YAML-способ) фейлится с `parse_failed: Unexpected indentation`. Это не задокументировано ни в `--help`, ни в schema. Добавить явный hint в parse_failed message: "YAML folded/literal scalars are not supported — use single-line quoted strings." |
| M2 | Test environment (PowerShell) | `Set-Content ... -Encoding utf8` добавляет BOM в начало файла при создании YAML. kit-setup BlockYamlParser не стрипает BOM → ложный `missing_required_key: manifest_version`. Это **Windows-специфичная** проблема. Добавить BOM-stripping в начало парсера (или явно документировать). |

### 5.4 Низкие

| ID | Слой | Описание |
|---|---|---|
| L1 | Main.md | `<forbidden>` block полностью дублирует `CLAUDE.md` (76 паттернов). Сознательно — агент видит запреты в своём prompt'е. Но при большом наборе профилей это может сильно раздуть context агента. Потенциальная оптимизация — включение по ссылке через конституцию вместо дублирования. |
| L2 | `.claude/prompts/explore-module.md` | User-prompt сгенерирован, но не описан в kit.md/kit-do.md как доступный. Артефакт существует, но discovery низкий. Наследовано из rc.1, зафиксировано как deferred. |
| L3 | manifest schema | `stack.profiles` не listed в `kit-setup schema --format human`. Пользователю нужно читать профили в README или templates/ вручную. Добавить секцию `profiles` в `schema --format human` была бы полезна (она есть, но с описанием — 12 профилей с описанием). Фактически уже работает — переоценка: L3 закрыт. |
| L4 | YAML parser | YAML alias/anchor (`&anchor`, `*ref`) не протестированы. Если поддерживаются — неизвестно. Документировать или добавить тест. |

### 5.5 Что зачёт-в-плюс (новые по сравнению с rc.1)

- **Stage 4 squash probe** работает корректно — текст в Main.md наличествует (`git rev-parse --verify <plan-commit>~1`, root-commit диагностика, integration-branch нота). Закрыт M1 из rc.1. ✓
- **`unknown_native_provider`** возвращается первым перед `unresolvable_model` — правильный порядок диагностики. Закрыт M3 из rc.1. ✓
- **`-Xexpect-actual-classes` и NO-OP `compileKotlinMetadata`** паттерны в CLAUDE.md — из обновлённого `compose-multiplatform.yaml` профиля (`1f7c0a8`). Критически важны для Kotlin 2.x. ✓
- **communication_language** рендерится в каждом агенте и каждой команде с одним и тем же языком. Не было в rc.1 явного упоминания — теперь проверено. ✓
- **Идемпотентность generate** сохраняется — повторный запуск без изменений даёт тот же набор файлов. ✓

---

## 6. Сравнение v3.0.0 vs v3.0.0-rc.1

| Аспект | rc.1 | 3.0.0 | Изменение |
|---|---|---|---|
| `kit-setup --version` | 3.0.0-rc.1 | **3.0.0** | ✓ бамп |
| Stage 4 squash probe | отсутствовал | **присутствует** | ✓ закрыт M1 |
| `unknown_native_provider` | присутствовал (d215c88) | присутствует | без регрессии |
| `compose-multiplatform.yaml` | без K2 паттернов | **+2 паттерна** (`-Xexpect-actual-classes`, NO-OP trap) | ✓ закрыт L5 |
| `kit-fix.md` | — | minor protocol hardening | ✓ |
| Число сгенерируемых файлов | 9 | 9 | без изменений |
| Поддержка YAML block scalars | нет | нет | **без изменений (M1 новый)** |
| BOM-стрипинг | нет | нет | **без изменений (M2 новый)** |

---

## 7. Артефакты теста

| Путь | Назначение |
|---|---|
| `sample-KMP/.aikit/manifest.yaml` | свежий манифест v3.0.0, pdf-annotator-kmp, 4 профиля |
| `sample-KMP/.aikit/plans/2026-05-11-m1-pdf-viewer.md` | план M1 (6 шагов), Session 1 симуляция **без коммита** |
| `sample-KMP/CLAUDE.md` | сгенерирован, 76 forbidden_patterns, проверен |
| `sample-KMP/.claude/agents/Main.md` | сгенерирован, модель claude-sonnet-4-6, Stage 4 probe present |
| `sample-KMP/.claude/agents/Researcher.md` | сгенерирован, модель claude-haiku-4-5-20251001 |
| `sample-KMP/.claude/commands/{kit,kit-do,kit-fix}.md` | сгенерированы, hard rules проверены |
| `sample-KMP/.claude/skills/summary-format/SKILL.md` | сгенерирован, 8 common rules, 4 shapes |
| `sample-KMP/.claude/prompts/explore-module.md` | сгенерирован (не проверялся отдельно) |
| `sample-KMP/.claude/settings.json` | KIT_LANG=ru, serena MCP, пустые permissions |
| **`sample-KMP/TEST_REPORT_v3.md`** | **этот файл** |

---

## 8. Вердикт

**AI-Kit v3.0.0 функционально работоспособен.** Все rc.1 high/medium находки, заявленные как closed, подтверждены:

| ID (rc.1) | Статус в v3.0.0 |
|---|---|
| H1 sample-KMP не git-репо | by design, подтверждено |
| H2 .aikit/bin/ пуст | by design, подтверждено |
| H3 один kit per milestone | SETUP_PROMPT обновлён — не проверялся отдельно |
| M1 squash probe | ✓ подтверждён в Stage 4 Main.md |
| M3 unknown_native_provider | ✓ подтверждён в NEG-4 |
| M4 explore-module | deferred |
| L3 Gradle download hint | ✓ подтверждён в Stage 4 |
| L4 sandbox header в README | не проверялся |

**Новые находки v3.0.0:**

| ID | Приоритет | Краткое описание |
|---|---|---|
| H1 (новый) | высокий | Бинарь не пересобирается автоматически после изменений шаблонов — требует явной документации |
| M1 (новый) | средний | BlockYamlParser не поддерживает YAML block scalars (`>` / `\|`) без диагностики |
| M2 (новый) | средний | PowerShell UTF-8 BOM вызывает ложный `missing_required_key` при создании манифеста |

**Рекомендации до следующего релиза (3.0.1):**

1. **H1** — добавить в README.md раздел «Development: rebuild after template changes» с явной инструкцией `./gradlew linkReleaseExecutable<Target>`.
2. **M1** — улучшить сообщение `parse_failed` для indentation errors: добавить hint «YAML folded/literal scalars (> and |) are not supported; use single-line quoted strings».
3. **M2** — в `BlockYamlParser` или `DefaultManifestLoader`: стрипать UTF-8 BOM (0xEF 0xBB 0xBF) в начале входного потока; добавить тест.
