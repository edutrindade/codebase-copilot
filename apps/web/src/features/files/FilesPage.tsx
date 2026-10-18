import { FolderTree } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { usePageTitle } from '../../app/title';
import { ButtonLink } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
import { SegmentedControl } from '../../components/ui/SegmentedControl';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import type { LineRange } from '../ask/evidence';
import { useProject } from '../project/ProjectLayout';
import { FILE_FILTERS, type FileFilter } from './api';
import { FilePreview } from './FilePreview';
import { FileTree } from './FileTree';
import './FilesPage.css';

export function FilesPage() {
  const text = messages.files;
  const project = useProject();
  const snapshot = project.activeSnapshot;
  const [searchParams, setSearchParams] = useSearchParams();
  const file = searchParams.get('file');
  const filter = parseFilter(searchParams.get('status'));
  const lines = parseLines(searchParams.get('lines'));
  const [expanded, setExpanded] = useState(() => new Set(file ? ancestors(file) : []));
  usePageTitle(
    file
      ? [file.slice(file.lastIndexOf('/') + 1), messages.pages.files, project.name]
      : [messages.pages.files, project.name],
  );

  if (!snapshot) {
    return (
      <section className="stack">
        <PageHeader title={messages.pages.files} />
        <EmptyState
          icon={FolderTree}
          title={text.noSnapshotTitle}
          action={<ButtonLink to={`/projects/${project.id}`}>{text.openOverview}</ButtonLink>}
        >
          <p>{text.noSnapshotDetail}</p>
        </EmptyState>
      </section>
    );
  }

  const counts: Record<FileFilter, number> = {
    ALL: snapshot.counts.files,
    INDEXED: snapshot.counts.indexedFiles,
    UNSUPPORTED: snapshot.counts.unsupportedFiles,
    FAILED: snapshot.counts.failedFiles,
  };

  function search(changes: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    }
    return next;
  }

  function toggle(path: string, open: boolean) {
    setExpanded((current) => {
      const next = new Set(current);
      if (open) {
        next.add(path);
      } else {
        next.delete(path);
      }
      return next;
    });
  }

  return (
    <section className="stack files">
      <PageHeader
        title={messages.pages.files}
        description={
          snapshot.completedAt && <p>{text.detail(formatDateTime(snapshot.completedAt))}</p>
        }
      />
      <div className="files__columns">
        <Card as="section" className="stack files__tree" aria-labelledby="files-tree-title">
          <h2 id="files-tree-title">{text.treeTitle}</h2>
          <SegmentedControl
            label={text.filterLabel}
            value={filter}
            options={FILE_FILTERS.map((option) => ({
              value: option,
              label: text.filters[option](counts[option]),
            }))}
            onChange={(next) =>
              setSearchParams(search({ status: next === 'ALL' ? null : next }), { replace: true })
            }
          />
          <div className="files__tree-scroll">
            <FileTree
              key={filter}
              snapshotId={snapshot.id}
              filter={filter}
              selected={file}
              expanded={expanded}
              onToggle={toggle}
              linkTo={(path) => ({ search: search({ file: path, lines: null }).toString() })}
            />
          </div>
        </Card>
        <Card as="section" className="stack files__viewer" aria-labelledby="files-viewer-title">
          <h2 id="files-viewer-title">{text.viewerTitle}</h2>
          {file ? (
            <FilePreview
              key={file}
              projectId={project.id}
              snapshotId={snapshot.id}
              path={file}
              lines={lines}
            />
          ) : (
            <p className="muted">{text.viewerEmpty}</p>
          )}
        </Card>
      </div>
    </section>
  );
}

function parseFilter(value: string | null): FileFilter {
  return FILE_FILTERS.find((option) => option === value) ?? 'ALL';
}

function parseLines(value: string | null): LineRange | null {
  const match = value ? /^(\d+)-(\d+)$/.exec(value) : null;
  if (!match) {
    return null;
  }
  const start = Number(match[1]);
  const end = Number(match[2]);
  return start >= 1 && end >= start ? { start, end } : null;
}

function ancestors(path: string): string[] {
  const parts = path.split('/').slice(0, -1);
  return parts.map((_, index) => parts.slice(0, index + 1).join('/'));
}
