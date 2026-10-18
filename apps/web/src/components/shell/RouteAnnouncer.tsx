import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigationType } from 'react-router';

const FOLLOW_MS = 10_000;

// After a navigation, focus goes to the new screen's <h1> and its title is announced. A screen
// swaps its loading <h1> for the loaded one, so focus follows the <h1> until the person acts.
export function RouteAnnouncer() {
  const { pathname } = useLocation();
  const navigationType = useNavigationType();
  const previous = useRef(pathname);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (previous.current === pathname) {
      return;
    }
    previous.current = pathname;
    // A replace keeps the person where they were (a new conversation getting its URL, for one).
    const main = document.getElementById('content');
    if (navigationType === 'REPLACE' || !main) {
      return;
    }
    let focused: HTMLElement | null = null;
    let announced = false;

    function follow() {
      const heading = main?.querySelector('h1');
      if (!heading || heading === focused) {
        return;
      }
      const active = document.activeElement;
      if (focused && active !== document.body && active !== focused) {
        stop();
        return;
      }
      heading.tabIndex = -1;
      heading.focus();
      focused = heading;
      if (!announced) {
        announced = true;
        setMessage(document.title);
      }
    }

    const observer = new MutationObserver(follow);
    const timer = window.setTimeout(stop, FOLLOW_MS);
    function stop() {
      observer.disconnect();
      window.clearTimeout(timer);
      window.removeEventListener('pointerdown', stop);
      window.removeEventListener('keydown', stop);
    }
    observer.observe(main, { childList: true, subtree: true });
    window.addEventListener('pointerdown', stop);
    window.addEventListener('keydown', stop);
    follow();
    return stop;
  }, [pathname, navigationType]);

  return (
    <div className="visually-hidden" aria-live="polite" aria-atomic="true">
      {message}
    </div>
  );
}
