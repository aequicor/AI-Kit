# kit-fix diagnostic flow — v4.0.0

**Created:** 2026-05-12
**Branch:** master
**Source task:** Переработать Session 3 (`/kit-fix`) из текущего линейного flow («fix → auto-commit → SUMMARY») в диагностический процесс с пятью stage'ами и AWAIT'ами на ключевых развилках. Включить adaptive fast-path для подвыборов (root-cause, подход) и await-first commit-gate. Релиз v4.0.0.

## Context (digest)

- Текущий `/kit-fix`: 30 строк в `kit-setup/templates/commands/kit-fix.md` + дублирующий блок в `kit-setup/templates/prompts/Main.md:192-203` — линейный flow «show → find plan → read → fix → commit → SUMMARY → END», без AWAIT'ов.
- `debug-loop` skill — `<!-- aikit:optional -->`, 5-step triage (reproduce / localize / reduce / fix / guard); включается через `policies.optional_skills`.
- Multi-runner ветвление в `Main.md` через `{{#if cap.skills}}` и `{{#if cap.subagents}}` — для cursor / aider / qwen-code нужны inline-fallback'и.
- `summary-format` skill уже описывает FIX SUMMARY; новые блоки (DIAGNOSIS, CAUSE OPTIONS, FIX OPTIONS, DIFF PREVIEW) добавляются туда.
- Версия сейчас `3.5.0`. v4.0.0 — major bump (breaking для агентов на старом /kit-fix flow).
- `README.md`, `SETUP_PROMPT.md`, `docs/src/locales/{en,ru}.json` содержат упоминания debug-loop как optional и числа core skills (4) — синхронизируются.
- Тесты `kit-setup/src/commonTest/` зелёные на mingwX64 (после фикса парсера `SkillSections.kt` в pdf-viewer squash).
- AI-Kit репо сам не self-dogfoods свой v3 pipeline на корневом уровне — у него нет `.aikit/manifest.yaml`. Выполнение шагов будет вручную с auto-commit'ами в формате `kit: step N/M — <slug>`, без `kit-setup` orchestrator binary.

## Invariants

- CLI binary interface (subcommands, exit codes, JSON shapes, error codes) не меняется
- Sessions 1 и 2 не трогаются — изменения только в Session 3 и связанных артефактах
- Skill-format остаётся прежним (description / when to invoke / procedure / output format), новых manifest-полей нет
- Существующие тесты в `kit-setup/src/commonTest/` остаются зелёными
- Multi-runner совместимость: при `cap.skills=false` весь новый flow должен иметь inline-fallback в `Main.md`

## Steps

### Step 1 — new-skills-and-debug-loop-redesign
- **Goal:** Создать новые скиллы `cause-hypotheses` и `fix-options`. Убрать `<!-- aikit:optional -->` marker у `debug-loop` и переоформить его body как авторитетный формат Stage 1 диагностики (always-on, не triage-only).
- **DoD:** В `kit-setup/templates/skills/` есть три обновлённых SKILL.md; debug-loop без optional-маркера; `./gradlew.bat mingwX64Test` зелёный.
- **Review:** standard
- **What would be wrong:** Новые скиллы не имеют обязательных секций (`# When to invoke`, `# Procedure`, `# Output format`) — wrapper подставит пустые placeholder'ы, на runtime получим скиллы-болванки.
- **Verify:** [compile, test]
- **Expect:** green
- **Shape:**
    - **files-glob:** "kit-setup/templates/skills/**"
    - **max-diff-lines:** 400
    - **no-test-changes:** true
- **Assumptions:** Существующий парсер SkillSections корректно обработает новые скиллы (доказано fenced-fence фиксом в pdf-viewer squash).

