import { useTranslation } from 'react-i18next';

export default function Build() {
  const { t } = useTranslation();
  return (
    <>
      <section className="page-head">
        <div className="wrap">
          <div className="sec-eyebrow">{t('build.eyebrow')}</div>
          <h1 className="sec-title">{t('build.title')}</h1>
          <p className="sec-sub">{t('build.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <h2 className="sec-title" style={{ fontSize: '1.4rem' }}>{t('build.clone')}</h2>
          <pre><code>{`git clone https://github.com/aequicor/AI-Kit.git
cd AI-Kit/kit-setup`}</code></pre>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 36 }}>{t('build.compile')}</h2>

          <h3 style={{ marginTop: 24, color: 'var(--text)' }}>{t('build.windows')}</h3>
          <pre><code>gradlew.bat mingwX64Binaries</code></pre>

          <h3 style={{ marginTop: 24, color: 'var(--text)' }}>{t('build.linux')}</h3>
          <pre><code>./gradlew linuxX64Binaries</code></pre>

          <h3 style={{ marginTop: 24, color: 'var(--text)' }}>{t('build.macosArm')}</h3>
          <pre><code>./gradlew macosArm64Binaries</code></pre>

          <h3 style={{ marginTop: 24, color: 'var(--text)' }}>{t('build.macosIntel')}</h3>
          <pre><code>./gradlew macosX64Binaries</code></pre>
        </div>
      </section>
    </>
  );
}
