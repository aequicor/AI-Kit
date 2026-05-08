import { useTranslation } from 'react-i18next';

const DOWNLOADS = [
  { platform: 'Windows x64', file: 'kit-setup-windows-x64.exe' },
  { platform: 'Linux x64', file: 'kit-setup-linux-x64' },
  { platform: 'macOS Apple Silicon', file: 'kit-setup-macos-arm64' },
  { platform: 'macOS Intel', file: 'kit-setup-macos-x64' },
];

const RELEASE_BASE = 'https://github.com/aequicor/AI-Kit/releases/latest/download/';

const SUBCOMMANDS: { name: string; key: 'verify' | 'generate' | 'help' | 'version' }[] = [
  { name: 'kit-setup verify [<manifest-path>]', key: 'verify' },
  { name: 'kit-setup generate [<manifest-path>]', key: 'generate' },
  { name: 'kit-setup --help | -h', key: 'help' },
  { name: 'kit-setup --version | -v', key: 'version' },
];

const EXIT_CODES: { code: string; key: 'ok' | 'invalid' | 'error' }[] = [
  { code: '0', key: 'ok' },
  { code: '1', key: 'invalid' },
  { code: '2', key: 'error' },
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
          <pre><code>{`# Validate a manifest (defaults to .aikit/manifest.yaml)
kit-setup verify

# Generate the kit from a validated manifest
kit-setup generate .aikit/manifest.yaml

# JSON output is single-line; pipe through jq if you like
kit-setup verify | jq .

kit-setup --help
kit-setup --version`}</code></pre>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 48 }}>{t('cli.subcommands')}</h2>
          <table>
            <thead>
              <tr>
                <th>{t('cli.subcommand')}</th>
                <th>{t('cli.description')}</th>
                <th>{t('cli.output')}</th>
              </tr>
            </thead>
            <tbody>
              {SUBCOMMANDS.map((s) => (
                <tr key={s.key}>
                  <td><code>{s.name}</code></td>
                  <td>{t(`cli.subcommandsList.${s.key}.description`)}</td>
                  <td><code>{t(`cli.subcommandsList.${s.key}.output`)}</code></td>
                </tr>
              ))}
            </tbody>
          </table>

          <p style={{ marginTop: 24 }}>{t('cli.manifestNote')}</p>

          <h2 className="sec-title" style={{ fontSize: '1.4rem', marginTop: 48 }}>{t('cli.exitCodesTitle')}</h2>
          <table>
            <thead>
              <tr>
                <th>{t('cli.exitCode')}</th>
                <th>{t('cli.meaning')}</th>
              </tr>
            </thead>
            <tbody>
              {EXIT_CODES.map((e) => (
                <tr key={e.code}>
                  <td><code>{e.code}</code></td>
                  <td>{t(`cli.exitCodes.${e.key}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
