import { messages } from './messages';

const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const numberFormat = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
const relativeFormat = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' });
const BYTE_UNITS = ['bytes', 'KB', 'MB', 'GB'] as const;

export function formatDateTime(iso: string): string {
  return dateFormat.format(new Date(iso));
}

// Recent dates as "há 5 minutos" or "ontem"; a week or more falls back to the full date.
export function formatRelativeTime(iso: string, now: number = Date.now()): string {
  const seconds = (new Date(iso).getTime() - now) / 1000;
  if (Math.abs(seconds) < 45) {
    return messages.time.justNow;
  }
  const minutes = seconds / 60;
  if (Math.abs(minutes) < 60) {
    return relativeFormat.format(Math.round(minutes), 'minute');
  }
  const hours = minutes / 60;
  if (Math.abs(hours) < 24) {
    return relativeFormat.format(Math.round(hours), 'hour');
  }
  const days = hours / 24;
  if (Math.abs(days) < 7) {
    return relativeFormat.format(Math.round(days), 'day');
  }
  return formatDateTime(iso);
}

export function formatNumber(value: number): string {
  return numberFormat.format(value);
}

export function formatBytes(bytes: number): string {
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < BYTE_UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${numberFormat.format(value)} ${BYTE_UNITS[unit]}`;
}

export function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${numberFormat.format(ms / 1000)} s`;
}

export function formatCost(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency,
      maximumSignificantDigits: 3,
    }).format(value);
  } catch {
    // Not an ISO 4217 code: show the amount as configured on the server.
    return `${value} ${currency}`;
  }
}
