import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Loading } from '../../components/Loading';
import { Pagination } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { listUnindexedFiles, projectKeys, type UnindexedStatus } from './api';

interface UnindexedFilesProps {
  snapshotId: string;
  failedFiles: number;
  unsupportedFiles: number;
}

export function UnindexedFiles({ snapshotId, failedFiles, unsupportedFiles }: UnindexedFilesProps) {
  const text = messages.project.unindexed;
  const [status, setStatus] = useState<UnindexedStatus>(failedFiles > 0 ? 'FAILED' : 'UNSUPPORTED');
  const [page, setPage] = useState(0);
  const files = useQuery({
    queryKey: projectKeys.files(snapshotId, status, page),
    queryFn: ({ signal }) => listUnindexedFiles(snapshotId, status, page, signal),
    placeholderData: keepPreviousData,
  });
  const counts: Record<UnindexedStatus, number> = {
    FAILED: failedFiles,
    UNSUPPORTED: unsupportedFiles,
  };

  function choose(next: UnindexedStatus) {
    setStatus(next);
    setPage(0);
  }

  return (
    <section className="stack panel" aria-labelledby="unindexed-title">
      <h2 id="unindexed-title">{text.title}</h2>
      <p className="muted">{text.detail}</p>
      <fieldset className="segmented">
        <legend>{text.filterLegend}</legend>
        {(['FAILED', 'UNSUPPORTED'] as const).map((option) => (
          <label key={option} className="checkbox">
            <input
              type="radio"
              name="unindexed-status"
              checked={status === option}
              onChange={() => choose(option)}
            />
            {text[option](counts[option])}
          </label>
        ))}
      </fieldset>
      {files.isPending && <Loading />}
      {files.isError && <ProblemAlert error={files.error} onRetry={() => void files.refetch()} />}
      {files.isSuccess && files.data.totalItems === 0 && <p className="muted">{text.empty}</p>}
      {files.isSuccess && files.data.totalItems > 0 && (
        <>
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{text.pathColumn}</th>
                  <th scope="col">{text.reasonColumn}</th>
                  <th scope="col">{text.sizeColumn}</th>
                </tr>
              </thead>
              <tbody>
                {files.data.items.map((file) => (
                  <tr key={file.relativePath}>
                    <th scope="row">
                      <code>{file.relativePath}</code>
                    </th>
                    <td>{file.reason ? text.reasons[file.reason] : '—'}</td>
                    <td>{formatBytes(file.sizeBytes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={files.data.totalPages} onChange={setPage} />
        </>
      )}
    </section>
  );
}
