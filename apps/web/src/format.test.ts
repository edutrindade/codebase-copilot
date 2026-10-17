import { describe, expect, test } from 'vitest';
import {
  formatBytes,
  formatCost,
  formatDateTime,
  formatDuration,
  formatNumber,
  formatRelativeTime,
} from './format';
import { messages } from './messages';

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

describe('formatRelativeTime', () => {
  const now = Date.parse('2026-10-17T12:00:00Z');
  const ago = (ms: number) => new Date(now - ms).toISOString();

  test('says "just now" under a minute', () => {
    expect(formatRelativeTime(ago(30_000), now)).toBe(messages.time.justNow);
  });

  test('uses minutes, hours and days within a week', () => {
    expect(formatRelativeTime(ago(5 * 60_000), now)).toBe('há 5 minutos');
    expect(formatRelativeTime(ago(2 * 3_600_000), now)).toBe('há 2 horas');
    expect(formatRelativeTime(ago(26 * 3_600_000), now)).toBe('ontem');
    expect(formatRelativeTime(ago(3 * 86_400_000), now)).toBe('há 3 dias');
  });

  test('falls back to the full date after a week', () => {
    const iso = ago(10 * 86_400_000);
    expect(formatRelativeTime(iso, now)).toBe(formatDateTime(iso));
  });
});
