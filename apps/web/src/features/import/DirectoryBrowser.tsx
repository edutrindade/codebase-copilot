import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
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
  const { rootKey, path } = value;
  const listing = useQuery({
    queryKey: importKeys.directories(rootKey, path),
    queryFn: ({ signal }) => listDirectories(rootKey, path, signal),
    placeholderData: keepPreviousData,
  });
  const segments = path === '' ? [] : path.split('/');
  const rootLabel = roots.find((root) => root.key === rootKey)?.label ?? rootKey;
  const goTo = (nextPath: string) => onChange({ rootKey, path: nextPath });

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
      <p aria-live="polite" className="browser__selected">
        {messages.newProject.browser.selected(selectionLabel(roots, value))}
      </p>
      {listing.isPending && <Loading />}
      {listing.isError && (
        <div className="stack">
          <ProblemAlert error={listing.error} onRetry={() => void listing.refetch()} />
          {path !== '' && (
            <div>
              <button type="button" className="button" onClick={() => goTo('')}>
                {messages.newProject.browser.backToRoot}
              </button>
            </div>
          )}
        </div>
      )}
      {listing.isSuccess && (
        <>
          {listing.data.directories.length === 0 ? (
            <p className="muted">{messages.newProject.browser.empty}</p>
          ) : (
            <ul className="browser__list" aria-label={messages.newProject.browser.subfolders}>
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
                    onClick={() => goTo(directory.relativePath)}
                  >
                    <span>{directory.name}</span>
                    {!directory.readable && (
                      <span className="muted"> ({messages.newProject.browser.unreadable})</span>
                    )}
                    {directory.readable && directory.hasChildren && (
                      <span aria-hidden="true" className="muted">
                        ›
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {listing.data.truncated && (
            <p className="notice notice--warning">{messages.newProject.browser.truncated}</p>
          )}
        </>
      )}
    </div>
  );
}
