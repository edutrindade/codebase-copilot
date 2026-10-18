import { describe, expect, test } from 'vitest';
import { formatAnswer } from './answerFormat';

const labels = new Set(['E1', 'E2']);

describe('formatAnswer', () => {
  test('splits paragraphs on blank lines and keeps single breaks inside them', () => {
    expect(formatAnswer('Primeira\nlinha\n\nSegunda', labels)).toEqual([
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'Primeira' },
          { type: 'break' },
          { type: 'text', text: 'linha' },
        ],
      },
      { type: 'paragraph', content: [{ type: 'text', text: 'Segunda' }] },
    ]);
  });

  test('turns the markers of known evidence into citations', () => {
    expect(formatAnswer('Total [E1][E2], taxa [E1].', labels)).toEqual([
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'Total ' },
          { type: 'citation', label: 'E1' },
          { type: 'citation', label: 'E2' },
          { type: 'text', text: ', taxa ' },
          { type: 'citation', label: 'E1' },
          { type: 'text', text: '.' },
        ],
      },
    ]);
  });

  test('keeps unknown markers and look-alikes as text', () => {
    expect(formatAnswer('Veja [E9], [e1] e [E1 ].', labels)).toEqual([
      { type: 'paragraph', content: [{ type: 'text', text: 'Veja [E9], [e1] e [E1 ].' }] },
    ]);
  });

  test('reads bullet and numbered lists, with continuation lines in the last item', () => {
    expect(
      formatAnswer('Passos:\n3. valida [E1]\n4. grava\n   no banco\n\n- um\n* dois', labels),
    ).toEqual([
      { type: 'paragraph', content: [{ type: 'text', text: 'Passos:' }] },
      {
        type: 'list',
        ordered: true,
        start: 3,
        items: [
          [
            { type: 'text', text: 'valida ' },
            { type: 'citation', label: 'E1' },
          ],
          [{ type: 'text', text: 'grava' }, { type: 'break' }, { type: 'text', text: 'no banco' }],
        ],
      },
      {
        type: 'list',
        ordered: false,
        start: 1,
        items: [[{ type: 'text', text: 'um' }], [{ type: 'text', text: 'dois' }]],
      },
    ]);
  });

  test('reads inline code and bold, with citations and code inside the bold', () => {
    expect(formatAnswer('Use `refund(id)` **sempre `antes` [E2]** e ****.', labels)).toEqual([
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'Use ' },
          { type: 'code', text: 'refund(id)' },
          { type: 'text', text: ' ' },
          {
            type: 'strong',
            children: [
              { type: 'text', text: 'sempre ' },
              { type: 'code', text: 'antes' },
              { type: 'text', text: ' ' },
              { type: 'citation', label: 'E2' },
            ],
          },
          { type: 'text', text: ' e ****.' },
        ],
      },
    ]);
  });

  test('keeps fenced code verbatim, markers included, and closes an unterminated fence at the end', () => {
    expect(
      formatAnswer('```java\nint a = 1; // [E1]\n\n  **x**\n```\nDepois\n```bad lang\nfim', labels),
    ).toEqual([
      { type: 'code', language: 'java', code: 'int a = 1; // [E1]\n\n  **x**' },
      { type: 'paragraph', content: [{ type: 'text', text: 'Depois' }] },
      { type: 'code', language: null, code: 'fim' },
    ]);
  });

  test('headings become plain paragraphs', () => {
    expect(formatAnswer('## Resumo ##\nTexto', labels)).toEqual([
      { type: 'paragraph', content: [{ type: 'text', text: 'Resumo' }] },
      { type: 'paragraph', content: [{ type: 'text', text: 'Texto' }] },
    ]);
  });

  test('links, images, HTML and tables have no node of their own and stay text', () => {
    const hostile = [
      '[clique](javascript:alert(1)) ![x](https://evil.example/x.png)',
      '<a href="https://evil.example">[E1]</a> <img src=x onerror=alert(1)>',
      '| a | b |',
      '|---|---|',
    ].join('\n');
    const blocks = formatAnswer(hostile, labels);
    expect(blocks).toEqual([
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: '[clique](javascript:alert(1)) ![x](https://evil.example/x.png)' },
          { type: 'break' },
          { type: 'text', text: '<a href="https://evil.example">' },
          { type: 'citation', label: 'E1' },
          { type: 'text', text: '</a> <img src=x onerror=alert(1)>' },
          { type: 'break' },
          { type: 'text', text: '| a | b |' },
          { type: 'break' },
          { type: 'text', text: '|---|---|' },
        ],
      },
    ]);
  });
});
