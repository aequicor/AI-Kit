import { useTranslation } from 'react-i18next';
import InstallBox from '../components/InstallBox';

const PROMPT = 'Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/main/SETUP_PROMPT.md and follow the instructions.';

export default function QuickStart() {
  const { t } = useTranslation();
  return (
    <>
      <section className="page-head">
        <div className="wrap">
          <div className="sec-eyebrow">{t('quickstart.eyebrow')}</div>
          <h1 className="sec-title">{t('quickstart.title')}</h1>
          <p className="sec-sub">{t('quickstart.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <h2 className="sec-title" style={{ fontSize: '1.25rem' }}>{t('quickstart.step1Title')}</h2>
          <p>{t('quickstart.step1')}</p>

          <h2 className="sec-title" style={{ fontSize: '1.25rem', marginTop: 36 }}>{t('quickstart.step2Title')}</h2>
          <p>{t('quickstart.step2')}</p>
          <InstallBox
            command={PROMPT}
            highlightUrl="https://raw.githubusercontent.com/aequicor/AI-Kit/main/SETUP_PROMPT.md"
          />

          <h2 className="sec-title" style={{ fontSize: '1.25rem', marginTop: 36 }}>{t('quickstart.step3Title')}</h2>
          <p>{t('quickstart.step3')}</p>

          <div className="callout">{t('quickstart.tip')}</div>
        </div>
      </section>
    </>
  );
}
