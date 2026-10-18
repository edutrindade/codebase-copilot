import { useQuery } from '@tanstack/react-query';
import { Cpu, Database, Keyboard, Palette, ShieldCheck, type LucideIcon } from 'lucide-react';
import { useId } from 'react';
import { setSingleKeyShortcuts, useSingleKeyShortcuts } from '../../app/shortcuts';
import { setThemePreference, useThemePreference, type ThemePreference } from '../../app/theme';
import { usePageTitle } from '../../app/title';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ShortcutList } from '../../components/shortcuts/ShortcutList';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { TableScroll } from '../../components/ui/TableScroll';
import { messages } from '../../messages';
import { getProviders, importKeys, type Providers } from '../import/api';
import './SystemPage.css';

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
      <div className="system__grid">
        <Card as="section" className="stack system__wide" aria-labelledby="providers-title">
          <CardTitle id="providers-title" icon={Cpu}>
            {text.providersTitle}
          </CardTitle>
          <p className="muted">{text.providersDetail}</p>
          {providers.isPending && <Loading />}
          {providers.isError && (
            <ProblemAlert error={providers.error} onRetry={() => void providers.refetch()} />
          )}
          {providers.isSuccess && <ProviderTable providers={providers.data} />}
        </Card>
        <Card as="section" className="stack" aria-labelledby="privacy-title">
          <CardTitle id="privacy-title" icon={ShieldCheck}>
            {text.privacyTitle}
          </CardTitle>
          <p>{text.privacySummary}</p>
          <ul>
            <li>{text.neverExecuted}</li>
            {providers.isSuccess && <PrivacyItems providers={providers.data} />}
          </ul>
        </Card>
        <Card as="section" className="stack" aria-labelledby="data-title">
          <CardTitle id="data-title" icon={Database}>
            {text.dataTitle}
          </CardTitle>
          <ul>
            {text.data.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </Card>
        <Card as="section" className="stack" aria-labelledby="appearance-title">
          <CardTitle id="appearance-title" icon={Palette}>
            {text.appearanceTitle}
          </CardTitle>
          <ThemeChoice />
        </Card>
        <Card as="section" className="stack system__wide" aria-labelledby="shortcuts-title">
          <CardTitle id="shortcuts-title" icon={Keyboard}>
            {messages.shortcuts.title}
          </CardTitle>
          <SingleKeyToggle />
          <ShortcutList />
          <p className="muted">{messages.shortcuts.detail}</p>
        </Card>
      </div>
    </section>
  );
}

function CardTitle({
  id,
  icon: Icon,
  children,
}: {
  id: string;
  icon: LucideIcon;
  children: string;
}) {
  return (
    <h2 id={id} className="system__title">
      <Icon aria-hidden="true" className="system__icon" />
      {children}
    </h2>
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

function SingleKeyToggle() {
  const hint = useId();
  const enabled = useSingleKeyShortcuts();
  const text = messages.shortcuts;
  return (
    <div>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={enabled}
          aria-describedby={hint}
          onChange={(event) => setSingleKeyShortcuts(event.target.checked)}
        />
        {text.singleKey}
      </label>
      <p id={hint} className="field__hint muted">
        {text.singleKeyHint}
      </p>
    </div>
  );
}
