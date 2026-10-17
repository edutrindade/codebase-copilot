import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError } from '../../api/errors';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { askKeys, getEvidence, getFileContent, type Evidence, type EvidenceDetail } from './api';
import { CodeLines } from './CodeLines';
import { highlightedRange, splitLines } from './evidence';

interface CodeViewerProps {
  projectId: string;
  evidence: Evidence;
  activeSnapshotId: string | null;
}

export function CodeViewer({ projectId, evidence, activeSnapshotId }: CodeViewerProps) {
  const text = messages.ask.viewer;
  const range = highlightedRange(evidence);
  const lines = messages.ask.lines(range.start, range.end);
  const detail = useQuery({
    queryKey: askKeys.evidence(projectId, evidence.id),
    queryFn: ({ signal }) => getEvidence(projectId, evidence.id, signal),
  });

  return (
    <div className="stack">
      <div>
        <p className="viewer__path">
          <span className="viewer__label">{evidence.citationLabel}</span>{' '}
          <code>{evidence.path}</code>
        </p>
        <p className="muted">{text.highlighted(lines)}</p>
      </div>
      {detail.isPending && <Loading />}
      {detail.isError && (
        <>
          <ProblemAlert error={detail.error} onRetry={() => void detail.refetch()} />
          <Snippet evidence={evidence} />
        </>
      )}
      {detail.isSuccess && (
        <EvidenceFile detail={detail.data} activeSnapshotId={activeSnapshotId} />
      )}
    </div>
  );
}

function EvidenceFile({
  detail,
  activeSnapshotId,
}: {
  detail: EvidenceDetail;
  activeSnapshotId: string | null;
}) {
  const text = messages.ask.viewer;
  const [showCurrent, setShowCurrent] = useState(false);
  const canShowCurrent =
    detail.activeVersion === 'CHANGED' &&
    activeSnapshotId !== null &&
    activeSnapshotId !== detail.snapshotId;
  const current = showCurrent && canShowCurrent;
  const versionNotice = text.activeVersion[detail.activeVersion];

  return (
    <>
      {versionNotice && (
        <Callout tone="warning">
          <p>{versionNotice}</p>
          {canShowCurrent && (
            <div>
              <Button size="sm" aria-pressed={current} onClick={() => setShowCurrent(!current)}>
                {current ? text.showAnswered : text.showCurrent}
              </Button>
            </div>
          )}
        </Callout>
      )}
      {detail.snapshotStatus === 'PRUNED' ? (
        <>
          <Callout tone="neutral">
            <p>{text.pruned}</p>
          </Callout>
          <Snippet evidence={detail} />
        </>
      ) : current && activeSnapshotId ? (
        <FileView
          key="current"
          snapshotId={activeSnapshotId}
          detail={detail}
          highlight={false}
          caption={text.currentVersion}
        />
      ) : (
        <FileView
          key="answered"
          snapshotId={detail.snapshotId}
          detail={detail}
          highlight
          caption={canShowCurrent ? text.answeredVersion : null}
        />
      )}
    </>
  );
}

interface FileViewProps {
  snapshotId: string;
  detail: EvidenceDetail;
  highlight: boolean;
  caption: string | null;
}

function FileView({ snapshotId, detail, highlight, caption }: FileViewProps) {
  const text = messages.ask.viewer;
  const content = useQuery({
    queryKey: askKeys.content(snapshotId, detail.path),
    queryFn: ({ signal }) => getFileContent(snapshotId, detail.path, signal),
    staleTime: Infinity,
  });

  if (content.isPending) {
    return <Loading />;
  }
  if (content.isError) {
    const pruned = content.error instanceof ApiError && content.error.code === 'SNAPSHOT_PRUNED';
    return (
      <>
        {pruned ? (
          <Callout tone="neutral">
            <p>{text.pruned}</p>
          </Callout>
        ) : (
          <>
            <ProblemAlert error={content.error} onRetry={() => void content.refetch()} />
            <Callout tone="neutral">
              <p>{text.fileUnavailable}</p>
            </Callout>
          </>
        )}
        <Snippet evidence={detail} />
      </>
    );
  }
  const file = content.data;
  return (
    <>
      {caption && <p className="muted">{caption}</p>}
      <p className="muted">
        {text.fileInfo(file.language, file.lineCount, formatBytes(file.sizeBytes))}
      </p>
      <CodeLines
        lines={splitLines(file.content)}
        firstLine={1}
        highlight={highlight ? highlightedRange(detail) : null}
        label={text.codeLabel(file.path)}
      />
    </>
  );
}

function Snippet({ evidence }: { evidence: Evidence | EvidenceDetail }) {
  const text = messages.ask.viewer;
  return (
    <div className="stack">
      <h3>{text.snippetTitle}</h3>
      <CodeLines
        lines={splitLines(evidence.snippet)}
        firstLine={evidence.startLine}
        highlight={highlightedRange(evidence)}
        label={text.codeLabel(evidence.path)}
      />
    </div>
  );
}
