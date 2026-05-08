import { useTranslation } from 'react-i18next';

const DOWNLOADS = [
  { platform: 'Windows x64', file: 'kit-setup-windows-x64.exe' },
  { platform: 'Linux x64', file: 'kit-setup-linux-x64' },
  { platform: 'macOS Apple Silicon', file: 'kit-setup-macos-arm64' },
  { platform: 'macOS Intel', file: 'kit-setup-macos-x64' },
];

const RELEASE_BASE = 'https://github.com/aequicor/AI-Kit/releases/latest/download/';

const SUBCOMMANDS = [
  { command: 'verify [<path>]', key: 'verify' },
  { command: 'generate [<path>]', key: 'generate' },
  { command: 'schema [--format json|human]', key: 'schema' },
  { command: '--help / -h', key: 'help' },
  { command: '--version / -v', key: 'version' },
];

const EXIT_CODES = [
  { code: '0', key: 'zero' },
  { code: '1', key: 'one' },
  { code: '2', key: 'two' },
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
          <pre><code>{`kit-setup schema                       # JSON catalog of bundled variants
kit-setup schema --format human        # readable tree
kit-setup verify   .aikit/manifest.yaml
kit-setup generate .aikit/manifest.yaml
kit-setup --help
kit-setup --version`}</code></pre>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 48 }}>{t('cli.subcommands')}</h2>
          <table>
            <thead>
              <tr>
                <th>{t('cli.subcommand')}</th>
                <th>{t('cli.description')}</th>
              </tr>
            </thead>
            <tbody>
              {SUBCOMMANDS.map((s) => (
                <tr key={s.key}>
                  <td><code>{s.command}</code></td>
                  <td>{t(`cli.subcommandsList.${s.key}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 48 }}>{t('cli.exitCodes')}</h2>
          <table>
            <thead>
              <tr>
                <th>{t('cli.exit')}</th>
                <th>{t('cli.description')}</th>
              </tr>
            </thead>
            <tbody>
              {EXIT_CODES.map((e) => (
                <tr key={e.code}>
                  <td><code>{e.code}</code></td>
                  <td>{t(`cli.exitMeanings.${e.key}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
