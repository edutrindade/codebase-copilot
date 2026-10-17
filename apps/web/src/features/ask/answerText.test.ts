import { describe, expect, test } from 'vitest';
import { answerSegments } from './answerText';

describe('answerSegments', () => {
  test('turns the markers of known evidence into citations', () => {
    expect(answerSegments('Total [E1][E2], taxa [E1].', new Set(['E1', 'E2']))).toEqual([
      { type: 'text', text: 'Total ' },
      { type: 'citation', label: 'E1' },
      { type: 'citation', label: 'E2' },
      { type: 'text', text: ', taxa ' },
      { type: 'citation', label: 'E1' },
      { type: 'text', text: '.' },
    ]);
  });

  test('keeps unknown markers and look-alikes as text', () => {
    expect(answerSegments('Veja [E9], [e1] e [E1 ].', new Set(['E1']))).toEqual([
      { type: 'text', text: 'Veja [E9], [e1] e [E1 ].' },
    ]);
  });

  test('keeps markup from the model as plain text', () => {
    expect(answerSegments('<a href="x">[E1]</a>', new Set(['E1']))).toEqual([
      { type: 'text', text: '<a href="x">' },
      { type: 'citation', label: 'E1' },
      { type: 'text', text: '</a>' },
    ]);
  });
});
