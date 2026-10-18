import type { ReactNode } from 'react';
import { cx } from './cx';
import './Stat.css';

export function StatGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <dl className={cx('stat-grid', className)}>{children}</dl>;
}

interface StatProps {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
}

export function Stat({ label, value, hint }: StatProps) {
  return (
    <div className="stat">
      <dt className="stat__label">{label}</dt>
      <dd className="stat__value">{value}</dd>
      {hint && <dd className="stat__hint">{hint}</dd>}
    </div>
  );
}
