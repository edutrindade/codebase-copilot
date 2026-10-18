import { useQuery } from '@tanstack/react-query';
import { LocateFixed } from 'lucide-react';
import { useState } from 'react';
import { ApiError } from '../../api/errors';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { CopyButton } from '../../components/ui/CopyButton';
import { SegmentedControl } from '../../components/ui/SegmentedControl';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { askKeys, getEvidence, getFileContent, type Evidence, type EvidenceDetail } from './api';
import { CodeLines } from './CodeLines';
import { highlightedRange, splitLines } from './evidence';
import { FileIcon } from './FileIcon';
import { PathTrail } from './PathTrail';

type Version = 'answered' | 'current';

interface CodeViewerProps {
  projectId: string;
  evidence: Evidence;
  activeSnapshotId: string | null;
}

export function CodeViewer({ projectId, evidence, activeSnapshotId }: CodeViewerProps) {
  const text = messages.ask.viewer;
  const range = highlightedRange(evidence);
  const lines = messages.ask.lines(range.start, range.end);
  const [scrollRequest, setScrollRequest] = useState(0);
  const [version, setVersion] = useState<Version>('answered');
  const detail = useQuery({
    queryKey: askKeys.evidence(projectId, evidence.id),
    queryFn: ({ signal }) => getEvidence(projectId, evidence.id, signal),
  });

  return (
    <div className="stack viewer">
      <div className="viewer__header">
        <p className="viewer__path">
          <FileIcon path={evidence.path} className="viewer__icon" />
          <span className="viewer__label">{evidence.citationLabel}</span>
          <PathTrail path={evidence.path} />
        </p>
        <div className="viewer__toolbar">
          <p className="muted">{text.highlighted(lines)}</p>
          <div className="viewer__actions">
            {version === 'answered' && (
              <Button
                size="sm"
                variant="ghost"
                icon={LocateFixed}
                onClick={() => setScrollRequest((count) => count + 1)}
              >
                {text.goToCited}
              </Button>
            )}
            <CopyButton text={evidence.path} label={text.copyPath} />
            <CopyButton text={evidence.snippet} label={text.copySnippet} />
          </div>
        </div>
      </div>
      {detail.isPending && <Loading />}
      {detail.isError && (
        <>
          <ProblemAlert error={detail.error} onRetry={() => void detail.refetch()} />
          <Snippet evidence={evidence} scrollRequest={scrollRequest} />
        </>
      )}
      {detail.isSuccess && (
        <EvidenceFile
          detail={detail.data}
          activeSnapshotId={activeSnapshotId}
          version={version}
          onVersionChange={setVersion}
          scrollRequest={scrollRequest}
        />
      )}
    </div>
  );
}

interface EvidenceFileProps {
  detail: EvidenceDetail;
  activeSnapshotId: string | null;
  version: Version;
  onVersionChange: (version: Version) => void;
  scrollRequest: number;
}

function EvidenceFile({
  detail,
  activeSnapshotId,
  version,
  onVersionChange,
  scrollRequest,
}: EvidenceFileProps) {
  const text = messages.ask.viewer;
  const canShowCurrent =
    detail.activeVersion === 'CHANGED' &&
    activeSnapshotId !== null &&
    activeSnapshotId !== detail.snapshotId;
  const current = version === 'current' && canShowCurrent;
  const versionNotice = text.activeVersion[detail.activeVersion];

  return (
    <>
      {versionNotice && (
        <Callout tone="warning">
          <p>{versionNotice}</p>
          {canShowCurrent && (
            <SegmentedControl
              label={text.versionLabel}
              value={current ? 'current' : 'answered'}
              options={[
                { value: 'answered', label: text.versions.answered },
                { value: 'current', label: text.versions.current },
              ]}
              onChange={onVersionChange}
            />
          )}
        </Callout>
      )}
      {detail.snapshotStatus === 'PRUNED' ? (
        <>
          <Callout tone="neutral">
            <p>{text.pruned}</p>
          </Callout>
          <Snippet evidence={detail} scrollRequest={scrollRequest} />
        </>
      ) : current && activeSnapshotId ? (
        <FileView
          key="current"
          snapshotId={activeSnapshotId}
          detail={detail}
          highlight={false}
          caption={text.currentVersion}
          scrollRequest={scrollRequest}
        />
      ) : (
        <FileView
          key="answered"
          snapshotId={detail.snapshotId}
          detail={detail}
          highlight
          caption={canShowCurrent ? text.answeredVersion : null}
          scrollRequest={scrollRequest}
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
  scrollRequest: number;
}

function FileView({ snapshotId, detail, highlight, caption, scrollRequest }: FileViewProps) {
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
        <Snippet evidence={detail} scrollRequest={scrollRequest} />
      </>
    );
  }
  const file = content.data;
  return (
    <>
      <p className="viewer__info muted">
        {caption && <span>{caption} </span>}
        {text.fileInfo(file.language, file.lineCount, formatBytes(file.sizeBytes))}
      </p>
      <CodeLines
        lines={splitLines(file.content)}
        firstLine={1}
        highlight={highlight ? highlightedRange(detail) : null}
        label={text.codeLabel(file.path)}
        scrollRequest={scrollRequest}
      />
    </>
  );
}

function Snippet({
  evidence,
  scrollRequest,
}: {
  evidence: Evidence | EvidenceDetail;
  scrollRequest: number;
}) {
  const text = messages.ask.viewer;
  return (
    <div className="stack">
      <h3>{text.snippetTitle}</h3>
      <CodeLines
        lines={splitLines(evidence.snippet)}
        firstLine={evidence.startLine}
        highlight={highlightedRange(evidence)}
        label={text.codeLabel(evidence.path)}
        scrollRequest={scrollRequest}
      />
    </div>
  );
}
