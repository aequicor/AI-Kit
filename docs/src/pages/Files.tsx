import { useTranslation } from 'react-i18next';

type Row = { file: string; target: string; purpose: string };

const FILES: Row[] = [
  // claude-code
  { file: 'CLAUDE.md', target: 'claude-code', purpose: 'claudemd' },
  { file: '.claude/agents/*.md', target: 'claude-code', purpose: 'claudeAgents' },
  { file: '.claude/skills/<id>/SKILL.md', target: 'claude-code', purpose: 'claudeSkills' },
  { file: '.claude/commands/*.md', target: 'claude-code', purpose: 'claudeCommands' },
  { file: '.claude/prompts/*.md', target: 'claude-code', purpose: 'claudePrompts' },
  { file: '.claude/settings.json', target: 'claude-code', purpose: 'claudeSettings' },
  // cursor
  { file: '.cursor/rules/*.mdc', target: 'cursor', purpose: 'cursorRules' },
  { file: '.cursor/rules/_prompts/*.mdc', target: 'cursor', purpose: 'cursorPrompts' },
  { file: '.cursor/mcp.json', target: 'cursor', purpose: 'cursorMcp' },
  // opencode
  { file: 'AGENTS.md', target: 'opencode', purpose: 'agentsMd' },
  { file: '.opencode/agents/*.md', target: 'opencode', purpose: 'opencodeAgents' },
  { file: 'opencode.json', target: 'opencode', purpose: 'opencodeSettings' },
  // aider
  { file: 'CONVENTIONS.md', target: 'aider', purpose: 'aiderConventions' },
  { file: '.aider.conf.yml', target: 'aider', purpose: 'aiderConf' },
  // qwen-code
  { file: 'AGENTS.md', target: 'qwen-code', purpose: 'agentsMd' },
  { file: '.qwen/agents/*.md', target: 'qwen-code', purpose: 'opencodeAgents' },
  { file: '.qwen/settings.json', target: 'qwen-code', purpose: 'qwenSettings' },
];

export default function Files() {
  const { t } = useTranslation();
  return (
    <>
      <section className="page-head">
        <div className="wrap">
          <div className="sec-eyebrow">{t('files.eyebrow')}</div>
          <h1 className="sec-title">{t('files.title')}</h1>
          <p className="sec-sub">{t('files.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <table>
            <thead>
              <tr>
                <th>{t('files.target')}</th>
                <th>{t('files.file')}</th>
                <th>{t('files.purpose')}</th>
              </tr>
            </thead>
            <tbody>
              {FILES.map((f, i) => (
                <tr key={`${f.target}-${f.file}-${i}`}>
                  <td><code>{f.target}</code></td>
                  <td><code>{f.file}</code></td>
                  <td>{t(`files.purposes.${f.purpose}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
