import { Link } from 'react-router';
import { formatBytes, formatDateTime, formatNumber } from '../../format';
import { messages } from '../../messages';
import type { ExclusionGroup, Inventory } from './api';

const TOTALS = [
  'files',
  'includedFiles',
  'includedBytes',
  'excludedFiles',
  'unsupportedFiles',
  'unreadableFiles',
  'excludedDirectories',
] as const;

export function InventorySummary({ inventory }: { inventory: Inventory }) {
  const { totals } = inventory;
  const text = messages.preAnalysis;

  return (
    <div className="stack">
      <p className="muted">
        {text.scannedAt(formatDateTime(inventory.scannedAt), inventory.settingsVersion)}
      </p>
      {inventory.truncated && <p className="notice notice--warning">{text.truncated}</p>}
      {inventory.warnings.length > 0 && (
        <section className="stack" aria-labelledby="inventory-warnings">
          <h3 id="inventory-warnings">{text.warningsTitle}</h3>
          <ul className="notice-list">
            {inventory.warnings.map((warning) => (
              <li key={warning.code} className="notice notice--warning">
                <p>{warning.message}</p>
                {warning.relatedProjectIds.map((projectId) => (
                  <Link key={projectId} to={`/projects/${projectId}`}>
                    {text.relatedProject}
                  </Link>
                ))}
              </li>
            ))}
          </ul>
        </section>
      )}
      <section aria-labelledby="inventory-totals">
        <h3 id="inventory-totals">{text.totalsTitle}</h3>
        <dl className="totals">
          {TOTALS.map((key) => (
            <div key={key} className="totals__item">
              <dt>{text.totals[key]}</dt>
              <dd>
                {key === 'includedBytes' ? formatBytes(totals[key]) : formatNumber(totals[key])}
              </dd>
            </div>
          ))}
        </dl>
      </section>
      <section aria-labelledby="inventory-languages">
        <h3 id="inventory-languages">{text.languagesTitle}</h3>
        {inventory.byLanguage.length === 0 ? (
          <p className="muted">{text.none}</p>
        ) : (
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{text.languageColumn}</th>
                  <th scope="col">{text.filesColumn}</th>
                  <th scope="col">{text.bytesColumn}</th>
                </tr>
              </thead>
              <tbody>
                {inventory.byLanguage.map((language) => (
                  <tr key={language.language}>
                    <th scope="row">{language.language}</th>
                    <td>{formatNumber(language.files)}</td>
                    <td>{formatBytes(language.bytes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section aria-labelledby="inventory-directories">
        <h3 id="inventory-directories">{text.directoriesTitle}</h3>
        {inventory.byDirectory.length === 0 ? (
          <p className="muted">{text.none}</p>
        ) : (
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{text.directoryColumn}</th>
                  <th scope="col">{text.includedColumn}</th>
                  <th scope="col">{text.excludedColumn}</th>
                  <th scope="col">{text.unsupportedColumn}</th>
                  <th scope="col">{text.unreadableColumn}</th>
                </tr>
              </thead>
              <tbody>
                {inventory.byDirectory.map((directory) => (
                  <tr key={directory.path}>
                    <th scope="row">
                      {directory.path === '' ? text.rootDirectory : <code>{directory.path}</code>}
                    </th>
                    <td>{formatNumber(directory.includedFiles)}</td>
                    <td>{formatNumber(directory.excludedFiles)}</td>
                    <td>{formatNumber(directory.unsupportedFiles)}</td>
                    <td>{formatNumber(directory.unreadableFiles)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section aria-labelledby="inventory-exclusions">
        <h3 id="inventory-exclusions">{text.exclusionsTitle}</h3>
        {inventory.exclusions.length === 0 ? (
          <p className="muted">{text.none}</p>
        ) : (
          <ul className="exclusions">
            {inventory.exclusions.map((group) => (
              <li key={`${group.reason}:${group.ruleId ?? ''}:${group.pattern ?? ''}`}>
                <ExclusionItem group={group} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export function exclusionReason(group: ExclusionGroup): string {
  const reasons = messages.preAnalysis.reasons;
  switch (group.reason) {
    case 'DEFAULT_RULE':
      return reasons.DEFAULT_RULE(group.ruleId ?? '');
    case 'CUSTOM_PATTERN':
      return reasons.CUSTOM_PATTERN(group.pattern ?? '');
    default:
      return reasons[group.reason];
  }
}

function ExclusionItem({ group }: { group: ExclusionGroup }) {
  const summary = `${exclusionReason(group)}: ${messages.preAnalysis.exclusionCount(group.files, group.directories)}`;
  if (group.examples.length === 0) {
    return <p>{summary}</p>;
  }
  return (
    <details>
      <summary>{summary}</summary>
      <p className="muted">{messages.preAnalysis.examples}</p>
      <ul className="exclusions__examples">
        {group.examples.map((example) => (
          <li key={example}>
            <code>{example}</code>
          </li>
        ))}
      </ul>
    </details>
  );
}
