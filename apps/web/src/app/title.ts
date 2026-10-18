import { useLayoutEffect } from 'react';
import { messages } from '../messages';

export function pageTitle(...parts: (string | null | undefined)[]): string {
  return [...parts.filter((part): part is string => Boolean(part)), messages.app.name].join(' · ');
}

// A layout effect, so the title is in place when the route announcer reads it.
export function usePageTitle(parts: (string | null | undefined)[], enabled = true): void {
  const title = pageTitle(...parts);
  useLayoutEffect(() => {
    if (enabled) {
      document.title = title;
    }
  }, [title, enabled]);
}
