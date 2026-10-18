import { useRef } from 'react';

// Radix returns focus to its Trigger; a dialog opened by a shortcut or from code has none,
// so focus would fall to <body>. This returns it to wherever it was when the dialog opened,
// unless something else (the new screen's heading, after a navigation) has taken it since.
export function useReturnFocus(skip?: () => boolean) {
  const previous = useRef<HTMLElement | null>(null);
  return {
    onOpenAutoFocus: () => {
      const active = document.activeElement;
      previous.current = active instanceof HTMLElement && active !== document.body ? active : null;
    },
    onCloseAutoFocus: (event: Event) => {
      event.preventDefault();
      const lost = document.activeElement === null || document.activeElement === document.body;
      if (lost && !skip?.() && previous.current?.isConnected) {
        previous.current.focus();
      }
    },
  };
}
