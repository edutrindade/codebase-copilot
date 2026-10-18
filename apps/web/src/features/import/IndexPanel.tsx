import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Play } from 'lucide-react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { Card } from '../../components/ui/Card';
import { messages } from '../../messages';
import { workspaceKeys } from '../workspaces/api';
import { getProviders, importKeys, startIndexing, type Providers } from './api';
import { acknowledgePrivacy, hasAcknowledgedPrivacy, providerName } from './privacy';

interface IndexPanelProps {
  projectId: string;
  title: string;
  startLabel: string;
  // Same page anchor ("#…") or a route to the exclusions.
  reviewHref: string;
  ready: boolean;
  blockedReason: string | null;
  // Inside another card: a lower heading and no card of its own.
  embedded?: boolean;
}

export function IndexPanel({
  projectId,
  title,
  startLabel,
  reviewHref,
  ready,
  blockedReason,
  embedded = false,
}: IndexPanelProps) {
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

  const reason = blockedReason;
  const disabled =
    reason !== null ||
    !ready ||
    !providers.isSuccess ||
    (remote && !acknowledged) ||
    mutation.isPending;

  const Heading = embedded ? 'h3' : 'h2';
  const Container = embedded ? 'section' : Card;
  return (
    <Container className="stack index-panel" aria-labelledby="index-panel-title">
      <Heading id="index-panel-title">{title}</Heading>
      {providers.isPending && <Loading />}
      {providers.isSuccess && <IndexSummary providers={providers.data} />}
      {providers.isError && (
        <ProblemAlert error={providers.error} onRetry={() => void providers.refetch()} />
      )}
      {providers.isSuccess && (
        <ProviderNotice
          providers={providers.data}
          reviewHref={reviewHref}
          acknowledged={acknowledged}
          onAcknowledge={setAcknowledged}
        />
      )}
      {mutation.isError && <ProblemAlert error={mutation.error} />}
      {reason && (
        <Callout tone="warning">
          <p id={reasonId}>{reason}</p>
        </Callout>
      )}
      <div>
        <Button
          variant="primary"
          icon={Play}
          disabled={disabled}
          pending={mutation.isPending}
          aria-describedby={reason ? reasonId : undefined}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? messages.indexing.starting : startLabel}
        </Button>
      </div>
    </Container>
  );
}

function IndexSummary({ providers }: { providers: Providers }) {
  const text = messages.indexing;
  const { embeddings } = providers;
  const none = embeddings.provider === 'none';
  const provider = none
    ? text.noProvider
    : [providerName(embeddings.provider), embeddings.model].filter(Boolean).join(' · ');
  return (
    <dl className="index-summary">
      <div>
        <dt>{text.provider}</dt>
        <dd>{provider}</dd>
      </div>
      <div>
        <dt>{text.leaves}</dt>
        <dd>{embeddings.remote ? text.leavesChunks : text.leavesNothing}</dd>
      </div>
      <div>
        <dt>{text.cost}</dt>
        <dd>{embeddings.remote ? text.costUnknown : text.costNone}</dd>
      </div>
    </dl>
  );
}

interface ProviderNoticeProps {
  providers: Providers;
  reviewHref: string;
  acknowledged: boolean;
  onAcknowledge: (value: boolean) => void;
}

function ProviderNotice({
  providers,
  reviewHref,
  acknowledged,
  onAcknowledge,
}: ProviderNoticeProps) {
  const { embeddings } = providers;
  if (embeddings.provider === 'none') {
    return <p>{messages.privacy.noEmbeddings}</p>;
  }
  if (!embeddings.remote) {
    return <p>{messages.privacy.localOnly}</p>;
  }
  return (
    <Callout tone="warning">
      <p>{messages.privacy.remote(providerName(embeddings.provider), embeddings.model)}</p>
      <p>
        {reviewHref.startsWith('#') ? (
          <a href={reviewHref}>{messages.privacy.review}</a>
        ) : (
          <Link to={reviewHref}>{messages.privacy.review}</Link>
        )}
      </p>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(event) => onAcknowledge(event.target.checked)}
        />
        {messages.privacy.acknowledge}
      </label>
    </Callout>
  );
}
