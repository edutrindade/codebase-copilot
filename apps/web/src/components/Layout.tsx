import { Link, Outlet } from 'react-router';
import { messages } from '../messages';

export function Layout() {
  return (
    <>
      <a className="skip-link" href="#content">
        {messages.app.skipToContent}
      </a>
      <header className="app-header">
        <Link to="/" className="app-header__brand">
          {messages.app.name}
        </Link>
      </header>
      <main id="content" className="app-main" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  );
}
