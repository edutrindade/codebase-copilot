export type HighlightLanguage =
  | 'java'
  | 'javascript'
  | 'jsx'
  | 'typescript'
  | 'tsx'
  | 'python'
  | 'go'
  | 'rust'
  | 'json'
  | 'yaml'
  | 'xml'
  | 'html'
  | 'css'
  | 'markdown';

// File extensions and the names used after ``` in answers. SQL and the rest stay plain (F0).
const NAMES: Record<string, HighlightLanguage> = {
  java: 'java',
  js: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  javascript: 'javascript',
  jsx: 'jsx',
  ts: 'typescript',
  mts: 'typescript',
  cts: 'typescript',
  typescript: 'typescript',
  tsx: 'tsx',
  py: 'python',
  python: 'python',
  go: 'go',
  golang: 'go',
  rs: 'rust',
  rust: 'rust',
  json: 'json',
  yml: 'yaml',
  yaml: 'yaml',
  xml: 'xml',
  svg: 'xml',
  html: 'html',
  htm: 'html',
  css: 'css',
  md: 'markdown',
  markdown: 'markdown',
};

export function languageForPath(path: string): HighlightLanguage | null {
  const name = path.slice(path.lastIndexOf('/') + 1).toLowerCase();
  const dot = name.lastIndexOf('.');
  return dot > 0 ? (NAMES[name.slice(dot + 1)] ?? null) : null;
}

export function languageForName(name: string | null): HighlightLanguage | null {
  return name ? (NAMES[name.toLowerCase()] ?? null) : null;
}
