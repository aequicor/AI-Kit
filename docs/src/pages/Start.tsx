import { useTranslation } from 'react-i18next';
import CopyPromptBlock from '../components/CopyPromptBlock';
import setupPrompt from '../../../SETUP_PROMPT.md?raw';

const REPO = 'https://github.com/aequicor/AI-Kit';

type FileNode = { path: string; note: string };
type CommandItem = { name: string; subtitle: string; body: string };
type Scenario = { title: string; body: string };
type SubCmd = { cmd: string; body: string };
type ExitRow = { code: string; body: string };

export default function Start() {
  const { t } = useTranslation();

  const steps = t('start.quickSetup.steps', { returnObjects: true }) as string[];
  const tree = t('start.files.tree', { returnObjects: true }) as FileNode[];
  const commands = t('start.commands.items', { returnObjects: true }) as CommandItem[];
  const scenarios = t('start.scenarios.items', { returnObjects: true }) as Scenario[];
  const subcommands = t('start.cli.subcommands', { returnObjects: true }) as SubCmd[];
  const exits = t('start.cli.exit', { returnObjects: true }) as ExitRow[];

  return (
    <>
      <section className="hero">
        <div className="wrap">
          <span className="hero-badge">{t('start.hero.eyebrow')}</span>
          <h1>{t('start.hero.title')}</h1>
          <p className="hero-sub">{t('start.hero.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <CopyPromptBlock
            fullText={setupPrompt}
            title={t('start.quickSetup.title')}
            subtitle={t('start.quickSetup.subtitle')}
            previewLabel={t('start.quickSetup.previewLabel')}
          />

          <h3 className="subhead">{t('start.quickSetup.stepsTitle')}</h3>
          <ol className="steps-list compact">
            {steps.map((s, i) => (
              <li className="step" key={i}>
                <div className="step-num">{i + 1}</div>
                <div className="step-body">
                  <p>{s}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('start.files.eyebrow')}</div>
          <h2 className="sec-title">{t('start.files.title')}</h2>
          <p className="sec-sub">{t('start.files.lead')}</p>
          <div className="file-tree">
            {tree.map((n, i) => (
              <div className="file-tree-row" key={i}>
                <code>{n.path}</code>
                <span className="file-tree-note">{n.note}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('start.commands.eyebrow')}</div>
          <h2 className="sec-title">{t('start.commands.title')}</h2>
          <p className="sec-sub">{t('start.commands.lead')}</p>
          <div className="card-grid card-grid-3">
            {commands.map((c) => (
              <div className="card command-card" key={c.name}>
                <code className="command-name">{c.name}</code>
                <h3>{c.subtitle}</h3>
                <p>{c.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('start.scenarios.eyebrow')}</div>
          <h2 className="sec-title">{t('start.scenarios.title')}</h2>
          <div className="scenarios-list">
            {scenarios.map((s, i) => (
              <div className="scenario-row" key={i}>
                <h3>{s.title}</h3>
                <p>{s.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('start.cli.eyebrow')}</div>
          <h2 className="sec-title">{t('start.cli.title')}</h2>
          <p className="sec-sub">{t('start.cli.lead')}</p>

          <table>
            <tbody>
              {subcommands.map((c) => (
                <tr key={c.cmd}>
                  <td style={{ width: '38%' }}><code>{c.cmd}</code></td>
                  <td>{c.body}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3 className="subhead">{t('start.cli.exitTitle')}</h3>
          <table>
            <tbody>
              {exits.map((e) => (
                <tr key={e.code}>
                  <td style={{ width: '60px' }}><code>{e.code}</code></td>
                  <td>{e.body}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <p style={{ marginTop: 24 }}>
            <a href={`${REPO}/releases`} target="_blank" rel="noreferrer">
              {t('start.cli.releasesLink')}
            </a>
          </p>
        </div>
      </section>
    </>
  );
}
