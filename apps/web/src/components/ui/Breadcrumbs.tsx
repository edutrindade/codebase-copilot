import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { messages } from '../../messages';
import './Breadcrumbs.css';

export interface Crumb {
  label: string;
  to?: string;
}

export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label={messages.breadcrumbs.label} className="breadcrumb-trail">
      <ol>
        {items.map((item, index) => {
          const last = index === items.length - 1;
          return (
            <li key={`${index}:${item.label}`}>
              {index > 0 && (
                <ChevronRight aria-hidden="true" className="breadcrumb-trail__separator" />
              )}
              {last || !item.to ? (
                <span aria-current={last ? 'page' : undefined}>{item.label}</span>
              ) : (
                <Link to={item.to}>{item.label}</Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
