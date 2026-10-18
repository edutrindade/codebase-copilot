// Light formatting of answers (D-091): paragraphs, flat lists, inline code, fenced code and bold.
// The result is data rendered as React elements; links, images, HTML, tables and headings have no
// node type, so they can only come out as text.

export type Inline =
  | { type: 'text'; text: string }
  | { type: 'code'; text: string }
  | { type: 'strong'; children: Inline[] }
  | { type: 'citation'; label: string }
  | { type: 'break' };

export type Block =
  | { type: 'paragraph'; content: Inline[] }
  | { type: 'list'; ordered: boolean; start: number; items: Inline[][] }
  | { type: 'code'; language: string | null; code: string };

const FENCE_OPEN = /^ {0,3}```(.*)$/;
const FENCE_CLOSE = /^ {0,3}```\s*$/;
const LANGUAGE = /^[\w+#.-]{1,32}$/;
const BULLET = /^\s*[-*]\s+(.*)$/;
const NUMBERED = /^\s*(\d{1,9})\.\s+(.*)$/;
const HEADING = /^ {0,3}#{1,6}\s+(.*?)\s*#*\s*$/;
const INLINE = /`([^`]+)`|\*\*(.+?)\*\*|\[(E\d+)\]/g;
const INSIDE_STRONG = /`([^`]+)`|\[(E\d+)\]/g;

export function formatAnswer(answer: string, labels: ReadonlySet<string>): Block[] {
  const blocks: Block[] = [];
  const lines = answer.split(/\r\n|\r|\n/);
  let open: Extract<Block, { type: 'paragraph' | 'list' }> | null = null;

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index] ?? '';
    const fence = FENCE_OPEN.exec(line);
    if (fence) {
      open = null;
      const code: string[] = [];
      index++;
      while (index < lines.length && !FENCE_CLOSE.test(lines[index] ?? '')) {
        code.push(lines[index] ?? '');
        index++;
      }
      const info = (fence[1] ?? '').trim();
      blocks.push({
        type: 'code',
        language: LANGUAGE.test(info) ? info : null,
        code: code.join('\n'),
      });
      continue;
    }
    if (line.trim() === '') {
      open = null;
      continue;
    }
    const bullet = BULLET.exec(line);
    const numbered = bullet ? null : NUMBERED.exec(line);
    if (bullet || numbered) {
      const ordered = numbered !== null;
      const item = inlines((bullet?.[1] ?? numbered?.[2] ?? '').trim(), labels);
      if (open?.type === 'list' && open.ordered === ordered) {
        open.items.push(item);
      } else {
        open = { type: 'list', ordered, start: numbered ? Number(numbered[1]) : 1, items: [item] };
        blocks.push(open);
      }
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ type: 'paragraph', content: inlines(heading[1] ?? '', labels) });
      open = null;
      continue;
    }
    const content = inlines(line.trim(), labels);
    if (open?.type === 'list') {
      open.items[open.items.length - 1]?.push({ type: 'break' }, ...content);
    } else if (open?.type === 'paragraph') {
      open.content.push({ type: 'break' }, ...content);
    } else {
      open = { type: 'paragraph', content };
      blocks.push(open);
    }
  }
  return blocks;
}

// Markers become citations only when the answer carries that evidence; anything else stays text.
function inlines(text: string, labels: ReadonlySet<string>): Inline[] {
  const result: Inline[] = [];
  let last = 0;
  for (const match of text.matchAll(INLINE)) {
    const [whole, code, strong, label] = match;
    let node: Inline | null = null;
    if (code !== undefined) {
      node = { type: 'code', text: code };
    } else if (strong !== undefined && strong.trim() !== '') {
      node = { type: 'strong', children: strongChildren(strong, labels) };
    } else if (label !== undefined && labels.has(label)) {
      node = { type: 'citation', label };
    }
    if (node === null) {
      continue;
    }
    pushText(result, text.slice(last, match.index));
    result.push(node);
    last = match.index + whole.length;
  }
  pushText(result, text.slice(last));
  return result;
}

function strongChildren(text: string, labels: ReadonlySet<string>): Inline[] {
  const result: Inline[] = [];
  let last = 0;
  for (const match of text.matchAll(INSIDE_STRONG)) {
    const [whole, code, label] = match;
    if (code === undefined && (label === undefined || !labels.has(label))) {
      continue;
    }
    pushText(result, text.slice(last, match.index));
    result.push(
      code !== undefined ? { type: 'code', text: code } : { type: 'citation', label: label ?? '' },
    );
    last = match.index + whole.length;
  }
  pushText(result, text.slice(last));
  return result;
}

function pushText(result: Inline[], text: string) {
  if (text === '') {
    return;
  }
  const previous = result[result.length - 1];
  if (previous?.type === 'text') {
    previous.text += text;
  } else {
    result.push({ type: 'text', text });
  }
}
