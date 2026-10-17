import { describe, expect, test } from 'vitest';
import { formatBytes, formatNumber } from './format';

describe('format', () => {
  test('formats bytes with binary units in pt-BR', () => {
    expect(formatBytes(0)).toBe('0 bytes');
    expect(formatBytes(1023)).toBe('1.023 bytes');
    expect(formatBytes(1536)).toBe('1,5 KB');
    expect(formatBytes(1024 * 1024)).toBe('1 MB');
    expect(formatBytes(2 * 1024 ** 3)).toBe('2 GB');
  });

  test('formats counts in pt-BR', () => {
    expect(formatNumber(12345)).toBe('12.345');
  });
});
