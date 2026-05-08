import { useTranslation } from 'react-i18next';

const LANGS = [
  { code: 'en', label: 'EN' },
  { code: 'ru', label: 'RU' },
];

export default function LanguageSwitcher() {
  const { i18n, t } = useTranslation();
  const current = (i18n.resolvedLanguage || i18n.language || 'en').split('-')[0];

  return (
    <div className="lang-toggle" role="group" aria-label={t('lang.label')}>
      {LANGS.map((l) => (
        <button
          key={l.code}
          type="button"
          className={current === l.code ? 'active' : undefined}
          aria-pressed={current === l.code}
          onClick={() => i18n.changeLanguage(l.code)}
        >
          {l.label}
        </button>
      ))}
    </div>
  );
}
