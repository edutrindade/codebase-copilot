import { useEffect, type FC } from 'react';

// Stands in for react-style-singleton (aliased in vite.config.ts). The original appends a <style>
// element, which style-src 'self' blocks; a constructed stylesheet is CSSOM, which the CSP allows.
// Radix uses it, through react-remove-scroll, to lock the page scroll behind dialogs and menus.

function canConstruct(): boolean {
  return typeof CSSStyleSheet === 'function' && 'adoptedStyleSheets' in Document.prototype;
}

export function stylesheetSingleton() {
  let counter = 0;
  let sheet: CSSStyleSheet | null = null;
  return {
    add(style: string) {
      if (counter === 0 && canConstruct()) {
        try {
          sheet = new CSSStyleSheet();
          sheet.replaceSync(style);
          document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet];
        } catch {
          sheet = null;
        }
      }
      counter++;
    },
    remove() {
      counter--;
      if (counter === 0 && sheet) {
        const added = sheet;
        document.adoptedStyleSheets = document.adoptedStyleSheets.filter((item) => item !== added);
        sheet = null;
      }
    },
  };
}

export function styleHookSingleton() {
  const sheet = stylesheetSingleton();
  return function useStyle(styles: string, isDynamic?: boolean) {
    // Same dependency as the original: styles are applied once unless marked dynamic.
    const dependency = Boolean(styles) && isDynamic;
    useEffect(() => {
      sheet.add(styles);
      return () => sheet.remove();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [dependency]);
  };
}

export function styleSingleton(): FC<{ styles: string; dynamic?: boolean }> {
  const useStyle = styleHookSingleton();
  return function Sheet({ styles, dynamic }) {
    useStyle(styles, dynamic);
    return null;
  };
}
