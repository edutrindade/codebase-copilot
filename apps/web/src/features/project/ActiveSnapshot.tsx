import { useQuery } from '@tanstack/react-query';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Card } from '../../components/ui/Card';
import { Stat, StatGrid } from '../../components/ui/Stat';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatBytes, formatDateTime, formatNumber } from '../../format';
import { messages } from '../../messages';
import { LanguagesTable } from '../import/InventorySummary';
import type { Project } from '../workspaces/api';
import { getSnapshot, projectKeys } from './api';

const COUNTS = [
  'files',
  'indexedFiles',
  'unsupportedFiles',
  'failedFiles',
  'chunks',
  'bytes',
] as const;

type ActiveSnapshotData = NonNullable<Project['activeSnapshot']>;

export function ActiveSnapshot({ snapshot }: { snapshot: ActiveSnapshotData }) {
  const text = messages.project.snapshot;
  const detail = useQuery({
    queryKey: projectKeys.snapshot(snapshot.id),
    queryFn: ({ signal }) => getSnapshot(snapshot.id, signal),
  });

  return (
    <Card as="section" className="stack" aria-labelledby="active-snapshot-title">
      <h2 id="active-snapshot-title">{text.title}</h2>
      <p>
        <StatusBadge tone={snapshot.status === 'READY' ? 'success' : 'warning'}>
          {text.status[snapshot.status]}
        </StatusBadge>{' '}
        {snapshot.completedAt && (
          <span className="muted">{text.completedAt(formatDateTime(snapshot.completedAt))}</span>
        )}
      </p>
      <StatGrid>
        {COUNTS.map((key) => (
          <Stat
            key={key}
            label={text.counts[key]}
            value={
              key === 'bytes'
                ? formatBytes(snapshot.counts.bytes)
                : formatNumber(snapshot.counts[key])
            }
          />
        ))}
      </StatGrid>
      {detail.isError && (
        <ProblemAlert error={detail.error} onRetry={() => void detail.refetch()} />
      )}
      {detail.isSuccess && (
        <>
          <p className="muted">
            {detail.data.embedding
              ? text.embedding(
                  detail.data.embedding.provider,
                  detail.data.embedding.model,
                  detail.data.embedding.dimensions,
                )
              : text.noEmbedding}
          </p>
          {detail.data.inventory && (
            <section aria-labelledby="snapshot-languages">
              <h3 id="snapshot-languages">{text.languagesTitle}</h3>
              <LanguagesTable
                languages={detail.data.inventory.byLanguage}
                labelledBy="snapshot-languages"
              />
            </section>
          )}
        </>
      )}
    </Card>
  );
}
