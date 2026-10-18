import { useQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { setThemePreference, useThemePreference, type ThemePreference } from '../../app/theme';
import { usePageTitle } from '../../app/title';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { TableScroll } from '../../components/ui/TableScroll';
import { messages } from '../../messages';
import { getProviders, importKeys, type Providers } from '../import/api';

const THEMES: ThemePreference[] = ['system', 'light', 'dark'];

export function SystemPage() {
  const text = messages.system;
  usePageTitle([messages.pages.system]);
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });

  return (
    <section className="stack">
      <PageHeader title={messages.pages.system} description={text.detail} />
      <Card as="section" className="stack" aria-labelledby="providers-title">
        <h2 id="providers-title">{text.providersTitle}</h2>
        <p className="muted">{text.providersDetail}</p>
        {providers.isPending && <Loading />}
        {providers.isError && (
          <ProblemAlert error={providers.error} onRetry={() => void providers.refetch()} />
        )}
        {providers.isSuccess && <ProviderTable providers={providers.data} />}
      </Card>
      <Card as="section" className="stack" aria-labelledby="privacy-title">
        <h2 id="privacy-title">{text.privacyTitle}</h2>
        <p>{text.privacySummary}</p>
        <ul>
          <li>{text.neverExecuted}</li>
          {providers.isSuccess && <PrivacyItems providers={providers.data} />}
        </ul>
      </Card>
      <Card as="section" className="stack" aria-labelledby="data-title">
        <h2 id="data-title">{text.dataTitle}</h2>
        <ul>
          {text.data.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </Card>
      <Card as="section" className="stack" aria-labelledby="appearance-title">
        <h2 id="appearance-title">{text.appearanceTitle}</h2>
        <ThemeChoice />
      </Card>
    </section>
  );
}

function ProviderTable({ providers }: { providers: Providers }) {
  const text = messages.system;
  const rows = [
    {
      label: text.embeddings,
      provider: providers.embeddings.provider,
      model: providers.embeddings.model,
      detail: providers.embeddings.dimensions
        ? text.dimensions(providers.embeddings.dimensions)
        : null,
      remote: providers.embeddings.remote,
    },
    {
      label: text.generation,
      provider: providers.generation.provider,
      model: providers.generation.model,
      detail: null,
      remote: providers.generation.remote,
    },
  ];
  return (
    <TableScroll labelledBy="providers-title">
      <table className="data-table">
        <thead>
          <tr>
            <td />
            <th scope="col">{text.providerColumn}</th>
            <th scope="col">{text.modelColumn}</th>
            <th scope="col">{text.whereColumn}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const configured = row.provider !== 'none';
            return (
              <tr key={row.label}>
                <th scope="row">{row.label}</th>
                <td>{configured ? row.provider : text.notConfigured}</td>
                <td>
                  {configured && row.model}
                  {configured && row.detail && <span className="muted"> · {row.detail}</span>}
                </td>
                <td>
                  {configured && (
                    <StatusBadge tone={row.remote ? 'warning' : 'success'}>
                      {row.remote ? text.remote : text.local}
                    </StatusBadge>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </TableScroll>
  );
}

function PrivacyItems({ providers }: { providers: Providers }) {
  const { embeddings, generation } = providers;
  const privacy = messages.privacy;
  const ask = messages.ask.privacy;
  const indexing =
    embeddings.provider === 'none'
      ? privacy.noEmbeddings
      : embeddings.remote
        ? privacy.remote(embeddings.provider, embeddings.model)
        : privacy.localOnly;
  const questions =
    generation.provider === 'none'
      ? messages.system.noGeneration
      : generation.remote
        ? ask.remote(generation.provider, generation.model)
        : messages.system.questionsLocal;
  return (
    <>
      <li>{indexing}</li>
      <li>{questions}</li>
      {embeddings.provider !== 'none' && embeddings.remote && (
        <li>{ask.remoteEmbeddings(embeddings.provider)}</li>
      )}
    </>
  );
}

function ThemeChoice() {
  const name = useId();
  const preference = useThemePreference();
  return (
    <fieldset className="choice">
      <legend>{messages.system.themeLegend}</legend>
      {THEMES.map((theme) => (
        <label key={theme} className="choice__option">
          <input
            type="radio"
            name={name}
            value={theme}
            checked={preference === theme}
            onChange={() => setThemePreference(theme)}
          />
          {messages.theme.options[theme]}
        </label>
      ))}
    </fieldset>
  );
}
