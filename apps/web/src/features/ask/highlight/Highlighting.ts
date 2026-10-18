import type { Tokens } from './tokenize';

export interface Segment {
  text: string;
  className: string | null;
}

// Same line breaks as splitLines, so line i here is line i in the viewer.
const LINE_BREAK = /\r\n|\r|\n/g;

export class Highlighting {
  private readonly starts: number[] = [0];
  private readonly ends: number[] = [];

  constructor(
    private readonly text: string,
    private readonly tokens: Tokens,
  ) {
    for (const match of text.matchAll(LINE_BREAK)) {
      this.ends.push(match.index);
      this.starts.push(match.index + match[0].length);
    }
    this.ends.push(text.length);
  }

  // Tokens may span lines (block comments); each line gets its own slice.
  segments(line: number): Segment[] {
    const start = this.starts[line];
    const end = this.ends[line];
    if (start === undefined || end === undefined) {
      return [];
    }
    const { ranges, classes } = this.tokens;
    const segments: Segment[] = [];
    let position = start;
    for (let token = this.firstEndingAfter(start); token < ranges.length / 3; token++) {
      const from = Math.max(ranges[token * 3] ?? 0, start);
      const to = Math.min(ranges[token * 3 + 1] ?? 0, end);
      if (from >= end) {
        break;
      }
      if (from > position) {
        segments.push({ text: this.text.slice(position, from), className: null });
      }
      if (to > from) {
        segments.push({
          text: this.text.slice(from, to),
          className: classes[ranges[token * 3 + 2] ?? 0] ?? null,
        });
        position = to;
      }
    }
    if (position < end) {
      segments.push({ text: this.text.slice(position, end), className: null });
    }
    return segments;
  }

  private firstEndingAfter(offset: number): number {
    const { ranges } = this.tokens;
    let low = 0;
    let high = ranges.length / 3;
    while (low < high) {
      const middle = (low + high) >> 1;
      if ((ranges[middle * 3 + 1] ?? 0) <= offset) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    return low;
  }
}
