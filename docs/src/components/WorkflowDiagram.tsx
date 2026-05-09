import { useTranslation } from 'react-i18next';

const AGENTS = [
  { id: 'claude-code', label: 'Claude Code', emoji: '🤖' },
  { id: 'cursor', label: 'Cursor', emoji: '✏️' },
  { id: 'opencode', label: 'OpenCode', emoji: '🧩' },
  { id: 'aider', label: 'Aider', emoji: '🛠' },
  { id: 'qwen-code', label: 'Qwen Code', emoji: '🌀' },
];

const PROCESS_LABELS_EN = [
  '/kit-prepare',
  '/kit-new-feature',
  '/kit-fix',
  'approval points',
  'verifiable evidence',
];

const PROCESS_LABELS_RU = [
  '/kit-prepare',
  '/kit-new-feature',
  '/kit-fix',
  'точки согласования',
  'проверяемые артефакты',
];

export default function WorkflowDiagram() {
  const { i18n } = useTranslation();
  const labels = i18n.language?.startsWith('ru') ? PROCESS_LABELS_RU : PROCESS_LABELS_EN;
  const hubLabel = i18n.language?.startsWith('ru') ? 'Один процесс' : 'One process';

  return (
    <div className="workflow-diagram">
      <div className="workflow-process">
        <div className="workflow-hub">
          <span className="workflow-hub-icon" aria-hidden>⚙️</span>
          <span className="workflow-hub-label">{hubLabel}</span>
        </div>
        <ul className="workflow-process-list">
          {labels.map((l) => (
            <li key={l}><code>{l}</code></li>
          ))}
        </ul>
      </div>
      <div className="workflow-arrow" aria-hidden>
        <svg width="48" height="60" viewBox="0 0 48 60" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
          <path d="M6 30h32" />
          <path d="M30 18l10 12-10 12" />
        </svg>
      </div>
      <div className="workflow-agents" role="list">
        {AGENTS.map((a, i) => (
          <div className="workflow-agent" role="listitem" key={a.id} style={{ animationDelay: `${i * 120}ms` }}>
            <span className="workflow-agent-emoji" aria-hidden>{a.emoji}</span>
            <span className="workflow-agent-label">{a.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
