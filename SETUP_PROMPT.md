# AI Kit — Setup Instructions

You are helping the user configure AI agent tooling (Claude Code / OpenCode) for their project.
Follow the steps below exactly.

---

## Step 1 — Collect project information

Ask the user the following questions one by one. Use their answers to build the CLI flags in Step 3.

| # | Question | CLI flag | Default |
|---|----------|----------|---------|
| 1 | What is the project name? | `--name` | inferred from directory |
| 2 | What is the root path of the project? (`.` = current dir) | `--path` | `.` |
| 3 | What is the primary language? (`kotlin` / `java` / `python` / `typescript` / `go` / `rust` / `other`) | `--lang` | `kotlin` |
| 4 | What framework are you using, if any? (e.g. `spring`, `ktor`, `react`, `fastapi` — or skip) | `--framework` | _(omit flag)_ |
| 5 | Which AI provider? (`claude` / `opencode` / `both`) | `--provider` | `both` |
| 6 | Which model? (press Enter to use `claude-sonnet-4-6`) | `--model` | `claude-sonnet-4-6` |
| 7 | Enable planning system? (`.planning/` directory) `yes` / `no` | `--no-planning` if no | enabled |
| 8 | Enable agent definitions? `yes` / `no` | `--no-agents` if no | enabled |

---

## Step 2 — Detect OS and download the binary

Detect the operating system and CPU architecture, then download the matching binary from
`https://github.com/aequicor/AI-Kit/releases/latest/download/`.

### Windows
```powershell
Invoke-WebRequest -Uri "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-windows-x64.exe" -OutFile "kit-setup.exe"
$binary = ".\kit-setup.exe"
```

### macOS — Apple Silicon (arm64)
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-macos-arm64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

### macOS — Intel (x86_64)
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-macos-x64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

### Linux
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-linux-x64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

To detect macOS architecture run: `uname -m` (returns `arm64` or `x86_64`).

---

## Step 3 — Run the binary

Assemble the flags from Step 1 and run the binary. Example:

```bash
./kit-setup --name my-app --path . --lang kotlin --framework ktor --provider both --model claude-sonnet-4-6
```

Flags reference:
- `--name <name>` — project name
- `--path <path>` — target directory
- `--lang <language>` — programming language
- `--framework <name>` — framework (omit if none)
- `--provider <claude|opencode|both>`
- `--model <model-id>`
- `--no-planning` — skip `.planning/` generation
- `--no-agents` — skip agent definition files

---

## Step 4 — Confirm results

After the binary runs, list the generated files and confirm with the user that the setup looks correct.
If the user wants to change any settings, adjust the flags and re-run the binary — it overwrites existing files safely.
