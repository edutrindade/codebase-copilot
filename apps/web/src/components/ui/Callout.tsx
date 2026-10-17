import { CircleAlert, CircleCheck, Info, TriangleAlert, type LucideIcon } from 'lucide-react';
import type { HTMLAttributes, ReactNode } from 'react';
import { cx } from './cx';
import './Callout.css';

export type CalloutTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

const ICONS: Record<CalloutTone, LucideIcon> = {
  neutral: Info,
  info: Info,
  success: CircleCheck,
  warning: TriangleAlert,
  danger: CircleAlert,
};

interface CalloutProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  tone?: CalloutTone;
  title?: ReactNode;
  // null hides the icon.
  icon?: LucideIcon | null;
  actions?: ReactNode;
  as?: 'div' | 'section' | 'li';
}

export function Callout({
  tone = 'info',
  title,
  icon,
  actions,
  as: Element = 'div',
  className,
  children,
  ...rest
}: CalloutProps) {
  const Icon = icon === undefined ? ICONS[tone] : icon;
  return (
    <Element className={cx('callout', `callout--${tone}`, className)} {...rest}>
      {Icon && <Icon aria-hidden="true" className="callout__icon" />}
      <div className="callout__body">
        {title && <p className="callout__title">{title}</p>}
        {children}
        {actions && <div className="callout__actions">{actions}</div>}
      </div>
    </Element>
  );
}
