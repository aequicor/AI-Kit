import { useState } from 'react';
import { useTranslation } from 'react-i18next';

type Props = {
  title?: string;
  command: string;
  highlightUrl?: string;
};

export default function InstallBox({ title, command, highlightUrl }: Props) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  const onCopy = async () => {
    try {
      await navigator.clipboard.writeText(command);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {}
  };

  let body: React.ReactNode = command;
  if (highlightUrl && command.includes(highlightUrl)) {
    const [before, after] = command.split(highlightUrl);
    body = (
      <>
        {before}
        <span className="url">{highlightUrl}</span>
        {after}
      </>
    );
  }

  return (
    <div className="install-box">
      <div className="install-header">
        <span className="install-title">{title || t('install.title')}</span>
        <button
          type="button"
          className={'copy-btn' + (copied ? ' copied' : '')}
          onClick={onCopy}
        >
          {copied ? t('install.copied') : t('install.copy')}
        </button>
      </div>
      <div className="install-body">{body}</div>
    </div>
  );
}
