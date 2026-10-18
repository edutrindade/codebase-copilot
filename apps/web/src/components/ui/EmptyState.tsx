import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import './EmptyState.css';

interface EmptyStateProps {
  icon?: LucideIcon;
  // Decorative, shown instead of the icon.
  illustration?: ReactNode;
  title: string;
  headingLevel?: 1 | 2 | 3;
  children?: ReactNode;
  action?: ReactNode;
}

export function EmptyState({
  icon: Icon,
  illustration,
  title,
  headingLevel = 2,
  children,
  action,
}: EmptyStateProps) {
  const Heading = (['h1', 'h2', 'h3'] as const)[headingLevel - 1] ?? 'h2';
  return (
    <div className="empty-state">
      {illustration ??
        (Icon && (
          <div className="empty-state__icon">
            <Icon aria-hidden="true" />
          </div>
        ))}
      <Heading className="empty-state__title">{title}</Heading>
      {children && <div className="empty-state__description">{children}</div>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
