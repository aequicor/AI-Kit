import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const REPO = 'https://github.com/aequicor/AI-Kit';

type IconCard = { icon: string; title: string; body: string };
type StepCard = { num: string; title: string; body: string };
type RunnerItem = { id: string; name: string; note: string };

export default function Home() {
  const { t } = useTranslation();

  const problems = t('home.problem.items', { returnObjects: true }) as IconCard[];
  const solutions = t('home.solution.items', { returnObjects: true }) as IconCard[];
  const howSteps = t('home.how.steps', { returnObjects: true }) as StepCard[];
  const runners = t('home.runners.items', { returnObjects: true }) as RunnerItem[];

  return (
    <>
      <section className="hero">
        <div className="wrap">
          <span className="hero-badge">{t('home.hero.badge')}</span>
          <h1>{t('home.hero.title')}</h1>
          <p className="hero-sub">{t('home.hero.lead')}</p>
          <div className="hero-actions">
            <Link to="/start" className="btn-primary">{t('home.hero.ctaStart')}</Link>
            <a href={REPO} className="btn-secondary" target="_blank" rel="noreferrer">
              {t('home.hero.ctaGithub')}
            </a>
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.problem.eyebrow')}</div>
          <h2 className="sec-title">{t('home.problem.title')}</h2>
          <div className="card-grid card-grid-3">
            {problems.map((c, i) => (
              <div className="card" key={i}>
                <div className="card-emoji" aria-hidden>{c.icon}</div>
                <h3>{c.title}</h3>
                <p>{c.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.solution.eyebrow')}</div>
          <h2 className="sec-title">{t('home.solution.title')}</h2>
          <p className="sec-sub">{t('home.solution.lead')}</p>
          <div className="card-grid card-grid-3">
            {solutions.map((c, i) => (
              <div className="card" key={i}>
                <div className="card-emoji" aria-hidden>{c.icon}</div>
                <h3>{c.title}</h3>
                <p>{c.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.how.eyebrow')}</div>
          <h2 className="sec-title">{t('home.how.title')}</h2>
          <ol className="steps-list">
            {howSteps.map((s, i) => (
              <li className="step" key={i}>
                <div className="step-num">{s.num}</div>
                <div className="step-body">
                  <h3>{s.title}</h3>
                  <p>{s.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.runners.eyebrow')}</div>
          <h2 className="sec-title">{t('home.runners.title')}</h2>
          <p className="sec-sub">{t('home.runners.lead')}</p>
          <div className="runners-grid">
            {runners.map((r) => (
              <div className="runner-pill" key={r.id}>
                <span className="runner-name">{r.name}</span>
                <span className="runner-note">{r.note}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="final-cta">
        <div className="wrap">
          <h2 className="cta-title">{t('home.cta.title')}</h2>
          <p className="cta-body">{t('home.cta.body')}</p>
          <div className="hero-actions" style={{ marginTop: 28, marginBottom: 0 }}>
            <Link to="/start" className="btn-primary">{t('home.cta.button')}</Link>
          </div>
        </div>
      </section>
    </>
  );
}
