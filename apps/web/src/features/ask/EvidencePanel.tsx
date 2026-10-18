import { useEffect, useRef, type KeyboardEvent } from 'react';
import { StatusBadge, type Tone } from '../../components/ui/StatusBadge';
import { messages } from '../../messages';
import type { Answer, Evidence } from './api';
import { groupByFile, highlightedRange } from './evidence';
import { FileIcon } from './FileIcon';
import { PathTrail } from './PathTrail';

const STATUS_TONE: Record<Evidence['validationStatus'], Tone> = {
  CITED: 'success',
  INVALID_RANGE: 'warning',
  NOT_CITED: 'neutral',
};

interface EvidencePanelProps {
  answer: Answer | null;
  selectedEvidenceId: string | null;
  onOpen: (evidence: Evidence) => void;
}

export function EvidencePanel({ answer, selectedEvidenceId, onOpen }: EvidencePanelProps) {
  const text = messages.ask.evidence;
  if (!answer) {
    return <p className="muted">{text.empty}</p>;
  }
  if (answer.evidence.length === 0) {
    return (
      <>
        <p className="muted">{text.forAnswer(answer.question)}</p>
        <p>{text.none}</p>
      </>
    );
  }

  // ↑ and ↓ move between the items, in the same order as [ and ].
  function onKeyDown(event: KeyboardEvent<HTMLOListElement>) {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }
    const buttons = [...event.currentTarget.querySelectorAll<HTMLButtonElement>('.evidence__open')];
    const index = buttons.findIndex((button) => button === document.activeElement);
    if (index === -1) {
      return;
    }
    event.preventDefault();
    const next = event.key === 'ArrowDown' ? index + 1 : index - 1;
    buttons[Math.min(buttons.length - 1, Math.max(0, next))]?.focus();
  }

  return (
    <>
      <p className="muted">{text.forAnswer(answer.question)}</p>
      <ol className="evidence-groups" onKeyDown={onKeyDown}>
        {groupByFile(answer.evidence).map((group) => (
          <li key={group.path} className="evidence-group">
            <p className="evidence-group__file">
              <FileIcon path={group.path} className="evidence-group__icon" />
              <PathTrail path={group.path} />
            </p>
            <ol className="evidence-list">
              {group.evidence.map((evidence) => (
                <EvidenceItem
                  key={evidence.id}
                  evidence={evidence}
                  selected={evidence.id === selectedEvidenceId}
                  onOpen={onOpen}
                />
              ))}
            </ol>
          </li>
        ))}
      </ol>
    </>
  );
}

interface EvidenceItemProps {
  evidence: Evidence;
  selected: boolean;
  onOpen: (evidence: Evidence) => void;
}

function EvidenceItem({ evidence, selected, onOpen }: EvidenceItemProps) {
  const text = messages.ask.evidence;
  const ref = useRef<HTMLLIElement>(null);
  const range = highlightedRange(evidence);
  const ranks = [
    evidence.ranks.lexical !== null && text.rankLexical(evidence.ranks.lexical),
    evidence.ranks.vector !== null && text.rankVector(evidence.ranks.vector),
    evidence.ranks.path !== null && text.rankPath(evidence.ranks.path),
  ].filter((rank): rank is string => typeof rank === 'string');
  const status = evidence.validationStatus;

  useEffect(() => {
    if (selected) {
      // jsdom has no scrollIntoView.
      ref.current?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [selected]);

  return (
    <li
      ref={ref}
      className={`evidence evidence--${status.toLowerCase()}${selected ? ' evidence--selected' : ''}`}
      aria-current={selected ? 'true' : undefined}
    >
      <button type="button" className="evidence__open" onClick={() => onOpen(evidence)}>
        <span className="visually-hidden">{text.open}</span>{' '}
        <span className="viewer__label">{evidence.citationLabel}</span>{' '}
        <span className="evidence__lines">{messages.ask.lines(range.start, range.end)}</span>{' '}
        <StatusBadge tone={STATUS_TONE[status]}>{text.badge[status]}</StatusBadge>
        {selected && <span className="visually-hidden">, {text.selected}</span>}
      </button>
      {text.status[status] && <p className="muted">{text.status[status]}</p>}
      {evidence.otherPaths.length > 0 && (
        <div className="evidence__other">
          <p className="muted">{text.otherPaths(evidence.otherPaths.length)}</p>
          <ul>
            {evidence.otherPaths.map((path) => (
              <li key={path}>
                <code>{path}</code>
              </li>
            ))}
          </ul>
        </div>
      )}
      {ranks.length > 0 && (
        <p className="muted">
          {text.ranks}: {ranks.join(', ')}
        </p>
      )}
    </li>
  );
}
