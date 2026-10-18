import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Loading } from '../../components/Loading';
import { Pagination } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Card } from '../../components/ui/Card';
import { RelativeTime } from '../../components/ui/RelativeTime';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatNumber } from '../../format';
import { messages } from '../../messages';
import { listSnapshots, projectKeys, type Snapshot } from './api';

export function SnapshotList({ projectId }: { projectId: string }) {
  const text = messages.project.snapshots;
  const [page, setPage] = useState(0);
  const snapshots = useQuery({
    queryKey: projectKeys.snapshots(projectId, page),
    queryFn: ({ signal }) => listSnapshots(projectId, page, signal),
    placeholderData: keepPreviousData,
  });

  return (
    <Card as="section" className="stack" aria-labelledby="snapshots-title">
      <h2 id="snapshots-title">{text.title}</h2>
      {snapshots.isPending && <Loading />}
      {snapshots.isError && (
        <ProblemAlert error={snapshots.error} onRetry={() => void snapshots.refetch()} />
      )}
      {snapshots.isSuccess && snapshots.data.totalItems === 0 && (
        <p className="muted">{text.empty}</p>
      )}
      {snapshots.isSuccess && snapshots.data.totalItems > 0 && (
        <>
          <ol className="timeline">
            {snapshots.data.items.map((snapshot) => (
              <SnapshotItem key={snapshot.id} snapshot={snapshot} />
            ))}
          </ol>
          <Pagination page={page} totalPages={snapshots.data.totalPages} onChange={setPage} />
        </>
      )}
    </Card>
  );
}

function SnapshotItem({ snapshot }: { snapshot: Snapshot }) {
  const text = messages.project.snapshots;
  const { changes, counts } = snapshot;
  return (
    <li className={snapshot.active ? 'timeline__item timeline__item--active' : 'timeline__item'}>
      <p className="timeline__heading">
        <RelativeTime iso={snapshot.createdAt} />
        <span>{text.status[snapshot.status]}</span>
        {snapshot.active && <StatusBadge tone="success">{text.active}</StatusBadge>}
        {snapshot.prunedAt && <span className="muted">({text.pruned})</span>}
      </p>
      <p className="muted">
        {changes
          ? text.changes(changes.added, changes.modified, changes.removed, changes.unchanged)
          : text.noChanges}
      </p>
      {counts && (
        <p className="muted tabular">
          {text.filesColumn}: {formatNumber(counts.indexedFiles)} · {text.chunksColumn}:{' '}
          {formatNumber(counts.chunks)}
        </p>
      )}
    </li>
  );
}
