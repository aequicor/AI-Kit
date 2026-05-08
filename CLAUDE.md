# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Two independent components share this repo. They have **different toolchains and different release/deploy paths** — keep that in mind before running commands.

- `kit-setup/` — Kotlin Multiplatform CLI (`com.aikit.setup`). Compiles to **native** executables for Windows x64, Linux x64, macOS arm64, macOS x64 via Kotlin/Native. The binary (`kit-setup`) generates `CLAUDE.md`, `.claude/settings.json`, `opencode.json`, agent definitions, and `.planning/` for **target projects** — not for this repo. Entry point: `kit-setup/src/commonMain/kotlin/com/aikit/setup/Main.kt` (`runSetup`); per-platform `Platform.kt` files implement `FileSystem` against POSIX/Win32 and call `runSetup`.
- `docs/` — React 18 + Vite + TypeScript SPA with i18n (en/ru via `react-i18next`). Deployed to GitHub Pages at `https://aequicor.github.io/AI-Kit/`. Note `vite.config.ts` sets `base: '/AI-Kit/'` — paths break if this is changed without renaming the repo.
- `SETUP_PROMPT.md` — the prompt users paste into Claude Code/OpenCode. It downloads the latest binary from `https://github.com/aequicor/AI-Kit/releases/latest/download/` and runs it. Any change to CLI flags **must** be reflected here, in the README CLI section, and in `docs/src/locales/*.json` `cli.flagsList` / `cli.defaults`.

## Common commands

### Kotlin CLI (`kit-setup/`)

Requires JDK 11+ (CI uses JDK 21). Native compilation; Gradle picks targets based on host OS — building macOS targets on Linux silently does nothing, that's by design (`kotlin.native.ignoreDisabledTargets=true`).

```sh
cd kit-setup
./gradlew linkReleaseExecutableMacosArm64    # or MacosX64 / LinuxX64 / MingwX64
./gradlew build                              # all enabled targets
```

Run the freshly built binary against this repo or any other:

```sh
./build/bin/macosArm64/releaseExecutable/kit-setup.kexe --name demo --path /tmp/demo --lang kotlin --provider both
```

There is **no test suite yet** — no `src/*Test/` source sets exist. If you add tests, wire them per Kotlin Multiplatform conventions before claiming they run.

### Docs site (`docs/`)

```sh
cd docs
npm install
npm run dev      # local preview (note: base path '/AI-Kit/' — open http://localhost:5173/AI-Kit/)
npm run build    # tsc -b && vite build → docs/dist
npm run preview
```

## Release plan — keep README and the Pages site in sync

Releases are triggered by pushing a `v*` tag. The release workflow (`.github/workflows/release.yml`) builds all four native binaries and attaches them to a GitHub Release. The Pages workflow (`.github/workflows/pages.yml`) **only fires on changes under `docs/**`** — bumping the CLI version alone will not refresh the published guide.

Because of that split, every release must include doc updates **in the same release commit, before tagging**, otherwise the published site lags behind the binary.

**Pre-tag checklist** — before pushing `vX.Y.Z`:

1. **Bump version in code:** `kit-setup/build.gradle.kts` (`version = "X.Y.Z"`).
2. **Sync the version badge** shown on the landing page: `docs/src/locales/en.json` and `docs/src/locales/ru.json` → `home.badge` (currently `"User Guide · v1.0"` / `"Руководство · v1.0"`).
3. **Update `README.md`** if any of the following changed: CLI flags, supported languages/providers, platform list, or the quick-setup prompt URL. Keep the "Quick setup" prompt block aligned with `SETUP_PROMPT.md`.
4. **Update `SETUP_PROMPT.md`** for any flag/argument change. Then update the docs site equivalents: `docs/src/locales/en.json` and `docs/src/locales/ru.json` under `cli.flagsList`, `cli.defaults`, and `files.purposes`.
5. **Update the FAQ / file table** in both locales if the generator now produces different files (check `ProjectGenerator.kt` against `docs/src/pages/Files.tsx` and the `files.purposes` keys).
6. Verify the footer license string in both locales matches the actual `LICENSE` file. The repo is **Apache 2.0**; the locale strings (`footer.license`) historically said "MIT" — fix if you see it.
7. Commit everything in a single "Release vX.Y.Z" commit, then `git tag vX.Y.Z && git push --follow-tags`.

The Pages workflow auto-deploys because step 2/4/5 touch `docs/**`. The release workflow auto-deploys binaries because of the tag. README is part of the GitHub repo landing page — it updates immediately on push.

## Things that are easy to get wrong

- The CLI is **Kotlin/Native**, not JVM. Don't suggest `java -jar`, `application` plugin, or `fatJar` tasks — they won't work. Linking is via `linkReleaseExecutable<Target>`, not `installDist`.
- `.claude/settings.json` and `opencode.json` produced by the CLI are for **target user projects**. Don't edit them in this repo expecting them to affect Claude Code here — this repo's own settings live in `.claude/settings.local.json` (gitignored).
- The release workflow renames artifacts (`.kexe` → no extension) before publishing. The names users download (`kit-setup-macos-arm64`, etc.) only exist on the Releases page, not in `kit-setup/build/`.
- When changing the `DEFAULT_AGENTS` list in `model/Config.kt`, the docs `Files.tsx` page and locale `files.purposes` need a matching update — they're not generated.
- i18n: every user-facing string in `docs/` lives in **both** `en.json` and `ru.json`. Adding a key to one without the other shows the raw key in the missing language.
