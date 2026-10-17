import axe from 'axe-core';

// jsdom has no layout, so contrast is left to the Playwright run (P10).
export async function axeViolations(element: Element = document.body): Promise<string[]> {
  const results = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
  return results.violations.map((violation) => violation.id);
}
