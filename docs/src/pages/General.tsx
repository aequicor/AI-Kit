import { useTranslation } from 'react-i18next';

type FileRow = { path: string; purpose: string };
type OtherItem = { name: string; body: string };

export default function General() {
  const { t } = useTranslation();

  const opencodeRows = t('general.opencode.rows', { returnObjects: true }) as FileRow[];
  const qwenRows = t('general.qwen.rows', { returnObjects: true }) as FileRow[];
  const others = t('general.others.items', { returnObjects: true }) as OtherItem[];

  const renderRows = (rows: FileRow[]) => (
    <table className="files-table">
      <tbody>
        {rows.map((f) => (
          <tr key={f.path}>
            <td style={{ width: '34%' }}><code>{f.path}</code></td>
            <td>{f.purpose}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );

  return (
    <>
      <section className="hero">
        <div className="wrap">
          <span className="hero-badge">{t('general.hero.eyebrow')}</span>
          <h1>{t('general.hero.title')}</h1>
          <p className="hero-sub">{t('general.hero.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <h2 className="sec-title">{t('general.opencode.title')}</h2>
          <p className="sec-sub">{t('general.opencode.lead')}</p>
          {renderRows(opencodeRows)}
        </div>
      </section>

      <section>
        <div className="wrap">
          <h2 className="sec-title">{t('general.qwen.title')}</h2>
          <p className="sec-sub">{t('general.qwen.lead')}</p>
          {renderRows(qwenRows)}
        </div>
      </section>

      <section>
        <div className="wrap">
          <h2 className="sec-title">{t('general.others.title')}</h2>
          <div className="card-grid card-grid-2">
            {others.map((o) => (
              <div className="card" key={o.name}>
                <h3>{o.name}</h3>
                <p>{o.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="sec-eyebrow">{t('general.dialect.eyebrow')}</div>
          <h2 className="sec-title">{t('general.dialect.title')}</h2>
          <p className="sec-sub">{t('general.dialect.lead')}</p>
        </div>
      </section>
    </>
  );
}
