export type AnswerSegment = { type: 'text'; text: string } | { type: 'citation'; label: string };

const MARKER = /\[(E\d+)\]/g;

// Markers become citations only when the answer carries that evidence; anything else stays text.
export function answerSegments(answer: string, labels: ReadonlySet<string>): AnswerSegment[] {
  const segments: AnswerSegment[] = [];
  let last = 0;
  for (const match of answer.matchAll(MARKER)) {
    const label = match[1];
    if (label === undefined || !labels.has(label)) {
      continue;
    }
    if (match.index > last) {
      segments.push({ type: 'text', text: answer.slice(last, match.index) });
    }
    segments.push({ type: 'citation', label });
    last = match.index + match[0].length;
  }
  if (last < answer.length) {
    segments.push({ type: 'text', text: answer.slice(last) });
  }
  return segments;
}
