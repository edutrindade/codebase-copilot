import { Link, Outlet } from 'react-router';
import { messages } from '../messages';
import { ThemeMenu } from './ThemeMenu';
import { BrandMark } from './ui/BrandMark';

export function Layout() {
  return (
    <>
      <a className="skip-link" href="#content">
        {messages.app.skipToContent}
      </a>
      <header className="app-header">
        <Link to="/" className="app-header__brand">
          <BrandMark />
          {messages.app.name}
        </Link>
        <ThemeMenu />
      </header>
      <main id="content" className="app-main" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  );
}
