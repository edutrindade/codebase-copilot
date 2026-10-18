import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { messages } from '../src/messages';

// Palette and shortcuts (F3 of Etapa 9); F10 adds the sidebar, the trail and reloads.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

const created: { workspaceId?: string; projectId?: string } = {};

test.beforeAll(async ({ request }) => {
  const workspace = await request.post('/api/v1/workspaces', {
    data: { name: `E2E navegação ${Date.now()}` },
  });
  created.workspaceId = (await workspace.json()).id;
  const roots = await (await request.get('/api/v1/local-roots')).json();
  const project = await request.post(`/api/v1/workspaces/${created.workspaceId}/projects`, {
    data: {
      sourceType: 'LOCAL_FOLDER',
      name: 'sample-shop',
      rootKey: roots[0].key,
      relativePath: 'sample-shop',
    },
  });
  expect(project.ok()).toBe(true);
  created.projectId = (await project.json()).id;
});

test.afterAll(async ({ request }) => {
  if (created.projectId) {
    await request.delete(`/api/v1/projects/${created.projectId}`);
  }
  if (created.workspaceId) {
    await request.delete(`/api/v1/workspaces/${created.workspaceId}`);
  }
});

async function expectAccessible(page: Page, screen: string) {
  // Contrast measured mid fade-in is lower than what stays on screen; skeleton shimmers never end.
  await page.waitForFunction(() =>
    document
      .getAnimations()
      .every(
        (animation) =>
          animation.effect?.getComputedTiming().iterations === Infinity ||
          animation.playState === 'finished',
      ),
  );
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  const violations = results.violations.map(
    (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(', ')}`,
  );
  expect(violations, screen).toEqual([]);
}

function scrollLocked(page: Page) {
  return page.evaluate(() => getComputedStyle(document.body).overflow === 'hidden');
}

test('command palette and keyboard shortcuts, with the page scroll locked under the CSP', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text());
    }
  });
  page.on('pageerror', (error) => errors.push(error.message));
  await page.addInitScript(() => {
    const violations: string[] = [];
    Object.assign(window, { cspViolations: violations });
    document.addEventListener('securitypolicyviolation', (event) => {
      violations.push(`${event.violatedDirective} ${event.blockedURI}`);
    });
  });

  await test.step('the palette opens with the shortcut and goes to a project', async () => {
    await page.goto(`/workspaces/${created.workspaceId}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    await page.keyboard.press('ControlOrMeta+K');
    const palette = page.getByRole('dialog', { name: messages.palette.title });
    await expect(palette).toBeVisible();
    await expect(palette.getByRole('combobox', { name: messages.palette.label })).toBeFocused();
    await expect(palette.getByRole('option', { name: /^sample-shop/ })).toBeVisible();
    expect(await scrollLocked(page)).toBe(true);
    await expectAccessible(page, 'palette');
    await page.emulateMedia({ colorScheme: 'dark' });
    await expectAccessible(page, 'palette, dark theme');
    await page.emulateMedia({ colorScheme: 'light' });

    await page.keyboard.type('sample');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/projects/${created.projectId}$`));
    await expect(palette).toBeHidden();
    expect(await scrollLocked(page)).toBe(false);
  });

  await test.step('g then h goes to the history; ? lists the shortcuts', async () => {
    // The URL changes before the view transition renders the project area and its shortcuts.
    await expect(
      page.getByRole('navigation', { name: messages.projectArea.tabsLabel }),
    ).toBeVisible();
    await page.keyboard.press('g');
    await page.keyboard.press('h');
    await expect(page).toHaveURL(new RegExp(`/projects/${created.projectId}/history$`));
    await expect(
      page.getByRole('heading', { level: 1, name: messages.pages.history }),
    ).toBeVisible();

    await page.keyboard.press('?');
    const dialog = page.getByRole('dialog', { name: messages.shortcuts.title });
    await expect(dialog).toBeVisible();
    await expectAccessible(page, 'shortcuts');
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
  });

  await test.step('the theme menu locks the scroll too, without a blocked style', async () => {
    await page.getByRole('button', { name: /^Tema:/ }).click();
    await expect(page.getByRole('menu')).toBeVisible();
    expect(await scrollLocked(page)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menu')).toBeHidden();
  });

  expect(
    await page.evaluate(() => (window as unknown as { cspViolations: string[] }).cspViolations),
  ).toEqual([]);
  expect(errors).toEqual([]);
});
