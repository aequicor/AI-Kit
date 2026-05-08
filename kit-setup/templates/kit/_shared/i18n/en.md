# Agent Language — English

**Active locale: `en`**

All agent output, status labels, task descriptions, and user-facing messages must be in **English**.

## Action Labels

| Key | Use this text |
|-----|--------------|
| `classify_and_clarify` | Classify and clarify the task |
| `write_code` | Write code |
| `review_code` | Review code |
| `fix_bug` | Fix bug |
| `debug_issue` | Debug issue |
| `qa_check` | Quality assurance check |
| `design_ui` | Design UI |
| `prompt_maintenance` | Prompt maintenance |
| `checkpoint` | Checkpoint |
| `resume_work` | Resume work |
| `new_feature` | New feature |
| `lint` | Lint |
| `fix` | Fix |
| `review` | Review |
| `update_deps` | Update dependencies |
| `deploy` | Deploy |
| `tech_debt` | Tech debt |
| `record_tech_debt` | Record tech debt |

## Domain Terms

| Term | Use this text |
|------|--------------|
| module | module |
| source root | source root |
| test root | test root |
| docs | docs |
| requirements | requirements |
| spec | spec |
| plan | plan |
| report | report |

## Status Values

Use these exact strings in `.planning/tasks/*.md` checkpoints and test-cases files:

| Status | Text |
|--------|------|
| DONE | `DONE` |
| NEXT | `NEXT` |
| BLOCKED | `BLOCKED` |
| TODO | `TODO` |
| Approved | `Approved` |
| Proposed | `Proposed` |
| In Progress | `In Progress` |

## Commands

Command names are always English regardless of locale — use the slash-command as-is:
`/kit-new-feature`, `/kit-resume`, `/kit-checkpoint`, `/kit-lint`, `/kit-fix`, `/kit-review`, `/kit-update-deps`, `/kit-deploy`, `/kit-techdebt`
