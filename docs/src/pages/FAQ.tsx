import { useTranslation } from 'react-i18next';

export default function FAQ() {
  const { t } = useTranslation();
  const items = t('faq.items', { returnObjects: true }) as { q: string; a: string }[];
  return (
    <>
      <section className="page-head">
        <div className="wrap">
          <div className="sec-eyebrow">{t('faq.eyebrow')}</div>
          <h1 className="sec-title">{t('faq.title')}</h1>
          <p className="sec-sub">{t('faq.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          {items.map((item, i) => (
            <div key={i} className="faq-item">
              <h3>{item.q}</h3>
              <p>{item.a}</p>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
