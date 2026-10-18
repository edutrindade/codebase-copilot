import { useQuery } from '@tanstack/react-query';
import { ChevronRight, Folder, FolderOpen } from 'lucide-react';
import type { CSSProperties, KeyboardEvent } from 'react';
import { Link, type To } from 'react-router';
import { Button } from '../../components/ui/Button';
import { cx } from '../../components/ui/cx';
import { Skeleton } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { messages } from '../../messages';
import { FileIcon } from '../ask/FileIcon';
import { filesKeys, getTree, type FileFilter, type TreeEntry } from './api';

interface FileTreeProps {
  snapshotId: string;
  filter: FileFilter;
  selected: string | null;
  expanded: ReadonlySet<string>;
  onToggle: (path: string, open: boolean) => void;
  linkTo: (path: string) => To;
}

// Nested lists of disclosure buttons and links; the arrows move between the visible rows.
export function FileTree(props: FileTreeProps) {
  return (
    <ul className="file-tree" aria-label={messages.files.treeLabel} onKeyDown={moveFocus}>
      <TreeLevel {...props} path="" depth={0} />
    </ul>
  );
}

function TreeLevel({ path, depth, ...props }: FileTreeProps & { path: string; depth: number }) {
  const text = messages.files;
  const { snapshotId, filter } = props;
  const tree = useQuery({
    queryKey: filesKeys.tree(snapshotId, filter, path),
    queryFn: ({ signal }) => getTree(snapshotId, filter, path, signal),
    staleTime: Infinity,
  });
  const style = { '--tree-depth': depth } as CSSProperties;

  if (tree.isPending) {
    return (
      <li className="file-tree__note" style={style} role="status">
        <span className="visually-hidden">{messages.loading}</span>
        <Skeleton width="60%" />
        <Skeleton width="45%" />
      </li>
    );
  }
  if (tree.isError) {
    return (
      <li className="file-tree__note" style={style}>
        <span className="muted">{text.loadFailed}</span>{' '}
        <Button size="sm" variant="ghost" onClick={() => void tree.refetch()}>
          {text.retry}
        </Button>
      </li>
    );
  }
  if (tree.data.entries.length === 0) {
    return (
      <li className="file-tree__note muted" style={style}>
        {depth === 0 && filter !== 'ALL' ? text.emptyFilter : text.emptyFolder}
      </li>
    );
  }
  return tree.data.entries.map((entry) =>
    entry.type === 'DIRECTORY' ? (
      <DirectoryRow key={entry.path} entry={entry} depth={depth} {...props} />
    ) : (
      <FileRow key={entry.path} entry={entry} depth={depth} {...props} />
    ),
  );
}

interface RowProps extends FileTreeProps {
  entry: TreeEntry;
  depth: number;
}

function DirectoryRow({ entry, depth, ...props }: RowProps) {
  const text = messages.files;
  const open = props.expanded.has(entry.path);
  const unindexed = entry.unsupportedFiles + entry.failedFiles;
  const Icon = open ? FolderOpen : Folder;
  return (
    <li>
      <button
        type="button"
        className="file-tree__row"
        style={{ '--tree-depth': depth } as CSSProperties}
        aria-expanded={open}
        data-tree-path={entry.path}
        onClick={() => props.onToggle(entry.path, !open)}
      >
        <ChevronRight
          aria-hidden="true"
          className={cx('file-tree__chevron', open && 'file-tree__chevron--open')}
        />
        <Icon aria-hidden="true" className="file-tree__icon" />
        <span className="file-tree__name">{entry.name}</span>
        <span className="file-tree__meta">
          {props.filter === 'ALL' && unindexed > 0
            ? text.unindexedCount(unindexed)
            : text.fileCount(entry.files)}
        </span>
      </button>
      {open && (
        <ul>
          <TreeLevel {...props} path={entry.path} depth={depth + 1} />
        </ul>
      )}
    </li>
  );
}

function FileRow({ entry, depth, selected, linkTo }: RowProps) {
  const status = entry.status ?? 'INDEXED';
  return (
    <li>
      <Link
        to={linkTo(entry.path)}
        className="file-tree__row"
        style={{ '--tree-depth': depth } as CSSProperties}
        aria-current={selected === entry.path ? 'true' : undefined}
        data-tree-path={entry.path}
      >
        <span className="file-tree__chevron" />
        <FileIcon path={entry.path} className="file-tree__icon" />
        <span className="file-tree__name">{entry.name}</span>
        {status !== 'INDEXED' && (
          <StatusBadge
            tone={status === 'FAILED' ? 'danger' : 'warning'}
            className="file-tree__badge"
          >
            {messages.files.status[status]}
          </StatusBadge>
        )}
      </Link>
    </li>
  );
}

function parentPath(path: string): string {
  return path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
}

function moveFocus(event: KeyboardEvent<HTMLUListElement>) {
  const current = event.target;
  if (!(current instanceof HTMLElement) || current.dataset.treePath === undefined) {
    return;
  }
  const rows = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('[data-tree-path]'));
  const index = rows.indexOf(current);
  const expanded = current.getAttribute('aria-expanded');
  let next: HTMLElement | undefined;
  switch (event.key) {
    case 'ArrowDown':
      next = rows[index + 1];
      break;
    case 'ArrowUp':
      next = rows[index - 1];
      break;
    case 'Home':
      next = rows[0];
      break;
    case 'End':
      next = rows.at(-1);
      break;
    case 'ArrowRight':
      if (expanded === 'false') {
        current.click();
      } else if (expanded === 'true') {
        next = rows[index + 1];
      }
      break;
    case 'ArrowLeft':
      if (expanded === 'true') {
        current.click();
      } else {
        const parent = parentPath(current.dataset.treePath);
        next = rows.find((row) => row.dataset.treePath === parent);
      }
      break;
    default:
      return;
  }
  event.preventDefault();
  next?.focus();
}
