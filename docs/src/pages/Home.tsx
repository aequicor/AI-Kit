import { useTranslation } from 'react-i18next';
import InstallBox from '../components/InstallBox';

const REPO = 'https://github.com/aequicor/AI-Kit';
const PROMPT = 'Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/master/SETUP_PROMPT.md and follow the instructions.';

type Stat = { num: string; unit?: string; label: string };
type Card = { icon: string; title: string; body: string };
type QA = { q: string; a: string };
type Step = { title: string; body: string; hint?: string };
type Scenario = { title: string; body: string };
type Usecase = { title: string; open: boolean; steps: string[]; watch: string };
type CmdRow = { command: string; body: string };
type ExitRow = { code: string; body: string };
type Axis = { name: string; body: string; list: string[] };
type Target = { id: string; status: string; role: string; files: string[]; body: string };
type SupportItem = { title: string; body: string };

const MANIFEST_SAMPLE = `project: my-shop
stack:
  languages: [typescript]
  profiles: [typescript-pnpm, nextjs, security-baseline]
modules:
  - name: web
    path: apps/web
forbidden_patterns:
  - legacy/
render_targets: [claude-code, cursor]`;

const CLAUDE_SAMPLE = `# my-shop

## Conventions
- TypeScript, Next.js, pnpm.
- Don't touch \`legacy/\`.

## Commands
- pnpm install
- pnpm dev
- pnpm test

## Agents
- review-agent — runs on every PR.
- security-agent — checks new deps.`;

