import { useQuery } from '@tanstack/react-query';
import { MessageSquareText } from 'lucide-react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { CopyButton } from '../../components/ui/CopyButton';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { askKeys, getFileContent } from '../ask/api';
import { CodeLines } from '../ask/CodeLines';
import { askDraftState } from '../ask/draft';
import { splitLines, type LineRange } from '../ask/evidence';
import { FileIcon } from '../ask/FileIcon';
import { PathTrail } from '../ask/PathTrail';
import { filesKeys, getFileInfo } from './api';

interface FilePreviewProps {
  projectId: string;
  snapshotId: string;
  path: string;
  lines: LineRange | null;
}

export function FilePreview({ projectId, snapshotId, path, lines }: FilePreviewProps) {
  const text = messages.files;
  const info = useQuery({
    queryKey: filesKeys.file(snapshotId, path),
    queryFn: ({ signal }) => getFileInfo(snapshotId, path, signal),
    staleTime: Infinity,
  });
  const indexed = info.data?.status === 'INDEXED';
  const content = useQuery({
    queryKey: askKeys.content(snapshotId, path),
    queryFn: ({ signal }) => getFileContent(snapshotId, path, signal),
    staleTime: Infinity,
    enabled: indexed,
  });

  return (
    <div className="stack viewer">
      <div className="viewer__header">
        <p className="viewer__path">
          <FileIcon path={path} className="viewer__icon" />
          <PathTrail path={path} />
        </p>
        <div className="viewer__toolbar">
          <p className="muted">
            {content.data
              ? messages.ask.viewer.fileInfo(
                  content.data.language,
                  content.data.lineCount,
                  formatBytes(content.data.sizeBytes),
                )
              : info.data && formatBytes(info.data.sizeBytes)}
            {lines && content.data && <> · {text.lines(lines.start, lines.end)}</>}
          </p>
          <div className="viewer__actions">
            <CopyButton text={path} label={text.copyPath} />
            <ButtonLink
              to={`/projects/${projectId}/ask`}
              state={askDraftState(text.askDraft(path))}
              size="sm"
              variant="ghost"
              icon={MessageSquareText}
            >
              {text.askAbout}
            </ButtonLink>
          </div>
        </div>
      </div>
      {(info.isPending || (indexed && content.isPending)) && <Loading />}
      {info.isError && <ProblemAlert error={info.error} onRetry={() => void info.refetch()} />}
      {info.isSuccess && info.data === null && (
        <Callout tone="neutral">
          <p>{text.notFound}</p>
        </Callout>
      )}
      {info.data && !indexed && (
        <Callout tone={info.data.status === 'FAILED' ? 'danger' : 'warning'}>
          <p>
            {text.notIndexed(
              info.data.reason
                ? messages.project.unindexed.reasons[info.data.reason]
                : text.status[info.data.status],
            )}
          </p>
          <p>{text.notIndexedDetail}</p>
        </Callout>
      )}
      {content.isError && (
        <ProblemAlert error={content.error} onRetry={() => void content.refetch()} />
      )}
      {content.isSuccess && (
        <CodeLines
          lines={splitLines(content.data.content)}
          firstLine={1}
          highlight={lines}
          label={messages.ask.viewer.codeLabel(path)}
        />
      )}
    </div>
  );
}
