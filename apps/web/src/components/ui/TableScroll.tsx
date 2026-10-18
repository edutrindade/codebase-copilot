import type { ReactNode } from 'react';

// Focusable, so a table wider than the screen can be scrolled by keyboard (WCAG 2.1.1).
export function TableScroll({ labelledBy, children }: { labelledBy: string; children: ReactNode }) {
  return (
    <div className="table-scroll" role="group" aria-labelledby={labelledBy} tabIndex={0}>
      {children}
    </div>
  );
}
