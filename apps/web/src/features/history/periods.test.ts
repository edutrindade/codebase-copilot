import { describe, expect, test } from 'vitest';
import { groupByPeriod } from './periods';

const now = new Date(2026, 9, 17, 15, 30).getTime();
const at = (day: number, hour = 12) => ({ updatedAt: new Date(2026, 9, day, hour).toISOString() });

describe('groupByPeriod', () => {
  test('splits by calendar day in local time, keeping the order', () => {
    const items = [at(17, 9), at(17, 0), at(16, 23), at(16, 0), at(11, 1), at(10, 23), at(1)];

    const groups = groupByPeriod(items, now);

    expect(groups.map((group) => [group.period, group.items])).toEqual([
      ['today', [items[0], items[1]]],
      ['yesterday', [items[2], items[3]]],
      ['lastWeek', [items[4]]],
      ['older', [items[5], items[6]]],
    ]);
  });

  test('leaves out empty periods', () => {
    expect(groupByPeriod([at(1)], now).map((group) => group.period)).toEqual(['older']);
    expect(groupByPeriod([], now)).toEqual([]);
  });
});
