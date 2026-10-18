import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { Callout } from '../../components/ui/Callout';
import { TableScroll } from '../../components/ui/TableScroll';
import { Stat, StatGrid } from '../../components/ui/Stat';
import { formatBytes, formatDateTime, formatNumber } from '../../format';
import { messages } from '../../messages';
import type { ExclusionGroup, Inventory } from './api';

// The rest of the languages share one neutral color.
const LANGUAGE_COLORS = 5;

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
  const maxIncluded = Math.max(0, ...inventory.byDirectory.map((d) => d.includedFiles));

  return (
    <div className="stack">
      <p className="muted">
        {text.scannedAt(formatDateTime(inventory.scannedAt), inventory.settingsVersion)}
      </p>
      {inventory.truncated && (
        <Callout tone="warning">
          <p>{text.truncated}</p>
        </Callout>
      )}
      {inventory.warnings.length > 0 && (
        <section className="stack" aria-labelledby="inventory-warnings">
          <h3 id="inventory-warnings">{text.warningsTitle}</h3>
          <ul className="notice-list">
            {inventory.warnings.map((warning) => (
              <Callout as="li" key={warning.code} tone="warning">
                <p>{warning.message}</p>
                {warning.relatedProjectIds.map((projectId) => (
                  <Link key={projectId} to={`/projects/${projectId}`}>
                    {text.relatedProject}
                  </Link>
                ))}
              </Callout>
            ))}
          </ul>
        </section>
      )}
      <section className="stack" aria-labelledby="inventory-totals">
        <h3 id="inventory-totals">{text.totalsTitle}</h3>
        <StatGrid>
          {TOTALS.map((key) => (
            <Stat
              key={key}
              label={text.totals[key]}
              value={key === 'includedBytes' ? formatBytes(totals[key]) : formatNumber(totals[key])}
            />
          ))}
        </StatGrid>
      </section>
      <section aria-labelledby="inventory-languages">
        <h3 id="inventory-languages">{text.languagesTitle}</h3>
        <LanguagesTable languages={inventory.byLanguage} labelledBy="inventory-languages" />
      </section>
      <section aria-labelledby="inventory-directories">
        <h3 id="inventory-directories">{text.directoriesTitle}</h3>
        {inventory.byDirectory.length === 0 ? (
          <p className="muted">{text.none}</p>
        ) : (
          <TableScroll labelledBy="inventory-directories">
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
                    <td>
                      <span className="meter-cell">
                        <span className="tabular">{formatNumber(directory.includedFiles)}</span>
                        <Meter value={directory.includedFiles} max={maxIncluded} />
                      </span>
                    </td>
                    <td>{formatNumber(directory.excludedFiles)}</td>
                    <td>{formatNumber(directory.unsupportedFiles)}</td>
                    <td>{formatNumber(directory.unreadableFiles)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableScroll>
        )}
      </section>
      <section aria-labelledby="inventory-exclusions">
        <h3 id="inventory-exclusions">{text.exclusionsTitle}</h3>
        {inventory.exclusions.length === 0 ? (
          <p className="muted">{text.none}</p>
        ) : (
          <ul className="accordion">
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

interface LanguagesTableProps {
  languages: Inventory['byLanguage'];
  labelledBy: string;
}

export function LanguagesTable({ languages, labelledBy }: LanguagesTableProps) {
  const text = messages.preAnalysis;
  if (languages.length === 0) {
    return <p className="muted">{text.none}</p>;
  }
  const totalBytes = languages.reduce((sum, language) => sum + language.bytes, 0);
  const shown = languages.slice(0, LANGUAGE_COLORS);
  const otherBytes = totalBytes - shown.reduce((sum, language) => sum + language.bytes, 0);
  return (
    <div className="stack">
      {totalBytes > 0 && (
        // The table below carries the same numbers for screen readers.
        <div className="language-bar" aria-hidden="true">
          {shown.map((language, index) => (
            <span
              key={language.language}
              className={`language-bar__segment language-color-${index}`}
              style={{ width: `${(language.bytes / totalBytes) * 100}%` }}
              title={language.language}
            />
          ))}
          {otherBytes > 0 && (
            <span
              className="language-bar__segment language-color-other"
              style={{ width: `${(otherBytes / totalBytes) * 100}%` }}
              title={text.otherLanguages}
            />
          )}
        </div>
      )}
      <TableScroll labelledBy={labelledBy}>
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">{text.languageColumn}</th>
              <th scope="col">{text.filesColumn}</th>
              <th scope="col">{text.bytesColumn}</th>
            </tr>
          </thead>
          <tbody>
            {languages.map((language, index) => (
              <tr key={language.language}>
                <th scope="row">
                  <span
                    aria-hidden="true"
                    className={`language-swatch language-color-${index < LANGUAGE_COLORS ? index : 'other'}`}
                  />
                  {language.language}
                </th>
                <td>{formatNumber(language.files)}</td>
                <td>{formatBytes(language.bytes)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableScroll>
    </div>
  );
}

function Meter({ value, max }: { value: number; max: number }) {
  return (
    <span className="meter" aria-hidden="true">
      <span className="meter__fill" style={{ width: `${max > 0 ? (value / max) * 100 : 0}%` }} />
    </span>
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
    return <p className="accordion__static">{summary}</p>;
  }
  return (
    <details className="accordion__item">
      <summary>
        <ChevronRight aria-hidden="true" className="accordion__chevron" />
        {summary}
      </summary>
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
