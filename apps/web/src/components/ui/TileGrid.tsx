import { Ellipsis, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { messages } from '../../messages';
import { IconButton } from './IconButton';
import { Menu } from './Menu';
import { Skeleton } from './Skeleton';
import './TileGrid.css';

export function TileGrid({ children }: { children: ReactNode }) {
  return <ul className="tile-grid">{children}</ul>;
}

interface TileProps {
  to: string;
  title: string;
  icon: LucideIcon;
  menu?: ReactNode;
  children?: ReactNode;
}

// The whole card is clickable through the title link; the menu and tooltips sit above it.
export function Tile({ to, title, icon: Icon, menu, children }: TileProps) {
  return (
    <li className="tile">
      <div className="tile__head">
        <span className="tile__icon">
          <Icon aria-hidden="true" />
        </span>
        <Link to={to} className="tile__link" viewTransition>
          {title}
        </Link>
        {menu && <div className="tile__raised">{menu}</div>}
      </div>
      {children && <div className="tile__body">{children}</div>}
    </li>
  );
}

export function TileMenu({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Menu trigger={<IconButton label={label} icon={Ellipsis} size="sm" tooltip={false} />}>
      {children}
    </Menu>
  );
}

export function TileGridSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div role="status" className="tile-grid">
      <span className="visually-hidden">{messages.loading}</span>
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="tile" aria-hidden="true">
          <div className="tile__head">
            <Skeleton width="2.25rem" height="2.25rem" />
            <Skeleton width="55%" />
          </div>
          <div className="tile__body">
            <Skeleton width="80%" />
            <Skeleton width="40%" />
          </div>
        </div>
      ))}
    </div>
  );
}
