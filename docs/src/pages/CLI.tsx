import { useTranslation } from 'react-i18next';

const DOWNLOADS = [
  { platform: 'Windows x64', file: 'kit-setup-windows-x64.exe' },
  { platform: 'Linux x64', file: 'kit-setup-linux-x64' },
  { platform: 'macOS Apple Silicon', file: 'kit-setup-macos-arm64' },
  { platform: 'macOS Intel', file: 'kit-setup-macos-x64' },
];

const RELEASE_BASE = 'https://github.com/aequicor/AI-Kit/releases/latest/download/';

const FLAGS = [
  { flag: '--name <name>', desc: 'name', def: 'name' },
  { flag: '--path <path>', desc: 'path', def: 'path' },
  { flag: '--lang <language>', desc: 'lang', def: 'lang' },
  { flag: '--framework <name>', desc: 'framework', def: 'framework' },
  { flag: '--provider <claude|opencode|both>', desc: 'provider', def: 'provider' },
  { flag: '--model <model-id>', desc: 'model', def: 'model' },
  { flag: '--no-planning', desc: 'noPlanning', def: 'noPlanning' },
  { flag: '--no-agents', desc: 'noAgents', def: 'noAgents' },
];

export default function CLI() {
  const { t } = useTranslation();
  return (
    <>
      <section className="page-head">
        <div className="wrap">
          <div className="sec-eyebrow">{t('cli.eyebrow')}</div>
          <h1 className="sec-title">{t('cli.title')}</h1>
          <p className="sec-sub">{t('cli.lead')}</p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <h2 className="sec-title" style={{ fontSize: '1.4rem' }}>{t('cli.downloads')}</h2>
          <table>
            <thead>
              <tr>
                <th>{t('cli.platform')}</th>
                <th>{t('cli.binary')}</th>
              </tr>
            </thead>
            <tbody>
              {DOWNLOADS.map((d) => (
                <tr key={d.file}>
                  <td>{d.platform}</td>
                  <td>
                    <a href={`${RELEASE_BASE}${d.file}`} target="_blank" rel="noreferrer">
                      <code>{d.file}</code>
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 48 }}>{t('cli.examples')}</h2>
          <pre><code>{`kit-setup --name my-app --path . --lang kotlin --provider both
kit-setup -p . -l typescript -f react --provider claude
kit-setup --help`}</code></pre>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 48 }}>{t('cli.flags')}</h2>
          <table>
            <thead>
              <tr>
                <th>{t('cli.flag')}</th>
                <th>{t('cli.description')}</th>
                <th>{t('cli.default')}</th>
              </tr>
            </thead>
            <tbody>
              {FLAGS.map((f) => (
                <tr key={f.flag}>
                  <td><code>{f.flag}</code></td>
                  <td>{t(`cli.flagsList.${f.desc}`)}</td>
                  <td>{t(`cli.defaults.${f.def}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
