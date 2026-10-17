import { useQuery } from '@tanstack/react-query';
import { ProblemAlert } from '../../components/ProblemAlert';
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
    <section className="stack panel" aria-labelledby="active-snapshot-title">
      <h2 id="active-snapshot-title">{text.title}</h2>
      <p>
        <span className={`status status--${snapshot.status.toLowerCase()}`}>
          {text.status[snapshot.status]}
        </span>{' '}
        {snapshot.completedAt && (
          <span className="muted">{text.completedAt(formatDateTime(snapshot.completedAt))}</span>
        )}
      </p>
      <dl className="totals">
        {COUNTS.map((key) => (
          <div key={key} className="totals__item">
            <dt>{text.counts[key]}</dt>
            <dd>
              {key === 'bytes'
                ? formatBytes(snapshot.counts.bytes)
                : formatNumber(snapshot.counts[key])}
            </dd>
          </div>
        ))}
      </dl>
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
              <LanguagesTable languages={detail.data.inventory.byLanguage} />
            </section>
          )}
        </>
      )}
    </section>
  );
}
