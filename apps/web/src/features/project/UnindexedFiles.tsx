import { FileWarning } from 'lucide-react';
import { Link } from 'react-router';
import { Card } from '../../components/ui/Card';
import { messages } from '../../messages';

interface UnindexedFilesProps {
  projectId: string;
  failedFiles: number;
  unsupportedFiles: number;
}

// Only the counts: the list itself lives in the Files tab, with the filter applied.
export function UnindexedFiles({ projectId, failedFiles, unsupportedFiles }: UnindexedFilesProps) {
  const text = messages.project.unindexed;
  const counts = { FAILED: failedFiles, UNSUPPORTED: unsupportedFiles };
  return (
    <Card as="section" className="stack" aria-labelledby="unindexed-title">
      <h2 id="unindexed-title" className="unindexed__title">
        <FileWarning aria-hidden="true" className="unindexed__icon" />
        {text.title}
      </h2>
      <p className="muted">{text.detail}</p>
      <ul className="unindexed__links">
        {(['FAILED', 'UNSUPPORTED'] as const)
          .filter((status) => counts[status] > 0)
          .map((status) => (
            <li key={status}>
              <Link to={`/projects/${projectId}/files?status=${status}`}>
                {text[status](counts[status])}
              </Link>
            </li>
          ))}
      </ul>
    </Card>
  );
}