### Step 2 — summary-format-extension
- **Goal:** Расширить `summary-format` skill — добавить блоки DIAGNOSIS, CAUSE OPTIONS, FIX OPTIONS, DIFF PREVIEW. Каждый — с шаблоном (поля + пример), и явной отметкой какой Session-3 stage его эмитит.
- **DoD:** `kit-setup/templates/skills/summary-format/SKILL.md` содержит четыре новых раздела; `./gradlew.bat mingwX64Test` зелёный.
- **Review:** light
- **What would be wrong:** (n/a — light)
- **Verify:** [compile, test]
- **Expect:** green
- **Shape:**
    - **files-glob:** "kit-setup/templates/skills/summary-format/**"
    - **max-diff-lines:** 120
    - **no-test-changes:** true

### Step 3 — rewrite-kit-fix-command
- **Goal:** Переписать `kit-setup/templates/commands/kit-fix.md` под пятиэтапный flow: Stage 1 (Анамнез) → Stage 2 (Варианты причины) → Stage 3 (Варианты фикса) → Stage 4 (Реализация + DIFF PREVIEW) → Stage 5 (Commit). Adaptive fast-path для Stage 2/3 (auto-advance при single plausible option, с пометкой в FIX SUMMARY). Stage 4 AWAIT — обязательный, не подлежит auto-skip.
- **DoD:** kit-fix.md содержит Stage 1-5 с явными reply-командами для каждого AWAIT (например, `ok` / `<correction>` / `abort` / выбор номера); fast-path условия описаны как «if only-one-plausible-cause then output `Auto-advanced: …` and proceed»; `./gradlew.bat mingwX64Test` зелёный.
- **Review:** heavy
- **What would be wrong:** Reply-команды для AWAIT'ов размыты (нет фиксированного набора слов) — модель угадывает, пользователь видит неожиданное поведение. Или fast-path-trigger описан неоднозначно — модель пропускает обязательный gate.
- **Verify:** [compile, test]
- **Expect:** green
- **Assumptions:** —

