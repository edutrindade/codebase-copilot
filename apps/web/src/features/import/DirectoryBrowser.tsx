import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useId, useRef, type KeyboardEvent } from 'react';
import { ChevronRight, Folder, FolderCheck, FolderLock } from 'lucide-react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { messages } from '../../messages';
import { importKeys, listDirectories, type LocalRoot } from './api';

export interface FolderSelection {
  rootKey: string;
  path: string;
}

interface DirectoryBrowserProps {
  roots: LocalRoot[];
  value: FolderSelection;
  onChange: (value: FolderSelection) => void;
}

export function selectionLabel(roots: LocalRoot[], { rootKey, path }: FolderSelection): string {
  const label = roots.find((root) => root.key === rootKey)?.label ?? rootKey;
  return path === '' ? label : `${label}/${path}`;
}

export function DirectoryBrowser({ roots, value, onChange }: DirectoryBrowserProps) {
  const rootSelectId = useId();
  const keysHintId = useId();
  const { rootKey, path } = value;
  const listing = useQuery({
    queryKey: importKeys.directories(rootKey, path),
    queryFn: ({ signal }) => listDirectories(rootKey, path, signal),
    placeholderData: keepPreviousData,
  });
  const segments = path === '' ? [] : path.split('/');
  const rootLabel = roots.find((root) => root.key === rootKey)?.label ?? rootKey;
  const listRef = useRef<HTMLUListElement>(null);
  const selectedRef = useRef<HTMLParagraphElement>(null);
  // The entry that was used disappears with the listing, so focus moves into the new one.
  const refocus = useRef(false);
  const goTo = (nextPath: string, keepFocus = false) => {
    refocus.current = keepFocus;
    onChange({ rootKey, path: nextPath });
  };
  const parentPath = segments.slice(0, -1).join('/');

  useEffect(() => {
    if (!refocus.current || listing.isPlaceholderData || !listing.isSuccess) {
      return;
    }
    refocus.current = false;
    const first = listRef.current?.querySelector<HTMLButtonElement>(
      '.browser__entry:not(:disabled)',
    );
    (first ?? selectedRef.current)?.focus();
  }, [listing.isPlaceholderData, listing.isSuccess, listing.data]);

  function moveFocus(event: KeyboardEvent<HTMLUListElement>) {
    const entries = [
      ...event.currentTarget.querySelectorAll<HTMLButtonElement>('.browser__entry:not(:disabled)'),
    ];
    const index = entries.findIndex((entry) => entry === document.activeElement);
    const target: Record<string, number> = {
      ArrowDown: Math.min(index + 1, entries.length - 1),
      ArrowUp: Math.max(index - 1, 0),
      Home: 0,
      End: entries.length - 1,
    };
    const next = target[event.key];
    if (next !== undefined) {
      event.preventDefault();
      entries[next]?.focus();
    } else if (event.key === 'ArrowRight' && index >= 0) {
      event.preventDefault();
      entries[index]?.click();
    } else if (event.key === 'ArrowLeft' && path !== '') {
      event.preventDefault();
      goTo(parentPath, true);
    }
  }

  return (
    <div className="browser stack">
      {roots.length > 1 && (
        <div className="field">
          <label htmlFor={rootSelectId}>{messages.newProject.browser.rootLabel}</label>
          <select
            id={rootSelectId}
            value={rootKey}
            onChange={(event) => onChange({ rootKey: event.target.value, path: '' })}
          >
            {roots.map((root) => (
              <option key={root.key} value={root.key}>
                {root.label}
              </option>
            ))}
          </select>
        </div>
      )}
      <nav aria-label={messages.newProject.browser.location} className="breadcrumbs">
        <ol>
          <li>
            <button
              type="button"
              className="link-button"
              aria-current={segments.length === 0 ? 'location' : undefined}
              onClick={() => goTo('')}
            >
              {rootLabel}
            </button>
          </li>
          {segments.map((segment, index) => (
            <li key={segments.slice(0, index + 1).join('/')}>
              <button
                type="button"
                className="link-button"
                aria-current={index === segments.length - 1 ? 'location' : undefined}
                onClick={() => goTo(segments.slice(0, index + 1).join('/'))}
              >
                {segment}
              </button>
            </li>
          ))}
        </ol>
      </nav>
      <p aria-live="polite" className="browser__selected" ref={selectedRef} tabIndex={-1}>
        <FolderCheck aria-hidden="true" className="browser__icon" />
        {messages.newProject.browser.selected(selectionLabel(roots, value))}
      </p>
      {listing.isPending && <Loading />}
      {listing.isError && (
        <div className="stack">
          <ProblemAlert error={listing.error} onRetry={() => void listing.refetch()} />
          {path !== '' && (
            <div>
              <Button size="sm" onClick={() => goTo('')}>
                {messages.newProject.browser.backToRoot}
              </Button>
            </div>
          )}
        </div>
      )}
      {listing.isSuccess && (
        <>
          {listing.data.directories.length === 0 ? (
            <p className="muted">{messages.newProject.browser.empty}</p>
          ) : (
            <ul
              ref={listRef}
              className="browser__list"
              aria-label={messages.newProject.browser.subfolders}
              aria-describedby={keysHintId}
              onKeyDown={moveFocus}
            >
              {listing.data.directories.map((directory) => (
                <li key={directory.relativePath}>
                  <button
                    type="button"
                    className="browser__entry"
                    disabled={!directory.readable}
                    aria-label={
                      directory.readable
                        ? messages.newProject.browser.open(directory.name)
                        : undefined
                    }
                    onClick={() => goTo(directory.relativePath, true)}
                  >
                    {directory.readable ? (
                      <Folder aria-hidden="true" className="browser__icon" />
                    ) : (
                      <FolderLock aria-hidden="true" className="browser__icon" />
                    )}
                    <span className="browser__name">{directory.name}</span>
                    {!directory.readable && (
                      <span className="muted"> ({messages.newProject.browser.unreadable})</span>
                    )}
                    {directory.readable && directory.hasChildren && (
                      <ChevronRight aria-hidden="true" className="browser__icon muted" />
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {listing.data.directories.length > 0 && (
            <p id={keysHintId} className="muted browser__keys">
              {messages.newProject.browser.keys}
            </p>
          )}
          {listing.data.truncated && (
            <Callout tone="warning">
              <p>{messages.newProject.browser.truncated}</p>
            </Callout>
          )}
        </>
      )}
    </div>
  );
}
