import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Loading } from '../../components/Loading';
import { Pagination } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Card } from '../../components/ui/Card';
import { TableScroll } from '../../components/ui/TableScroll';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatDateTime, formatNumber } from '../../format';
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
          <TableScroll labelledBy="snapshots-title">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{text.createdColumn}</th>
                  <th scope="col">{text.statusColumn}</th>
                  <th scope="col">{text.changesColumn}</th>
                  <th scope="col">{text.filesColumn}</th>
                  <th scope="col">{text.chunksColumn}</th>
                </tr>
              </thead>
              <tbody>
                {snapshots.data.items.map((snapshot) => (
                  <SnapshotRow key={snapshot.id} snapshot={snapshot} />
                ))}
              </tbody>
            </table>
          </TableScroll>
          <Pagination page={page} totalPages={snapshots.data.totalPages} onChange={setPage} />
        </>
      )}
    </Card>
  );
}

function SnapshotRow({ snapshot }: { snapshot: Snapshot }) {
  const text = messages.project.snapshots;
  const { changes, counts } = snapshot;
  return (
    <tr>
      <th scope="row">{formatDateTime(snapshot.createdAt)}</th>
      <td>
        {text.status[snapshot.status]}
        {snapshot.active && (
          <>
            {' '}
            <StatusBadge tone="success">{text.active}</StatusBadge>
          </>
        )}
        {snapshot.prunedAt && <span className="muted"> ({text.pruned})</span>}
      </td>
      <td>
        {changes
          ? text.changes(changes.added, changes.modified, changes.removed, changes.unchanged)
          : text.noChanges}
      </td>
      <td>{counts ? formatNumber(counts.indexedFiles) : text.noChanges}</td>
      <td>{counts ? formatNumber(counts.chunks) : text.noChanges}</td>
    </tr>
  );
}
