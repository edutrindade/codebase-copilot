import { describe, expect, test } from 'vitest';
import { evidence } from '../../test/fixtures';
import { answerRecovery } from './answerRecovery';
import { highlightedRange, sortEvidence, splitLines } from './evidence';

describe('evidence', () => {
  test('lists cited evidence first, then invalid ranges, then the rest, each by rank', () => {
    const sorted = sortEvidence([
      evidence({ citationLabel: 'E1', rank: 1, validationStatus: 'NOT_CITED' }),
      evidence({ citationLabel: 'E2', rank: 2, validationStatus: 'INVALID_RANGE' }),
      evidence({ citationLabel: 'E3', rank: 4, validationStatus: 'CITED' }),
      evidence({ citationLabel: 'E4', rank: 3, validationStatus: 'CITED' }),
    ]);

    expect(sorted.map((item) => item.citationLabel)).toEqual(['E4', 'E3', 'E2', 'E1']);
  });

  test('highlights the cited lines, or the whole chunk without them', () => {
    expect(highlightedRange(evidence())).toEqual({ start: 12, end: 14 });
    expect(highlightedRange(evidence({ citedStartLine: null, citedEndLine: null }))).toEqual({
      start: 1,
      end: 40,
    });
  });

  test('splits on any line break without an extra line for the final one', () => {
    expect(splitLines('a\r\nb\rc\nd\n')).toEqual(['a', 'b', 'c', 'd']);
    expect(splitLines('a\n\n')).toEqual(['a', '']);
    expect(splitLines('')).toEqual(['']);
  });

  test('maps answer error codes to an action, asking again for unknown ones', () => {
    expect(answerRecovery('GENERATION_PROVIDER_UNAUTHORIZED')).toBe('configureProvider');
    expect(answerRecovery('GENERATION_RATE_LIMITED')).toBe('providerLimit');
    expect(answerRecovery('EMBEDDING_QUOTA_EXCEEDED')).toBe('providerLimit');
    expect(answerRecovery('ANSWER_INTERRUPTED')).toBe('askAgain');
    expect(answerRecovery('SOMETHING_NEW')).toBe('askAgain');
  });
});
