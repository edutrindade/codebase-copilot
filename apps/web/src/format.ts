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
