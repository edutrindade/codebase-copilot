const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const numberFormat = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
const BYTE_UNITS = ['bytes', 'KB', 'MB', 'GB'] as const;

export function formatDateTime(iso: string): string {
  return dateFormat.format(new Date(iso));
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