### Step 4 — update-main-prompt-session-3
- **Goal:** Обновить `kit-setup/templates/prompts/Main.md` Session 3 секцию (строки 192-203) под новый flow. Добавить `{{#if cap.skills}}` ветку (ссылки на новые скиллы) и `{{#unless cap.skills}}` ветку (inline-формат CAUSE OPTIONS / FIX OPTIONS для runner'ов без skill-tree). Синхронизировать таблицу команд в начале prompt'а (строки 9-13).
- **DoD:** `Main.md` описывает Session 3 с пятью stage'ами; есть симметричные `cap.skills`-ветки; reply-команды совпадают с kit-fix.md из Step 3 байт-в-байт; `./gradlew.bat mingwX64Test` зелёный.
- **Review:** heavy
- **What would be wrong:** Рассинхрон между kit-fix.md и Main.md (stage'ы пронумерованы по-разному, reply-keys или текст команд отличаются) — модель будет читать оба и хаотично выбирать; пользователь видит непоследовательное поведение между chat'ом и slash-вызовом.
- **Verify:** [compile, test]
- **Expect:** green
- **Assumptions:** —

### Step 5 — readme-and-setup-prompt
- **Goal:** Обновить `README.md` (строки про /kit-fix в обзоре и про `debug-loop` как opt-in) и `SETUP_PROMPT.md` (perks-list optional skills, число core skills в Phase 6 hand-off). Везде: debug-loop переходит в core, core-skill count поднимается с 4 до 5, новые скиллы (`cause-hypotheses`, `fix-options`) — также core.
- **DoD:** В обоих файлах ни одно упоминание debug-loop как «opt-in»/«optional» не осталось; счёт core skills согласован (5 или 7 — учесть `cause-hypotheses` и `fix-options`); фразы про /kit-fix передают новый 5-stage характер.
- **Review:** standard
- **What would be wrong:** Counts в SETUP_PROMPT расходятся с фактическим выводом kit-setup binary — orchestrating agent сообщает пользователю «4 core skills», а реально emits 7, пользователь видит mismatch при первом запуске.
- **Verify:** [compile, test]
- **Expect:** green
- **Shape:**
    - **files-glob:** "{README.md,SETUP_PROMPT.md}"
    - **max-diff-lines:** 80

### Step 6 — docs-site-i18n
- **Goal:** Обновить `docs/src/locales/en.json` и `docs/src/locales/ru.json` — найти упоминания /kit-fix, debug-loop, core-skill list, синхронизировать под новый flow. Симметрично оба locale.
- **DoD:** Обе локали содержат одни и те же ключи (нет «raw key» drift'а); `cd docs && npm run build` собирается без ошибок.
- **Review:** standard
- **What would be wrong:** Добавлен ключ в en.json без зеркального ru.json — на сайте появляется raw-key в одной из локалей. Или текстуальный drift (en.json говорит про 5 stages, ru.json — про 4).
- **Verify:** [shell: "cd docs && npm run build"]
- **Expect:** green
- **Assumptions:** `npm` и `node` доступны в PATH; `node_modules/` в docs/ уже установлен. Если нет — отметить в SUMMARY как `BUILD: skipped (npm missing)` и перейти к Step 7, оставив `--skip-verify` override.

### Step 7 — version-bump
- **Goal:** Поднять версию до `4.0.0` в `kit-setup/build.gradle.kts` (`version = "4.0.0"`), `kit-setup/src/commonMain/kotlin/com/aikit/setup/cli/Help.kt` (`KIT_SETUP_VERSION = "4.0.0"`), и `docs/src/locales/{en,ru}.json` (`home.hero.badge` field).
- **DoD:** Четыре файла содержат `4.0.0` в нужных местах; `./gradlew.bat mingwX64Test` зелёный.
- **Review:** light
- **What would be wrong:** (n/a — light)
- **Verify:** [compile, test]
- **Expect:** green
- **Shape:**
    - **files-glob:** "{kit-setup/build.gradle.kts,kit-setup/src/commonMain/kotlin/com/aikit/setup/cli/Help.kt,docs/src/locales/en.json,docs/src/locales/ru.json}"
    - **max-diff-lines:** 10
    - **no-test-changes:** true

### Step 8 — smoke-regenerate-sample-kmp
- **Goal:** Пересобрать релизный бинарь (`linkReleaseExecutableMingwX64`), запустить `kit-setup.exe generate sample-KMP/.aikit/manifest.yaml`. Проверить, что в `sample-KMP/.claude/skills/` появились `cause-hypotheses/SKILL.md` и `fix-options/SKILL.md`; что `debug-loop/SKILL.md` сгенерирован без `<!-- aikit:optional -->`; что `sample-KMP/CLAUDE.md` содержит Session 3 5-stage flow. Затронутый sample-KMP — это renderer-output, не source.
- **DoD:** Перечисленные файлы в `sample-KMP/.claude/` существуют с ожидаемым содержанием; `kit-setup.exe generate` вернул `{"ok": true, …}` без errors.
- **Review:** standard
- **What would be wrong:** Регенерация пропустила новый скилл (имя не подхватилось `generateTemplates` Gradle-task потому что папка пустая или без `SKILL.md`) — пользователь, обновляющий kit, не получит ожидаемый артефакт. Или debug-loop всё ещё содержит optional marker (Step 1 неполный).
- **Verify:** [shell: ".\\kit-setup\\gradlew.bat -p kit-setup linkReleaseExecutableMingwX64"]
- **Expect:** green
- **Assumptions:** Подходящий `sample-KMP/.aikit/manifest.yaml` есть на диске (не закоммичен, по `.gitignore`). Это известно из предыдущих regenerate'ов.

## Out of scope

- Применять новый flow к /kit-do step-commits — остаётся текущая auto-commit + AWAIT для standard/heavy
- Перерабатывать /kit (Session 1) или Researcher subagent
- Менять manifest schema или добавлять новые `policies`-поля
- Создавать release-tag `v4.0.0` и push к origin — отдельный действие после Ship-stage validation (вручную, потому что нет /kit-do orchestrator на репо-уровне)
- Backport фикса парсера `SkillSections.kt` отдельным patch-релизом v3.5.1 — он уже squash'нут внутрь pdf-viewer коммита, фактически отдельный patch не выпускается; будет упомянут одним bullet в release notes v4.0.0
