import { useTranslation } from 'react-i18next';

type FileRow = { path: string; purpose: string };
type Feature = { title: string; body: string };

export default function Claude() {
  const { t } = useTranslation();

  const files = t('claude.files.rows', { returnObjects: true }) as FileRow[];
  const features = t('claude.features.items', { returnObjects: true }) as Feature[];

  return (
    <>
      <section className="hero">
        <div className="wrap">
          <span className="hero-badge">{t('claude.hero.eyebrow')}</span>
          <h1>{t('claude.hero.title')}</h1>
          <p className="hero-sub">{t('claude.hero.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('claude.files.eyebrow')}</div>
          <h2 className="sec-title">{t('claude.files.title')}</h2>
          <p className="sec-sub">{t('claude.files.lead')}</p>

          <table className="files-table">
            <tbody>
              {files.map((f) => (
                <tr key={f.path}>
                  <td style={{ width: '34%' }}><code>{f.path}</code></td>
                  <td>{f.purpose}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('claude.features.eyebrow')}</div>
          <h2 className="sec-title">{t('claude.features.title')}</h2>
          <div className="card-grid card-grid-2">
            {features.map((f, i) => (
              <div className="card" key={i}>
                <h3>{f.title}</h3>
                <p>{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('claude.dialect.eyebrow')}</div>
          <h2 className="sec-title">{t('claude.dialect.title')}</h2>
          <p className="sec-sub">{t('claude.dialect.lead')}</p>
        </div>
      </section>
    </>
  );
}
