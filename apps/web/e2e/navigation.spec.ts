import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { messages } from '../src/messages';

// Palette and shortcuts (F3 of Etapa 9); sidebar, trail and reloads (F10).
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

const created: { workspaceId?: string; projectId?: string } = {};
const WORKSPACE = `E2E navegação ${Date.now()}`;

test.beforeAll(async ({ request }) => {
  const workspace = await request.post('/api/v1/workspaces', {
    data: { name: WORKSPACE },
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

test('the sidebar and the trail follow the route, and every route survives a reload', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const nav = page.getByRole('navigation', { name: messages.shell.mainNav });
  const trail = page.getByRole('navigation', { name: messages.breadcrumbs.label });
  const project = `/projects/${created.projectId}`;

  await test.step('sidebar and trail inside a project', async () => {
    await page.goto(`${project}/history`);
    await expect(
      page.getByRole('heading', { level: 1, name: messages.pages.history }),
    ).toBeVisible();
    await expect(nav.getByRole('link', { name: /^sample-shop/ })).toHaveAttribute(
      'aria-current',
      'true',
    );
    await expect(trail.getByRole('link', { name: WORKSPACE })).toHaveAttribute(
      'href',
      `/workspaces/${created.workspaceId}`,
    );
    await expect(trail.getByRole('link', { name: 'sample-shop' })).toHaveAttribute('href', project);
    await expect(trail).toContainText(messages.pages.history);

    await trail.getByRole('link', { name: WORKSPACE }).click();
    await expect(page).toHaveURL(new RegExp(`/workspaces/${created.workspaceId}$`));
    await nav.getByRole('link', { name: /^sample-shop/ }).click();
    await expect(page).toHaveURL(new RegExp(`${project}$`));
    await expect(nav.getByRole('link', { name: /^sample-shop/ })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  await test.step('the collapsed sidebar is remembered', async () => {
    await nav.getByRole('button', { name: messages.shell.collapse }).click();
    await page.reload();
    await expect(nav.getByRole('button', { name: messages.shell.expand })).toBeVisible();
    await nav.getByRole('button', { name: messages.shell.expand }).click();
    await expect(nav.getByRole('button', { name: messages.shell.collapse })).toBeVisible();
  });

  await test.step('each route reloads in place with its heading and title', async () => {
    const routes: [string, RegExp][] = [
      ['/', /^Workspaces · Codebase Copilot$/],
      [`/workspaces/${created.workspaceId}`, new RegExp(`^${WORKSPACE}`)],
      [`/workspaces/${created.workspaceId}/new`, new RegExp(`^${messages.pages.newProject}`)],
      [project, new RegExp(`^${messages.pages.overview} · sample-shop`)],
      [`${project}/ask`, new RegExp(`^${messages.pages.ask} · sample-shop`)],
      [`${project}/files`, new RegExp(`^${messages.pages.files} · sample-shop`)],
      [`${project}/history`, new RegExp(`^${messages.pages.history} · sample-shop`)],
      [`${project}/settings`, new RegExp(`^${messages.pages.settings} · sample-shop`)],
      ['/system', new RegExp(`^${messages.pages.system}`)],
      ['/nao-existe', new RegExp(`^${messages.notFound.title}`)],
    ];
    for (const [url, title] of routes) {
      await page.goto(url);
      await page.reload();
      await expect(page.getByRole('heading', { level: 1 }), url).toBeAttached();
      await expect(page, url).toHaveTitle(title);
    }
  });

  expect(errors).toEqual([]);
});
