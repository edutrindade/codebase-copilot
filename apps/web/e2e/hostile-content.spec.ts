import { expect, test, type APIRequestContext } from '@playwright/test';
import { messages } from '../src/messages';

// Markup in file content, file and directory names and the question is shown as text: nothing
// runs and the CSP is never violated (P4 of Etapa 8).
const FIXTURE = 'hostile-content';
const QUESTION = `<img src=x onerror="window.__xss = 'question'"> xsscheck refund`;
const FILE_NAME = '<img src=x onerror=window.__xss=1>.md';
const DIRECTORY_NAME = '<svg onload=window.__xss=1>';
// The fake provider never writes markdown, so the stored answer is swapped in the response (D-091).
const hostileAnswer = (label: string) =>
  [
    '**Resumo** <b>sem HTML</b>',
    `1. Veja [o guia](javascript:window.__xss=1) [${label}]`,
    '2. ![x](x) <img src=x onerror="window.__xss=\'answer\'"> [E99]',
    '',
    '```html',
    '<script>window.__xss = 1</script>',
    '```',
  ].join('\n');

const created: { workspaceId?: string; projectId?: string } = {};

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

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

  await test.step('answer with hostile markdown', async () => {
    await page.route('**/api/v1/conversations/*', async (route) => {
      const response = await route.fetch();
      const body = (await response.json()) as {
        answers: { answer: string | null; evidence: { citationLabel: string }[] }[];
      };
      for (const item of body.answers) {
        item.answer = hostileAnswer(item.evidence[0]?.citationLabel ?? 'E1');
      }
      await route.fulfill({ response, json: body });
    });
    await page.reload();

    const card = page.getByRole('article', { name: messages.ask.answer }).first();
    await expect(card.locator('strong')).toHaveText('Resumo');
    await expect(card.locator('li')).toHaveCount(2);
    await expect(card).toContainText('<b>sem HTML</b>');
    await expect(card).toContainText('[o guia](javascript:window.__xss=1)');
    await expect(card).toContainText(`<img src=x onerror="window.__xss='answer'"> [E99]`);
    await expect(card.locator('pre code')).toHaveText('<script>window.__xss = 1</script>');
    await expect(card.locator('a, img, b, script, iframe')).toHaveCount(0);
    await expect(card.getByRole('button', { name: /^Abrir a evidência E\d+/ })).toHaveCount(1);
    await page.unrouteAll();
  });

  await test.step('names in the file tree', async () => {
    await page.goto(`/projects/${created.projectId}/files`);
    const tree = page.getByRole('list', { name: messages.files.treeLabel });
    await expect(tree.getByRole('link', { name: FILE_NAME })).toBeVisible();
    await tree
      .getByRole('button', { name: new RegExp(`^${escapeRegExp(DIRECTORY_NAME)}`) })
      .click();
    await tree.getByRole('link', { name: 'notes.md' }).click();
    await expect(page.locator('.code-view')).toBeVisible();
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
