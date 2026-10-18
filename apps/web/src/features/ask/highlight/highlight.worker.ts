import type { HighlightLanguage } from './languages';
import { tokenize } from './tokenize';

export interface HighlightRequest {
  id: number;
  language: HighlightLanguage;
  text: string;
}

export type HighlightResponse =
  { id: number; ranges: Int32Array; classes: string[] } | { id: number; failed: true };

self.onmessage = async (event: MessageEvent<HighlightRequest>) => {
  const { id, language, text } = event.data;
  try {
    const { ranges, classes } = await tokenize(language, text);
    self.postMessage({ id, ranges, classes } satisfies HighlightResponse, {
      transfer: [ranges.buffer],
    });
  } catch {
    self.postMessage({ id, failed: true } satisfies HighlightResponse);
  }
};
