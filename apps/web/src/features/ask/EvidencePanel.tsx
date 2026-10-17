import { messages } from '../../messages';
import type { Answer, Evidence } from './api';
import { highlightedRange, sortEvidence } from './evidence';

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
  return (
    <>
      <p className="muted">{text.forAnswer(answer.question)}</p>
      <ol className="evidence-list">
        {sortEvidence(answer.evidence).map((evidence) => (
          <EvidenceItem
            key={evidence.id}
            evidence={evidence}
            selected={evidence.id === selectedEvidenceId}
            onOpen={onOpen}
          />
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
  const range = highlightedRange(evidence);
  const ranks = [
    evidence.ranks.lexical !== null && text.rankLexical(evidence.ranks.lexical),
    evidence.ranks.vector !== null && text.rankVector(evidence.ranks.vector),
    evidence.ranks.path !== null && text.rankPath(evidence.ranks.path),
  ].filter((rank): rank is string => typeof rank === 'string');
  const status = evidence.validationStatus;

  return (
    <li
      className={`evidence evidence--${status.toLowerCase()}${selected ? ' evidence--selected' : ''}`}
      aria-current={selected ? 'true' : undefined}
    >
      <p className="evidence__title">
        <span className="viewer__label">{evidence.citationLabel}</span> <code>{evidence.path}</code>
      </p>
      <p>
        {messages.ask.lines(range.start, range.end)} · {text.status[status]}
        {selected && <span className="muted"> · {text.selected}</span>}
      </p>
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
      <div>
        <button type="button" className="button" onClick={() => onOpen(evidence)}>
          {text.open(evidence.citationLabel)}
        </button>
      </div>
    </li>
  );
}
