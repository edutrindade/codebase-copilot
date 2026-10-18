export type Period = 'today' | 'yesterday' | 'lastWeek' | 'older';

export interface PeriodGroup<T> {
  period: Period;
  items: T[];
}

const DAY_MS = 24 * 60 * 60 * 1000;

function startOfDay(time: number): number {
  const date = new Date(time);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

// Calendar days in local time; the items keep their order inside each group.
export function groupByPeriod<T extends { updatedAt: string }>(
  items: readonly T[],
  now: number = Date.now(),
): PeriodGroup<T>[] {
  const today = startOfDay(now);
  const yesterday = startOfDay(today - DAY_MS / 2);
  const lastWeek = startOfDay(today - 6 * DAY_MS + DAY_MS / 2);
  const groups = new Map<Period, T[]>();
  for (const item of items) {
    const time = new Date(item.updatedAt).getTime();
    const period: Period =
      time >= today
        ? 'today'
        : time >= yesterday
          ? 'yesterday'
          : time >= lastWeek
            ? 'lastWeek'
            : 'older';
    groups.set(period, [...(groups.get(period) ?? []), item]);
  }
  return (['today', 'yesterday', 'lastWeek', 'older'] as const)
    .filter((period) => groups.has(period))
    .map((period) => ({ period, items: groups.get(period) ?? [] }));
}
