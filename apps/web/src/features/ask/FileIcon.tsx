import { createElement } from 'react';
import {
  File,
  FileCode,
  FileCog,
  FileJson,
  FileTerminal,
  FileText,
  type LucideIcon,
} from 'lucide-react';

const BY_EXTENSION: Record<string, LucideIcon> = {
  json: FileJson,
  md: FileText,
  txt: FileText,
  rst: FileText,
  adoc: FileText,
  yml: FileCog,
  yaml: FileCog,
  toml: FileCog,
  properties: FileCog,
  ini: FileCog,
  xml: FileCog,
  env: FileCog,
  sh: FileTerminal,
  bash: FileTerminal,
  zsh: FileTerminal,
  ps1: FileTerminal,
};

const CODE = new Set([
  'java',
  'kt',
  'kts',
  'scala',
  'groovy',
  'js',
  'jsx',
  'mjs',
  'cjs',
  'ts',
  'tsx',
  'py',
  'go',
  'rs',
  'rb',
  'php',
  'c',
  'h',
  'cc',
  'cpp',
  'hpp',
  'cs',
  'swift',
  'm',
  'sql',
  'html',
  'css',
  'scss',
  'vue',
  'svelte',
  'dart',
  'lua',
  'r',
  'ex',
  'exs',
  'clj',
]);

function iconFor(path: string): LucideIcon {
  const name = path.slice(path.lastIndexOf('/') + 1).toLowerCase();
  const extension = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1) : '';
  if (name === 'dockerfile' || name === 'makefile') {
    return FileTerminal;
  }
  return BY_EXTENSION[extension] ?? (CODE.has(extension) ? FileCode : File);
}

// Decorative only: the path next to it says what the file is.
export function FileIcon({ path, className }: { path: string; className: string }) {
  return createElement(iconFor(path), { 'aria-hidden': true, className });
}
