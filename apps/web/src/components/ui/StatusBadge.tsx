import {
  Circle,
  CircleCheck,
  CircleDashed,
  CircleX,
  Info,
  LoaderCircle,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { cx } from './cx';
import './StatusBadge.css';

export type Tone = 'neutral' | 'accent' | 'progress' | 'success' | 'warning' | 'danger' | 'info';

const ICONS: Record<Tone, LucideIcon> = {
  neutral: CircleDashed,
  accent: Circle,
  progress: LoaderCircle,
  success: CircleCheck,
  warning: TriangleAlert,
  danger: CircleX,
  info: Info,
};

interface StatusBadgeProps {
  tone?: Tone;
  icon?: LucideIcon;
  children: ReactNode;
  className?: string;
}

// The text carries the meaning; color and icon only reinforce it (03 §8).
export function StatusBadge({ tone = 'neutral', icon, children, className }: StatusBadgeProps) {
  const Icon = icon ?? ICONS[tone];
  return (
    <span className={cx('badge', `badge--${tone}`, className)}>
      <Icon aria-hidden="true" className="badge__icon" />
      {children}
    </span>
  );
}
