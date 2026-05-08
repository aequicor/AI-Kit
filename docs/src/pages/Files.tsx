import { useTranslation } from 'react-i18next';

const FILES: { file: string; provider: string; purpose: string }[] = [
  { file: 'CLAUDE.md', provider: 'Claude Code', purpose: 'claudemd' },
  { file: '.claude/settings.json', provider: 'Claude Code', purpose: 'claudeSettings' },
  { file: '.claude/agents/*.md', provider: 'Claude Code', purpose: 'claudeAgents' },
  { file: 'opencode.json', provider: 'OpenCode', purpose: 'opencodeJson' },
  { file: '.opencode/agents/*.md', provider: 'OpenCode', purpose: 'opencodeAgents' },
  { file: '.planning/CURRENT.md', provider: 'both', purpose: 'current' },
  { file: '.planning/MORNING_REPORT.md', provider: 'both', purpose: 'morning' },
  { file: '.planning/tasks/', provider: 'both', purpose: 'tasks' },
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
                <th>{t('files.provider')}</th>
                <th>{t('files.purpose')}</th>
              </tr>
            </thead>
            <tbody>
              {FILES.map((f) => (
                <tr key={f.file}>
                  <td><code>{f.file}</code></td>
                  <td>{f.provider}</td>
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
