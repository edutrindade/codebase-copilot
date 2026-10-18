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

export interface EvidenceGroup {
  path: string;
  evidence: Evidence[];
}

// Groups follow the first item of each file in sortEvidence order.
export function groupByFile(evidence: readonly Evidence[]): EvidenceGroup[] {
  const groups = new Map<string, Evidence[]>();
  for (const item of sortEvidence(evidence)) {
    const group = groups.get(item.path);
    if (group) {
      group.push(item);
    } else {
      groups.set(item.path, [item]);
    }
  }
  return [...groups].map(([path, items]) => ({ path, evidence: items }));
}

// The order of the evidence list, which [ and ] walk.
export function listOrder(evidence: readonly Evidence[]): Evidence[] {
  return groupByFile(evidence).flatMap((group) => group.evidence);
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
