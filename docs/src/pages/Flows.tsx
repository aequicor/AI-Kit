import { useTranslation } from 'react-i18next';

type Flow = { title: string; open: boolean; steps: string[]; watch: string };

export default function Flows() {
  const { t } = useTranslation();
  const flows = t('home.flows.items', { returnObjects: true }) as Flow[];

  return (
    <>
      {/* Hero */}
      <section className="hero" id="top">
        <div className="wrap">
          <span className="hero-badge">{t('home.flows.eyebrow')}</span>
          <h1>{t('home.flows.title')}</h1>
          <p className="hero-sub">{t('home.flows.lead')}</p>
          <div className="hero-actions">
            <a href="./" className="btn-secondary">{t('flows.backHome')}</a>
          </div>
        </div>
      </section>

      {/* Workflows list */}
      <section>
        <div className="wrap">
          <div className="usecases-list">
            {flows.map((f, i) => (
              <details className="usecase" key={i} open={f.open}>
                <summary>
                  <span className="usecase-title">{f.title}</span>
                  <span className="chev" aria-hidden>+</span>
                </summary>
                <ol className="usecase-steps">
                  {f.steps.map((s, j) => (
                    <li key={j}>{s}</li>
                  ))}
                </ol>
                <p className="usecase-watch">{f.watch}</p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