export default function Home() {
  const { t } = useTranslation();

  const stats = t('home.stats', { returnObjects: true }) as Stat[];
  const whatCards = t('home.what.cards', { returnObjects: true }) as Card[];
  const problems = t('home.problems.items', { returnObjects: true }) as QA[];
  const howSteps = t('home.how.steps', { returnObjects: true }) as Step[];
  const scenarios = t('home.scenarios.items', { returnObjects: true }) as Scenario[];
  const usecases = t('home.usecases.items', { returnObjects: true }) as Usecase[];
  const flows = t('home.flows.items', { returnObjects: true }) as Usecase[];
  const commands = t('home.commands.items', { returnObjects: true }) as CmdRow[];
  const exits = t('home.commands.exit', { returnObjects: true }) as ExitRow[];
  const axes = t('home.profiles.axes', { returnObjects: true }) as Axis[];
  const targets = t('home.targets.items', { returnObjects: true }) as Target[];
  const support = t('home.support.items', { returnObjects: true }) as SupportItem[];
  const faq = t('home.faq.items', { returnObjects: true }) as QA[];

  return (
    <>
      {/* Hero */}
      <section className="hero" id="top">
        <div className="wrap">
          <span className="hero-badge">{t('home.badge')}</span>
          <h1>{t('home.title')}</h1>
          <p className="hero-sub">{t('home.lead')}</p>
          <div className="hero-actions">
            <a href="#start" className="btn-primary">{t('home.ctaQuick')}</a>
            <a href={REPO} className="btn-secondary" target="_blank" rel="noreferrer">
              {t('home.ctaGithub')}
            </a>
          </div>
          <InstallBox command={PROMPT} highlightUrl="https://raw.githubusercontent.com/aequicor/AI-Kit/master/SETUP_PROMPT.md" />
        </div>
      </section>

      {/* Stats */}
      <section className="stats-section">
        <div className="wrap-wide">
          <div className="stats-row">
            {stats.map((s, i) => (
              <div className="stat" key={i}>
                <div className="stat-num">
                  {s.num}
                  {s.unit ? <span className="stat-unit"> {s.unit}</span> : null}
                </div>
                <div className="stat-label">{s.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* What it is */}
      <section id="what">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.what.eyebrow')}</div>
          <h2 className="sec-title">{t('home.what.title')}</h2>
          <p className="sec-sub">{t('home.what.lead')}</p>

          <div className="card-grid card-grid-3">
            {whatCards.map((c, i) => (
              <div className="card" key={i}>
                <div className="card-emoji" aria-hidden>{c.icon}</div>
                <h3>{c.title}</h3>
                <p>{c.body}</p>
              </div>
            ))}
          </div>

          <p className="summary-line">{t('home.what.summary')}</p>
        </div>
      </section>

      {/* Problems */}
      <section id="problems">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.problems.eyebrow')}</div>
          <h2 className="sec-title">{t('home.problems.title')}</h2>
          <p className="sec-sub">{t('home.problems.lead')}</p>

          <div className="problems-list">
            {problems.map((p, i) => (
              <details className="problem-item" key={i}>
                <summary>
                  <span className="problem-q">{p.q}</span>
                  <span className="chev" aria-hidden>+</span>
                </summary>
                <p>{p.a}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section id="how">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.how.eyebrow')}</div>
          <h2 className="sec-title">{t('home.how.title')}</h2>
          <p className="sec-sub">{t('home.how.lead')}</p>

          <ol className="steps-list">
            {howSteps.map((s, i) => (
              <li className="step" key={i}>
                <div className="step-num">{i + 1}</div>
                <div className="step-body">
                  <h3>{s.title}</h3>
                  <p>{s.body}</p>
                  {s.hint ? <span className="step-hint">{s.hint}</span> : null}
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Short scenarios */}
      <section id="scenarios">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.scenarios.eyebrow')}</div>
          <h2 className="sec-title">{t('home.scenarios.title')}</h2>
          <p className="sec-sub">{t('home.scenarios.lead')}</p>

          <div className="card-grid card-grid-3">
            {scenarios.map((s, i) => (
              <div className="card" key={i}>
                <h3>{s.title}</h3>
                <p>{s.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Detailed usecases */}
      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.usecases.eyebrow')}</div>
          <h2 className="sec-title">{t('home.usecases.title')}</h2>
          <p className="sec-sub">{t('home.usecases.lead')}</p>

          <div className="usecases-list">
            {usecases.map((u, i) => (
              <details className="usecase" key={i} open={u.open}>
                <summary>
                  <span className="usecase-title">{u.title}</span>
                  <span className="chev" aria-hidden>+</span>
                </summary>
                <ol className="usecase-steps">
                  {u.steps.map((s, j) => (
                    <li key={j}>{s}</li>
                  ))}
                </ol>
                <p className="usecase-watch">{u.watch}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* User flows (slash-command workflows) */}
      <section id="flows">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.flows.eyebrow')}</div>
          <h2 className="sec-title">{t('home.flows.title')}</h2>
          <p className="sec-sub">{t('home.flows.lead')}</p>

          <div className="usecases-list">
            {flows.map((u, i) => (
              <details className="usecase" key={i} open={u.open}>
                <summary>
                  <span className="usecase-title">{u.title}</span>
                  <span className="chev" aria-hidden>+</span>
                </summary>
                <ol className="usecase-steps">
                  {u.steps.map((s, j) => (
                    <li key={j}>{s}</li>
                  ))}
                </ol>
                <p className="usecase-watch">{u.watch}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* Commands reference */}
      <section id="commands">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.commands.eyebrow')}</div>
          <h2 className="sec-title">{t('home.commands.title')}</h2>
          <p className="sec-sub">{t('home.commands.lead')}</p>

          <table>
            <tbody>
              {commands.map((c) => (
                <tr key={c.command}>
                  <td style={{ width: '34%' }}><code>{c.command}</code></td>
                  <td>{c.body}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3 className="subhead">{t('home.commands.exitTitle')}</h3>
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
        </div>
      </section>

      {/* Profiles / customization */}
      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.profiles.eyebrow')}</div>
          <h2 className="sec-title">{t('home.profiles.title')}</h2>
          <p className="sec-sub">{t('home.profiles.lead')}</p>

          <div className="axes-list">
            {axes.map((a, i) => (
              <div className="axis" key={i}>
                <h3>{a.name}</h3>
                <p>{a.body}</p>
                <div className="axis-tags">
                  {a.list.map((tag) => (
                    <span className="tag" key={tag}>{tag}</span>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <p className="profiles-note"><code>{t('home.profiles.note')}</code></p>
        </div>
      </section>

      {/* Targets / components */}
      <section id="targets">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.targets.eyebrow')}</div>
          <h2 className="sec-title">{t('home.targets.title')}</h2>
          <p className="sec-sub">{t('home.targets.lead')}</p>

          <div className="targets-list">
            {targets.map((tgt) => (
              <div className="target-card" key={tgt.id}>
                <div className="target-head">
                  <code className="target-id">{tgt.id}</code>
                  <span className="target-status">{tgt.status}</span>
                </div>
                <h3>{tgt.role}</h3>
                <p>{tgt.body}</p>
                <div className="target-files">
                  {tgt.files.map((f) => (
                    <code key={f}>{f}</code>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Real example */}
      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.example.eyebrow')}</div>
          <h2 className="sec-title">{t('home.example.title')}</h2>
          <p className="sec-sub">{t('home.example.lead')}</p>

          <div className="example-grid">
            <div className="example-block">
              <div className="example-label">{t('home.example.manifestLabel')}</div>
              <pre><code>{MANIFEST_SAMPLE}</code></pre>
            </div>
            <div className="example-arrow" aria-hidden>↓</div>
            <div className="example-block">
              <div className="example-label">{t('home.example.generatedLabel')}</div>
              <pre><code>{CLAUDE_SAMPLE}</code></pre>
            </div>
          </div>

          <p className="example-note">{t('home.example.note')}</p>
        </div>
      </section>

      {/* Support */}
      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.support.eyebrow')}</div>
          <h2 className="sec-title">{t('home.support.title')}</h2>
          <p className="sec-sub">{t('home.support.lead')}</p>

          <div className="card-grid card-grid-3">
            {support.map((s, i) => (
              <div className="card" key={i}>
                <h3>{s.title}</h3>
                <p>{s.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section id="faq">
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.faq.eyebrow')}</div>
          <h2 className="sec-title">{t('home.faq.title')}</h2>
          <p className="sec-sub">{t('home.faq.lead')}</p>

          <div className="faq-list">
            {faq.map((item, i) => (
              <details className="faq-row" key={i}>
                <summary>
                  <span>{item.q}</span>
                  <span className="chev" aria-hidden>+</span>
                </summary>
                <p>{item.a}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="final-cta" id="start">
        <div className="wrap">
          <h2 className="cta-title">{t('home.cta.title')}</h2>
          <p className="cta-body">{t('home.cta.body')}</p>
          <InstallBox command={PROMPT} highlightUrl="https://raw.githubusercontent.com/aequicor/AI-Kit/master/SETUP_PROMPT.md" />
          <div className="hero-actions" style={{ marginTop: 28, marginBottom: 0 }}>
            <a href={REPO} className="btn-secondary" target="_blank" rel="noreferrer">
              {t('home.cta.button')}
            </a>
          </div>
        </div>
      </section>
    </>
  );
}
