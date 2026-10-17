import type { Evidence, EvidenceDetail } from './api';

const ORDER: Record<Evidence['validationStatus'], number> = {
  CITED: 0,
  INVALID_RANGE: 1,
  NOT_CITED: 2,
};

export function sortEvidence(evidence: readonly Evidence[]): Evidence[] {
  return [...evidence].sort(
    (a, b) => ORDER[a.validationStatus] - ORDER[b.validationStatus] || a.rank - b.rank,
  );
}

export interface LineRange {
  start: number;
  end: number;
}

export function highlightedRange(evidence: Evidence | EvidenceDetail): LineRange {
  if (evidence.citedStartLine !== null && evidence.citedEndLine !== null) {
    return { start: evidence.citedStartLine, end: evidence.citedEndLine };
  }
  return { start: evidence.startLine, end: evidence.endLine };
}

// The content keeps its original line breaks; a final break does not open another line.
export function splitLines(content: string): string[] {
  const lines = content.split(/\r\n|\r|\n/);
  if (lines.length > 1 && lines[lines.length - 1] === '') {
    lines.pop();
  }
  return lines;
}
