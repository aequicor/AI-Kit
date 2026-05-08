import { useTranslation } from 'react-i18next';

const REPO = 'https://github.com/aequicor/AI-Kit';

export default function Footer() {
  const { t } = useTranslation();
  return (
    <footer className="footer">
      <a href={REPO} target="_blank" rel="noreferrer">{t('footer.repo')}</a>
      <span className="footer-sep">·</span>
      <a href={`${REPO}/releases`} target="_blank" rel="noreferrer">{t('footer.releases')}</a>
      <span className="footer-sep">·</span>
      <a href={`${REPO}/blob/master/LICENSE`} target="_blank" rel="noreferrer">{t('footer.license')}</a>
    </footer>
  );
}
