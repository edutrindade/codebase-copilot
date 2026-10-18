// @vitest-environment node
import { describe, expect, test } from 'vitest';
import { Highlighting } from './Highlighting';
import { languageForName, languageForPath, type HighlightLanguage } from './languages';
import { tokenize } from './tokenize';

async function classesOf(language: HighlightLanguage, text: string) {
  const highlighting = new Highlighting(text, await tokenize(language, text));
  return splitLines(text).map((_, line) =>
    highlighting
      .segments(line)
      .filter((segment) => segment.className)
      .map((segment) => `${segment.text}:${segment.className}`),
  );
}

function splitLines(text: string) {
  return text.split(/\r\n|\r|\n/);
}

describe('highlighting', () => {
  test('maps paths and fence names to the supported languages only', () => {
    expect(languageForPath('src/App.java')).toBe('java');
    expect(languageForPath('web/ui/Page.TSX')).toBe('tsx');
    expect(languageForPath('docs/README.md')).toBe('markdown');
    expect(languageForPath('db/V1__init.sql')).toBeNull();
    expect(languageForPath('Makefile')).toBeNull();
    expect(languageForPath('.env')).toBeNull();
    expect(languageForName('TypeScript')).toBe('typescript');
    expect(languageForName('sql')).toBeNull();
    expect(languageForName(null)).toBeNull();
  });

  test('colors Java keywords, strings, numbers and comments, line by line', async () => {
    const lines = await classesOf(
      'java',
      'class A {\r\n  String s = "x"; // note\n  int n = 42;\n}\n',
    );

    expect(lines[0]).toContain('class:tok-keyword');
    expect(lines[1]).toEqual(expect.arrayContaining(['"x":tok-string', '// note:tok-comment']));
    expect(lines[2]).toContain('42:tok-number');
  });

  test('a token across lines is split at each line break', async () => {
    const text = 'let a = 1; /* one\ntwo */ let b = 2;';
    const highlighting = new Highlighting(text, await tokenize('typescript', text));

    expect(
      highlighting
        .segments(0)
        .map((segment) => segment.text)
        .join(''),
    ).toBe('let a = 1; /* one');
    expect(highlighting.segments(1)[0]).toEqual({ text: 'two */', className: 'tok-comment' });
  });

  test('markup in the content stays text: only offsets and class names come back', async () => {
    const text = '<script>alert(1)</script><img src=x onerror=alert(2)>';
    const tokens = await tokenize('html', text);
    const highlighting = new Highlighting(text, tokens);

    expect(
      highlighting
        .segments(0)
        .map((segment) => segment.text)
        .join(''),
    ).toBe(text);
    expect(tokens.classes.every((name) => /^(tok-[a-zA-Z0-9]+ ?)+$/.test(name))).toBe(true);
  });

  test('every language loads and keeps the text intact', async () => {
    const samples: Record<HighlightLanguage, string> = {
      java: 'class A {}',
      javascript: 'const a = <b>{1}</b>;',
      jsx: 'const a = <b />;',
      typescript: 'type A = { a: number };',
      tsx: 'const a: number = <b />;',
      python: 'def f():\n    return "x"',
      go: 'package main\nfunc main() {}',
      rust: 'fn main() { let x = 1; }',
      json: '{"a": [1, true, null]}',
      yaml: 'a: 1\nb: "x"',
      xml: '<a b="c"/>',
      html: '<p class="x">y</p><style>p{}</style>',
      css: 'p { color: red; }',
      markdown: '# Title\n\n**bold** and `code`',
    };
    for (const [language, text] of Object.entries(samples)) {
      const tokens = await tokenize(language as HighlightLanguage, text);
      const highlighting = new Highlighting(text, tokens);
      const rebuilt = splitLines(text)
        .map((_, line) =>
          highlighting
            .segments(line)
            .map((segment) => segment.text)
            .join(''),
        )
        .join('\n');
      expect(rebuilt, language).toBe(text);
      expect(tokens.ranges.length, language).toBeGreaterThan(0);
    }
  });
});
