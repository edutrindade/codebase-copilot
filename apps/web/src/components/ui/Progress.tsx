import { Progress as RadixProgress } from 'radix-ui';
import { cx } from './cx';
import './Progress.css';

interface ProgressProps {
  // null: indeterminate.
  value: number | null;
  max?: number;
  label: string;
  className?: string;
}

export function Progress({ value, max = 100, label, className }: ProgressProps) {
  const percent = value === null || max <= 0 ? 0 : Math.min(100, (value / max) * 100);
  return (
    <RadixProgress.Root
      className={cx('progress', className)}
      value={value}
      max={max}
      aria-label={label}
    >
      <RadixProgress.Indicator
        className="progress__indicator"
        style={value === null ? undefined : { transform: `translateX(-${100 - percent}%)` }}
      />
    </RadixProgress.Root>
  );
}
