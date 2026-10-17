import { expect, test, type APIRequestContext } from '@playwright/test';

// Markup in file content, file and directory names and the question is shown as text: nothing
// runs and the CSP is never violated (P4 of Etapa 8).
const FIXTURE = 'hostile-content';
const QUESTION = `<img src=x onerror="window.__xss = 'question'"> xsscheck refund`;
const FILE_NAME = '<img src=x onerror=window.__xss=1>.md';
const DIRECTORY_NAME = '<svg onload=window.__xss=1>';

const created: { workspaceId?: string; projectId?: string } = {};

async function post(request: APIRequestContext, url: string, data?: object) {
  const response = await request.post(url, { data });
  expect(response.ok(), `${url}: ${response.status()}`).toBe(true);
  return (await response.json()) as Record<string, string>;
}

test.afterAll(async ({ request }) => {
  if (created.projectId) {
    await request.delete(`/api/v1/projects/${created.projectId}`);
  }
  if (created.workspaceId) {
    await request.delete(`/api/v1/workspaces/${created.workspaceId}`);
  }
});

test('hostile content, names and question are rendered as text', async ({ page, request }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => consoleErrors.push(error.message));

  await test.step('indexed project through the API', async () => {
    const workspace = await post(request, '/api/v1/workspaces', { name: `E2E ${Date.now()}` });
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

  await test.step('question and evidence', async () => {
    await page.goto(`/projects/${created.projectId}/ask`);
    await page.getByRole('textbox').first().fill(QUESTION);
    await page.keyboard.press('ControlOrMeta+Enter');
    await expect(page).toHaveURL(/\/conversations\//, { timeout: 60_000 });

    await expect(page.getByText(QUESTION, { exact: true }).first()).toBeVisible();
    await expect(page.getByText(FILE_NAME).first()).toBeVisible();
    await expect(page.getByText(`${DIRECTORY_NAME}/notes.md`).first()).toBeVisible();

    await page.getByRole('button', { name: /^Abrir a evidência E\d+: web\/payload\.html/ }).click();
    await expect(page.locator('.code-view')).toContainText(
      "<script>window.__xss = 'content-script';</script>",
    );
  });

  await test.step('nothing ran', async () => {
    expect(await page.evaluate(() => (window as unknown as { __xss?: string }).__xss)).toBe(
      undefined,
    );
    await expect(page.locator('main img, main svg[onload], main iframe, main script')).toHaveCount(
      0,
    );
    await expect(page.locator('a[href^="javascript:"]')).toHaveCount(0);
  });

  expect(consoleErrors).toEqual([]);
});
