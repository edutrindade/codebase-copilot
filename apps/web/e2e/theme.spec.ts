import { expect, test, type Page } from '@playwright/test';
import { messages } from '../src/messages';

// Theme (P9 of Etapa 9): switching, persistence and no flash of the other theme on load.

// Records the theme and the page background on the first frame with a body: rendering waits for
// the stylesheets in the head, so this is what the first paint shows.
async function recordFirstPaint(page: Page) {
  await page.addInitScript(() => {
    function sample() {
      if (!document.body) {
        requestAnimationFrame(sample);
        return;
      }
      Object.assign(window, {
        firstTheme: document.documentElement.dataset.theme ?? null,
        firstBackground: getComputedStyle(document.body).backgroundColor,
      });
    }
    requestAnimationFrame(sample);
  });
}

function first(page: Page) {
  return page.evaluate(() => {
    const w = window as unknown as { firstTheme: string | null; firstBackground: string };
    return { theme: w.firstTheme, background: w.firstBackground };
  });
}

function background(page: Page) {
  return page.evaluate(() => getComputedStyle(document.body).backgroundColor);
}

test('the theme menu switches the theme, which survives a reload without flashing', async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await recordFirstPaint(page);

  await page.goto('/system');
  await expect(page.getByRole('heading', { level: 1, name: messages.pages.system })).toBeVisible();
  const light = await background(page);

  await page.getByRole('button', { name: /^Tema:/ }).click();
  await page.getByRole('menuitemradio', { name: messages.theme.options.dark }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  const dark = await background(page);
  expect(dark).not.toBe(light);
  await expect(
    page.getByRole('button', { name: messages.theme.label(messages.theme.options.dark) }),
  ).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: messages.pages.system })).toBeVisible();
  expect(await first(page)).toEqual({ theme: 'dark', background: dark });
  await expect(
    page
      .getByRole('group', { name: messages.system.themeLegend })
      .getByRole('radio', { name: messages.theme.options.dark }),
  ).toBeChecked();

  await page
    .getByRole('group', { name: messages.system.themeLegend })
    .getByRole('radio', { name: messages.theme.options.system })
    .check();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect.poll(() => background(page)).toBe(dark);
  await page.emulateMedia({ colorScheme: 'light' });
  await expect.poll(() => background(page)).toBe(light);
});

test('following the system, a dark system loads dark from the first paint', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await recordFirstPaint(page);
  await page.goto('/system');
  await expect(page.getByRole('heading', { level: 1, name: messages.pages.system })).toBeVisible();
  const dark = await background(page);

  expect(await first(page)).toEqual({ theme: null, background: dark });
});
