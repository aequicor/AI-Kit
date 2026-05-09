import { useTranslation } from 'react-i18next';
import ThemeToggle from './ThemeToggle';
import LanguageSwitcher from './LanguageSwitcher';

const REPO = 'https://github.com/aequicor/AI-Kit';

type NavItem = { href: string; key: string };

const NAV: readonly NavItem[] = [
  { href: './#what', key: 'what' },
  { href: './#problems', key: 'problems' },
  { href: './#how', key: 'how' },
  { href: './#scenarios', key: 'scenarios' },
  { href: './flows.html', key: 'flows' },
  { href: './#commands', key: 'commands' },
  { href: './#targets', key: 'targets' },
  { href: './#faq', key: 'faq' },
  { href: './#start', key: 'start' },
];

function Logo() {
  return (
    <svg viewBox="0 0 32 32" aria-hidden>
      <rect x="2" y="2" width="28" height="28" rx="7" fill="#6366f1" />
      <g stroke="#ffffff" strokeWidth="1.4" strokeLinecap="round" fill="none">
        <line x1="16" y1="10.4" x2="10.4" y2="21.6" />
        <line x1="16" y1="10.4" x2="21.6" y2="21.6" />
        <line x1="10.4" y1="21.6" x2="21.6" y2="21.6" />
      </g>
      <circle cx="16" cy="10.4" r="2.6" fill="#ffffff" />
      <circle cx="10.4" cy="21.6" r="2.6" fill="#ffffff" />
      <circle cx="21.6" cy="21.6" r="2.6" fill="#ffffff" />
    </svg>
  );
}

export default function Header() {
  const { t } = useTranslation();
  return (
    <nav className="topnav">
      <div className="nav-inner">
        <a href="./" className="nav-logo">
          <Logo />
          <span>{t('brand')}</span>
        </a>
        <ul className="nav-links">
          {NAV.map((item) => (
            <li key={item.href}>
              <a href={item.href}>{t(`nav.${item.key}`)}</a>
            </li>
          ))}
        </ul>
        <div className="nav-right">
          <LanguageSwitcher />
          <ThemeToggle />
          <a className="nav-gh" href={REPO} target="_blank" rel="noreferrer" aria-label="GitHub">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
              <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2.16c-3.2.7-3.88-1.37-3.88-1.37-.52-1.34-1.28-1.7-1.28-1.7-1.05-.71.08-.7.08-.7 1.16.08 1.78 1.2 1.78 1.2 1.03 1.77 2.7 1.26 3.36.96.11-.75.4-1.26.74-1.55-2.55-.29-5.24-1.28-5.24-5.7 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.78 0c2.21-1.5 3.18-1.18 3.18-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.84 1.19 3.1 0 4.43-2.7 5.41-5.27 5.69.41.36.78 1.06.78 2.14v3.17c0 .31.21.66.79.55C20.21 21.39 23.5 17.08 23.5 12 23.5 5.65 18.35.5 12 .5z" />
            </svg>
            <span>GitHub</span>
          </a>
        </div>
      </div>
    </nav>
  );
}
