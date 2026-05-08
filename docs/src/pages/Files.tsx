import { useTranslation } from 'react-i18next';

type Scope = 'Claude Code' | 'OpenCode' | 'both';

const FILES: { file: string; scope: Scope; purpose: string }[] = [
  { file: 'CLAUDE.md', scope: 'Claude Code', purpose: 'claudemd' },
  { file: 'AGENTS.md', scope: 'OpenCode', purpose: 'agentsmd' },
  { file: '.claude/settings.json', scope: 'Claude Code', purpose: 'claudeSettings' },
  { file: 'opencode.json', scope: 'OpenCode', purpose: 'opencodeJson' },
  { file: '.mcp.json', scope: 'Claude Code', purpose: 'mcpJson' },
  { file: '.claude/agents/*.md', scope: 'Claude Code', purpose: 'claudeAgents' },
  { file: '.opencode/agents/*.md', scope: 'OpenCode', purpose: 'opencodeAgents' },
  { file: '<host>/commands/kit-*.md', scope: 'both', purpose: 'commands' },
  { file: '<host>/skills/<name>/SKILL.md', scope: 'both', purpose: 'skills' },
  { file: '<host>/_shared.md, FILE_STRUCTURE.md, sessions/, i18n/', scope: 'both', purpose: 'shared' },
  { file: '.planning/CURRENT.md', scope: 'both', purpose: 'current' },
  { file: '.planning/DECISIONS.md', scope: 'both', purpose: 'decisions' },
  { file: '.planning/tasks/TASK.md.template', scope: 'both', purpose: 'tasks' },
  { file: '<vault_path>/{concepts,reference,how-to,tutorials,guidelines}/', scope: 'both', purpose: 'vault' },
  { file: '<source_root>/AGENTS.md or CLAUDE.md', scope: 'both', purpose: 'moduleAgents' },
  { file: 'AUTO_MEMORY.md', scope: 'both', purpose: 'autoMemory' },
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
                <th>{t('files.file')}</th>
                <th>{t('files.scope')}</th>
                <th>{t('files.purpose')}</th>
              </tr>
            </thead>
            <tbody>
              {FILES.map((f) => (
                <tr key={f.file}>
                  <td><code>{f.file}</code></td>
                  <td>{f.scope}</td>
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
