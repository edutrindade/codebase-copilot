import { afterEach, beforeEach, describe, expect, test } from 'vitest';
import { stylesheetSingleton } from './styleSingleton';

// jsdom constructs stylesheets but has no adoptedStyleSheets; browsers have both.
let adopted: CSSStyleSheet[] = [];

beforeEach(() => {
  adopted = [];
  Object.defineProperty(Document.prototype, 'adoptedStyleSheets', {
    configurable: true,
    get: () => adopted,
    set: (value: CSSStyleSheet[]) => {
      adopted = value;
    },
  });
});

afterEach(() => {
  delete (Document.prototype as { adoptedStyleSheets?: unknown }).adoptedStyleSheets;
});

describe('stylesheetSingleton', () => {
  test('adopts a constructed stylesheet instead of adding a <style> element', () => {
    const sheet = stylesheetSingleton();
    sheet.add('body { overflow: hidden !important; }');

    expect(document.querySelectorAll('style')).toHaveLength(0);
    expect(adopted).toHaveLength(1);
    expect(adopted[0]?.cssRules[0]?.cssText).toContain('overflow: hidden');
  });

  test('keeps the stylesheet until the last user removes it', () => {
    const sheet = stylesheetSingleton();
    sheet.add('body { overflow: hidden; }');
    sheet.add('body { overflow: hidden; }');

    sheet.remove();
    expect(adopted).toHaveLength(1);
    sheet.remove();
    expect(adopted).toHaveLength(0);
  });

  test('leaves stylesheets added by others in place', () => {
    const other = new CSSStyleSheet();
    adopted = [other];
    const sheet = stylesheetSingleton();

    sheet.add('body { overflow: hidden; }');
    sheet.remove();

    expect(adopted).toEqual([other]);
  });

  test('does nothing where stylesheets cannot be adopted', () => {
    delete (Document.prototype as { adoptedStyleSheets?: unknown }).adoptedStyleSheets;
    const sheet = stylesheetSingleton();

    expect(() => {
      sheet.add('body { overflow: hidden; }');
      sheet.remove();
    }).not.toThrow();
    expect(document.querySelectorAll('style')).toHaveLength(0);
  });
});
