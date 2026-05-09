import { useTranslation } from 'react-i18next';

type Scenario = { title: string; body: string };
type Usecase = { title: string; open: boolean; steps: string[]; watch: string };

export default function Scenarios() {
  const { t } = useTranslation();
  const scenarios = t('home.scenarios.items', { returnObjects: true }) as Scenario[];
  const usecases = t('home.usecases.items', { returnObjects: true }) as Usecase[];

  return (
    <>
      <section className="hero" id="top">
        <div className="wrap">
          <span className="hero-badge">{t('home.scenarios.eyebrow')}</span>
          <h1>{t('home.scenarios.title')}</h1>
          <p className="hero-sub">{t('home.scenarios.lead')}</p>
          <div className="hero-actions">
            <a href="./" className="btn-secondary">{t('scenariosPage.backHome')}</a>
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
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
    </>
  );
}
