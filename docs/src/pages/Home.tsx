import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import InstallBox from '../components/InstallBox';

const REPO = 'https://github.com/aequicor/AI-Kit';
const PROMPT = 'Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/main/SETUP_PROMPT.md and follow the instructions.';

export default function Home() {
  const { t } = useTranslation();
  return (
    <>
      <section className="hero">
        <div className="wrap">
          <span className="hero-badge">{t('home.badge')}</span>
          <h1>{t('home.title')}</h1>
          <p className="hero-sub">{t('home.lead')}</p>
          <div className="hero-actions">
            <Link to="/quickstart" className="btn-primary">{t('home.ctaQuick')}</Link>
            <a href={REPO} className="btn-secondary" target="_blank" rel="noreferrer">
              {t('home.ctaGithub')}
            </a>
          </div>
          <InstallBox command={PROMPT} highlightUrl="https://raw.githubusercontent.com/aequicor/AI-Kit/main/SETUP_PROMPT.md" />
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.what.eyebrow')}</div>
          <h2 className="sec-title">{t('home.what.title')}</h2>
          <p className="sec-sub">{t('home.what.sub')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.features.eyebrow')}</div>
          <h2 className="sec-title">{t('home.features.title')}</h2>

          <div className="card-grid">
            <div className="card">
              <div className="card-icon">⚡</div>
              <h3>{t('home.features.providers.title')}</h3>
              <p>{t('home.features.providers.body')}</p>
            </div>
            <div className="card">
              <div className="card-icon">⚙</div>
              <h3>{t('home.features.languages.title')}</h3>
              <p>{t('home.features.languages.body')}</p>
            </div>
            <div className="card">
              <div className="card-icon">📋</div>
              <h3>{t('home.features.planning.title')}</h3>
              <p>{t('home.features.planning.body')}</p>
            </div>
            <div className="card">
              <div className="card-icon">🤖</div>
              <h3>{t('home.features.agents.title')}</h3>
              <p>{t('home.features.agents.body')}</p>
            </div>
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('home.next.eyebrow')}</div>
          <h2 className="sec-title">{t('home.next.title')}</h2>

          <div className="card-grid">
            <Link to="/quickstart" className="card" style={{ textDecoration: 'none' }}>
              <h3>{t('home.next.quickstart.title')} →</h3>
              <p>{t('home.next.quickstart.body')}</p>
            </Link>
            <Link to="/cli" className="card" style={{ textDecoration: 'none' }}>
              <h3>{t('home.next.cli.title')} →</h3>
              <p>{t('home.next.cli.body')}</p>
            </Link>
            <Link to="/files" className="card" style={{ textDecoration: 'none' }}>
              <h3>{t('home.next.files.title')} →</h3>
              <p>{t('home.next.files.body')}</p>
            </Link>
            <Link to="/build" className="card" style={{ textDecoration: 'none' }}>
              <h3>{t('home.next.build.title')} →</h3>
              <p>{t('home.next.build.body')}</p>
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
