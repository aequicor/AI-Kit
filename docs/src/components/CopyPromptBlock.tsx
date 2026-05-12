import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { copyText } from '../utils/clipboard';

type Props = {
  fullText: string;
  previewLines?: number;
  title: string;
  subtitle?: string;
  previewLabel?: string;
};

export default function CopyPromptBlock({
  fullText,
  previewLines = 8,
  title,
  subtitle,
  previewLabel,
}: Props) {
  const { t } = useTranslation();
  const [state, setState] = useState<'idle' | 'copied' | 'error'>('idle');

  const onCopy = async () => {
    const ok = await copyText(fullText);
    setState(ok ? 'copied' : 'error');
    setTimeout(() => setState('idle'), 2000);
  };

  const preview = fullText.split('\n').slice(0, previewLines).join('\n');

  const buttonLabel =
    state === 'copied' ? t('common.copied')
    : state === 'error' ? t('common.copyError')
    : t('common.copyFull');

  return (
    <div className="prompt-block">
      <div className="prompt-block-head">
        <div className="prompt-block-title">{title}</div>
        {subtitle ? <div className="prompt-block-subtitle">{subtitle}</div> : null}
      </div>

      <button
        type="button"
        className={'prompt-copy-btn' + (state === 'copied' ? ' is-copied' : '') + (state === 'error' ? ' is-error' : '')}
        onClick={onCopy}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          {state === 'copied' ? (
            <polyline points="20 6 9 17 4 12" />
          ) : (
            <>
              <rect x="9" y="9" width="13" height="13" rx="2" />
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
            </>
          )}
        </svg>
        <span>{buttonLabel}</span>
      </button>

      {previewLabel ? <div className="prompt-preview-label">{previewLabel}</div> : null}
      <div className="prompt-preview">
        <pre><code>{preview}</code></pre>
        <div className="prompt-preview-fade" aria-hidden />
      </div>
    </div>
  );
}
