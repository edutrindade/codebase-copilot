import { describe, expect, test } from 'vitest';
import { formatBytes, formatCost, formatDuration, formatNumber } from './format';

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

  test('formats durations in milliseconds below one second', () => {
    expect(formatDuration(120)).toBe('120 ms');
    expect(formatDuration(1840)).toBe('1,8 s');
  });

  test('formats small costs with significant digits and tolerates unknown currencies', () => {
    expect(formatCost(0.000611, 'USD')).toBe('US$\u00a00,000611');
    expect(formatCost(1.5, 'créditos')).toBe('1.5 créditos');
  });
});
