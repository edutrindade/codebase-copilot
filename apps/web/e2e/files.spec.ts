import AxeBuilder from '@axe-core/playwright';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { messages } from '../src/messages';

// The Files tab (P5.7 of Etapa 9): tree from GET /snapshots/{id}/tree, viewer and history.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
const FIXTURE = 'sample-shop';
const FILE = 'src/main/java/shop/RefundService.java';

const created: { workspaceId?: string; projectId?: string } = {};

async function post(request: APIRequestContext, url: string, data?: object) {
  const response = await request.post(url, { data });
  expect(response.ok(), `${url}: ${response.status()}`).toBe(true);
  return (await response.json()) as Record<string, string>;
}

async function expectAccessible(page: Page, screen: string) {
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

test.beforeAll(async ({ request }) => {
  const workspace = await post(request, '/api/v1/workspaces', {
    name: `E2E arquivos ${Date.now()}`,
  });
  created.workspaceId = workspace.id;
  const project = await post(request, `/api/v1/workspaces/${workspace.id}/projects`, {
    name: FIXTURE,
    sourceType: 'LOCAL_FOLDER',
    rootKey: 'default',
    relativePath: FIXTURE,
  });
  created.projectId = project.id;
  const job = await post(request, `/api/v1/projects/${project.id}/index-jobs`);
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/v1/index-jobs/${job.id}`);
        return ((await response.json()) as { status: string }).status;
      },
      { timeout: 120_000 },
    )
    .toBe('COMPLETED');
});

test.afterAll(async ({ request }) => {
  if (created.projectId) {
    await request.delete(`/api/v1/projects/${created.projectId}`);
  }
  if (created.workspaceId) {
    await request.delete(`/api/v1/workspaces/${created.workspaceId}`);
  }
});

test('explores the snapshot by keyboard, opens a file and asks about it', async ({ page }) => {
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
  const tree = page.getByRole('list', { name: messages.files.treeLabel });

  await test.step('g then a opens the tab', async () => {
    await page.goto(`/projects/${created.projectId}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeAttached();
    await page.locator('body').click({ position: { x: 1, y: 1 } });
    await page.keyboard.press('g');
    await page.keyboard.press('a');
    await expect(page).toHaveURL(new RegExp(`/projects/${created.projectId}/files$`));
    await expect(page.getByRole('heading', { level: 1, name: messages.pages.files })).toBeVisible();
    await expect(tree.getByRole('link', { name: 'README.md' })).toBeVisible();
    await expectAccessible(page, 'files');
  });

  await test.step('the arrows open the folders down to the file', async () => {
    await tree.getByRole('button', { name: /^src/ }).focus();
    // → opens the folder; once its rows load, → again moves into it.
    for (const child of ['main', 'java', 'shop']) {
      await page.keyboard.press('ArrowRight');
      const row = tree.getByRole('button', { name: new RegExp(`^${child}`) });
      await expect(row).toBeVisible();
      await page.keyboard.press('ArrowRight');
      await expect(row).toBeFocused();
    }
    await page.keyboard.press('ArrowRight');
    await expect(tree.getByRole('link', { name: 'Application.java' })).toBeVisible();
    await page.keyboard.press('ArrowRight');
    await expect(tree.getByRole('link', { name: 'Application.java' })).toBeFocused();
    await tree.getByRole('link', { name: 'RefundService.java' }).click();
    await expect(page.locator('.code-view')).toContainText('class RefundService');
    expect(new URL(page.url()).searchParams.get('file')).toBe(FILE);
    // The highlight worker runs under the build's CSP (F9).
    await expect(page.locator('.code-view .tok-keyword').first()).toHaveText(
      /^(package|import|public|final|class)$/,
    );
    await expectAccessible(page, 'file open');
    await page.emulateMedia({ colorScheme: 'dark' });
    await expectAccessible(page, 'file open, dark theme');
    await page.emulateMedia({ colorScheme: 'light' });
  });

  await test.step('the filter and the browser history', async () => {
    await page.getByRole('radio', { name: /^Indexados/ }).click();
    await expect(page).toHaveURL(/status=INDEXED/);
    await tree.getByRole('link', { name: 'README.md' }).click();
    await expect(page.locator('.code-view')).toContainText('Sample Shop');
    await page.goBack();
    expect(new URL(page.url()).searchParams.get('file')).toBe(FILE);
    await expect(page.locator('.code-view')).toContainText('class RefundService');
  });

  await test.step('asking about the file fills the question without sending it', async () => {
    await page.getByRole('link', { name: messages.files.askAbout }).click();
    await expect(page).toHaveURL(new RegExp(`/projects/${created.projectId}/ask$`));
    await expect(page.getByRole('textbox').first()).toHaveValue(messages.files.askDraft(FILE));
  });

  expect(errors).toEqual([]);
  expect(
    await page.evaluate(() => (window as unknown as { cspViolations: string[] }).cspViolations),
  ).toEqual([]);
});
