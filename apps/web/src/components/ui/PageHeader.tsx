import type { ReactNode } from 'react';
import './PageHeader.css';

interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <header className="page-heading">
      <div className="page-heading__row">
        <div className="page-heading__text">
          <h1>{title}</h1>
          {description && <div className="page-heading__description">{description}</div>}
        </div>
        {actions && <div className="page-heading__actions">{actions}</div>}
      </div>
    </header>
  );
}
