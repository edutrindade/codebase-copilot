import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { messages } from '../src/messages';

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const FIXTURE = 'sample-shop';
const QUESTION = 'How is the refund amount calculated?';

async function expectAccessible(page: Page, screen: string) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  const violations = results.violations.map(
    (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(', ')}`,
  );
  expect(violations, screen).toEqual([]);
}

function idAfter(url: string, segment: string): string {
  const match = new RegExp(`/${segment}/([^/?#]+)`).exec(url);
  if (!match?.[1]) {
    throw new Error(`No ${segment} id in ${url}`);
  }
  return match[1];
}

const created: { workspaceId?: string; projectId?: string } = {};

test.afterAll(async ({ request }) => {
  if (created.projectId) {
    await request.delete(`/api/v1/projects/${created.projectId}`);
  }
  if (created.workspaceId) {
    await request.delete(`/api/v1/workspaces/${created.workspaceId}`);
  }
});

test('from a new workspace to an answer with evidence and its conversation in the history', async ({
  page,
}) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => consoleErrors.push(error.message));

  await test.step('workspace', async () => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expectAccessible(page, 'workspaces');

    await page.getByLabel(messages.workspaces.nameLabel).fill(`E2E ${Date.now()}`);
    await page.getByRole('button', { name: messages.workspaces.create }).click();
    await expect(page).toHaveURL(/\/workspaces\/[^/]+$/);
    created.workspaceId = idAfter(page.url(), 'workspaces');
    await expect(page.getByRole('heading', { name: messages.projects.emptyTitle })).toBeVisible();
    await expectAccessible(page, 'workspace');
  });

  await test.step('project from a local folder and pre-analysis', async () => {
    await page.getByRole('link', { name: messages.projects.newProject }).first().click();
    await expect(
      page.getByRole('heading', { level: 1, name: messages.pages.newProject }),
    ).toBeVisible();
    await page.getByRole('button', { name: messages.newProject.browser.open(FIXTURE) }).click();
    await expect(page.getByLabel(messages.newProject.nameLabel)).toHaveValue(FIXTURE);
    await expectAccessible(page, 'new project: source');
    await page.getByRole('button', { name: messages.newProject.create }).click();

    await expect(page).toHaveURL(/[?&]project=/);
    created.projectId = new URL(page.url()).searchParams.get('project') ?? undefined;
    await expect(page.getByRole('heading', { name: messages.preAnalysis.title })).toBeVisible();
    await expect(page.getByText(messages.preAnalysis.totals.includedFiles)).toBeVisible();
    await expectAccessible(page, 'new project: pre-analysis');
  });

  await test.step('indexing', async () => {
    await page.getByRole('button', { name: messages.indexing.start, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/projects/${created.projectId}$`));
    await expect(page.getByRole('heading', { name: messages.project.snapshot.title })).toBeVisible({
      timeout: 120_000,
    });
    await expect(
      page
        .getByRole('region', { name: messages.project.snapshot.title })
        .getByText(messages.project.snapshot.status.READY, { exact: true }),
    ).toBeVisible();
    await expectAccessible(page, 'project');
  });

  await test.step('question by keyboard, answer and evidence', async () => {
    const tabs = page.getByRole('navigation', { name: messages.projectArea.tabsLabel });
    await tabs.getByRole('link', { name: messages.projectArea.tabs.ask }).click();
    await expect(page.getByRole('heading', { level: 1, name: messages.pages.ask })).toBeVisible();
    await expectAccessible(page, 'ask');

    const question = page.getByRole('textbox', { name: messages.ask.form.label });
    await question.focus();
    await page.keyboard.type(QUESTION);
    await page.keyboard.press('ControlOrMeta+Enter');

    await expect(page).toHaveURL(/\/conversations\//, { timeout: 60_000 });
    const citation = page.getByRole('button', { name: /^Abrir a evidência E\d+/ }).first();
    await expect(citation).toBeVisible();
    await expect(page.getByText(messages.ask.status.ANSWERED)).toBeVisible();

    await citation.focus();
    await page.keyboard.press('Enter');
    const code = page.locator('.code-view').first();
    await expect(code).toBeVisible();
    await expect(code.locator('.code-view__line--cited').first()).toBeVisible();
    await expect(page.locator('.viewer__path code')).toHaveText(/\.(java|md|properties)$/);
    await expectAccessible(page, 'conversation with the viewer open');
  });

  await test.step('history', async () => {
    await page
      .getByRole('navigation', { name: messages.projectArea.tabsLabel })
      .getByRole('link', { name: messages.projectArea.tabs.history })
      .click();
    await expect(
      page.getByRole('heading', { level: 1, name: messages.pages.history }),
    ).toBeVisible();
    const conversation = page.getByRole('main').getByRole('link', { name: QUESTION });
    await expect(conversation).toBeVisible();
    await expectAccessible(page, 'history');

    await conversation.click();
    await expect(page.getByRole('heading', { level: 1, name: QUESTION })).toBeVisible();
    await expect(
      page.getByRole('button', { name: /^Abrir a evidência E\d+/ }).first(),
    ).toBeVisible();
  });

  await test.step('skip link', async () => {
    await page.reload();
    await expect(page.getByRole('heading', { level: 1, name: QUESTION })).toBeVisible();
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: messages.app.skipToContent });
    await expect(skip).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('main')).toBeFocused();
  });

  expect(consoleErrors).toEqual([]);
});
