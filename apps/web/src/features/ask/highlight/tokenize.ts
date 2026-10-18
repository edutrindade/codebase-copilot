import type { Parser } from '@lezer/common';
import { classHighlighter, highlightTree } from '@lezer/highlight';
import type { HighlightLanguage } from './languages';

// One chunk per language, loaded the first time it is needed.
async function loadParser(language: HighlightLanguage): Promise<Parser> {
  switch (language) {
    case 'java':
      return (await import('@lezer/java')).parser;
    case 'javascript':
      return (await import('@lezer/javascript')).parser.configure({ dialect: 'jsx' });
    case 'jsx':
      return (await import('@lezer/javascript')).parser.configure({ dialect: 'jsx' });
    case 'typescript':
      return (await import('@lezer/javascript')).parser.configure({ dialect: 'ts' });
    case 'tsx':
      return (await import('@lezer/javascript')).parser.configure({ dialect: 'ts jsx' });
    case 'python':
      return (await import('@lezer/python')).parser;
    case 'go':
      return (await import('@lezer/go')).parser;
    case 'rust':
      return (await import('@lezer/rust')).parser;
    case 'json':
      return (await import('@lezer/json')).parser;
    case 'yaml':
      return (await import('@lezer/yaml')).parser;
    case 'xml':
      return (await import('@lezer/xml')).parser;
    case 'html':
      return (await import('@lezer/html')).parser;
    case 'css':
      return (await import('@lezer/css')).parser;
    case 'markdown':
      return (await import('@lezer/markdown')).parser;
  }
}

export interface Tokens {
  // Triples of start offset, end offset and index into classes, in text order.
  ranges: Int32Array;
  classes: string[];
}

// Only offsets and class names come back: nothing from the text becomes markup.
export async function tokenize(language: HighlightLanguage, text: string): Promise<Tokens> {
  const parser = await loadParser(language);
  const tree = parser.parse(text);
  const ranges: number[] = [];
  const classes: string[] = [];
  const indexes = new Map<string, number>();
  highlightTree(tree, classHighlighter, (from, to, names) => {
    let index = indexes.get(names);
    if (index === undefined) {
      index = classes.length;
      classes.push(names);
      indexes.set(names, index);
    }
    ranges.push(from, to, index);
  });
  return { ranges: Int32Array.from(ranges), classes };
}
