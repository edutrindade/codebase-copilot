import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useNavigate } from 'react-router';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { messages } from '../../messages';
import { workspaceKeys } from '../workspaces/api';
import { getProviders, importKeys, startIndexing, type Inventory, type Providers } from './api';
import { acknowledgePrivacy, hasAcknowledgedPrivacy } from './privacy';
import { SETTINGS_SECTION_ID } from './SettingsForm';

// The index job would fail with these same codes, so the button is blocked up front.
const BLOCKING_WARNINGS = new Set([
  'NO_INCLUDED_FILES',
  'PROJECT_SIZE_LIMIT_EXCEEDED',
  'SCAN_LIMIT_REACHED',
]);

const PROVIDER_NAMES: Record<Providers['embeddings']['provider'], string> = {
  openai: 'OpenAI',
  fake: 'fake',
  none: 'none',
};

interface IndexPanelProps {
  projectId: string;
  inventory: Inventory | undefined;
  settingsDirty: boolean;
}

export function IndexPanel({ projectId, inventory, settingsDirty }: IndexPanelProps) {
  const reasonId = useId();
  const [acknowledged, setAcknowledged] = useState(() => hasAcknowledgedPrivacy(projectId));
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  const remote = providers.data?.embeddings.remote ?? false;
  const mutation = useMutation({
    mutationFn: () => startIndexing(projectId),
    onSuccess: async () => {
      if (remote) {
        acknowledgePrivacy(projectId);
      }
      await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
      await queryClient.invalidateQueries({ queryKey: importKeys.project(projectId) });
      await navigate(`/projects/${projectId}`);
    },
  });

  const blocked = inventory?.warnings.some((warning) => BLOCKING_WARNINGS.has(warning.code));
  const reason = settingsDirty
    ? messages.indexing.dirty
    : blocked
      ? messages.preAnalysis.blocking
      : null;
  const disabled =
    reason !== null ||
    inventory === undefined ||
    !providers.isSuccess ||
    (remote && !acknowledged) ||
    mutation.isPending;

  return (
    <section className="stack panel" aria-labelledby="privacy-title">
      <h2 id="privacy-title">{messages.privacy.title}</h2>
      {providers.isPending && <Loading />}
      {providers.isError && (
        <ProblemAlert error={providers.error} onRetry={() => void providers.refetch()} />
      )}
      {providers.isSuccess && (
        <ProviderNotice
          providers={providers.data}
          acknowledged={acknowledged}
          onAcknowledge={setAcknowledged}
        />
      )}
      {mutation.isError && <ProblemAlert error={mutation.error} />}
      {reason && (
        <p id={reasonId} className="notice notice--warning">
          {reason}
        </p>
      )}
      <div>
        <button
          type="button"
          className="button button--primary"
          disabled={disabled}
          aria-describedby={reason ? reasonId : undefined}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? messages.indexing.starting : messages.indexing.start}
        </button>
      </div>
    </section>
  );
}

interface ProviderNoticeProps {
  providers: Providers;
  acknowledged: boolean;
  onAcknowledge: (value: boolean) => void;
}

function ProviderNotice({ providers, acknowledged, onAcknowledge }: ProviderNoticeProps) {
  const { embeddings } = providers;
  if (embeddings.provider === 'none') {
    return <p>{messages.privacy.noEmbeddings}</p>;
  }
  if (!embeddings.remote) {
    return <p>{messages.privacy.localOnly}</p>;
  }
  return (
    <div className="notice notice--warning stack">
      <p>{messages.privacy.remote(PROVIDER_NAMES[embeddings.provider], embeddings.model)}</p>
      <p>
        <a href={`#${SETTINGS_SECTION_ID}`}>{messages.privacy.review}</a>
      </p>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(event) => onAcknowledge(event.target.checked)}
        />
        {messages.privacy.acknowledge}
      </label>
    </div>
  );
}
