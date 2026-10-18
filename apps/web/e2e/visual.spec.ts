import AxeBuilder from '@axe-core/playwright';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { messages } from '../src/messages';

// Visual regression, accessibility and reflow on every screen (P13, F10 of Etapa 9): three widths ×
// two themes. References are generated on macOS with Playwright's Chromium, where they are compared.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
const FIXTURE = 'sample-shop';
const QUESTION = 'How is the refund amount calculated?';
const FILE = 'src/main/java/shop/RefundService.java';
const WIDTHS = [
  { width: 390, height: 844, screenshot: true },
  { width: 1024, height: 768, screenshot: false },
  { width: 1440, height: 900, screenshot: true },
] as const;
const THEMES = ['light', 'dark'] as const;

const created: { workspaceId?: string; projectId?: string; conversationId?: string } = {};

async function post(request: APIRequestContext, url: string, data?: object) {
  const response = await request.post(url, { data });
  expect(response.ok(), `${url}: ${response.status()}`).toBe(true);
  return (await response.json()) as Record<string, string>;
}

test.beforeAll(async ({ request }) => {
  const workspace = await post(request, '/api/v1/workspaces', { name: 'Loja' });
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
  const answer = await post(request, `/api/v1/projects/${project.id}/questions`, {
    question: QUESTION,
  });
  created.conversationId = answer.conversationId;
});

test.afterAll(async ({ request }) => {
  if (created.projectId) {
    await request.delete(`/api/v1/projects/${created.projectId}`);
  }
  if (created.workspaceId) {
    await request.delete(`/api/v1/workspaces/${created.workspaceId}`);
  }
});

interface Screen {
  name: string;
  url: () => string;
  // Leaves the screen in the state to capture: loaded, and a file open where there is one.
  ready: (page: Page) => Promise<void>;
}

const heading = (page: Page, name?: string | RegExp) =>
  expect(page.getByRole('heading', { level: 1, name })).toBeAttached();

const colored = (page: Page) =>
  expect(page.locator('.code-view .tok-keyword').first()).toBeVisible();

const SCREENS: Screen[] = [
  {
    name: 'workspaces',
    url: () => '/',
    ready: (page) => heading(page, messages.workspaces.title),
  },
  {
    name: 'workspace',
    url: () => `/workspaces/${created.workspaceId}`,
    ready: async (page) => {
      await heading(page, 'Loja');
      await expect(page.getByRole('main').getByRole('link', { name: FIXTURE })).toBeVisible();
    },
  },
  {
    name: 'new-project',
    url: () => `/workspaces/${created.workspaceId}/new`,
    ready: async (page) => {
      await heading(page, messages.pages.newProject);
      await expect(page.getByRole('radio').first()).toBeVisible();
    },
  },
  {
    name: 'pre-analysis',
    url: () => `/workspaces/${created.workspaceId}/new?project=${created.projectId}`,
    ready: async (page) => {
      await expect(page.getByText(messages.preAnalysis.totals.includedFiles)).toBeVisible();
    },
  },
  {
    name: 'overview',
    url: () => `/projects/${created.projectId}`,
    ready: async (page) => {
      await expect(
        page.getByRole('heading', { name: messages.project.startHere.title }),
      ).toBeVisible();
      await expect(
        page.getByRole('region', { name: messages.project.snapshots.title }).getByRole('listitem'),
      ).not.toHaveCount(0);
    },
  },
  {
    name: 'ask',
    url: () => `/projects/${created.projectId}/ask`,
    ready: async (page) => {
      await heading(page, messages.pages.ask);
      await expect(page.getByRole('textbox').first()).toBeVisible();
    },
  },
  {
    name: 'conversation',
    url: () => `/projects/${created.projectId}/conversations/${created.conversationId}`,
    ready: async (page) => {
      await heading(page, QUESTION);
      await page
        .getByRole('button', { name: /^Abrir a evidência E\d+/ })
        .first()
        .click();
      await colored(page);
    },
  },
  {
    name: 'files',
    url: () => `/projects/${created.projectId}/files?file=${encodeURIComponent(FILE)}`,
    ready: async (page) => {
      await expect(page.getByRole('link', { name: 'RefundService.java' })).toBeVisible();
      await colored(page);
    },
  },
  {
    name: 'history',
    url: () => `/projects/${created.projectId}/history`,
    ready: async (page) => {
      await expect(page.getByRole('main').getByRole('link', { name: QUESTION })).toBeVisible();
    },
  },
  {
    name: 'settings',
    url: () => `/projects/${created.projectId}/settings`,
    ready: async (page) => {
      await expect(page.getByLabel(messages.settings.patternsLabel)).toBeVisible();
    },
  },
  {
    name: 'system',
    url: () => '/system',
    ready: async (page) => {
      await expect(page.getByRole('table')).toBeVisible();
    },
  },
  {
    name: 'not-found',
    url: () => '/nao-existe',
    ready: (page) => heading(page, messages.notFound.title),
  },
];

// Dates, durations and other workspaces in the database change between runs.
function masks(page: Page) {
  return [
    page.locator('time'),
    page.getByText(/\d{2}\/\d{2}\/\d{4}|\b\d+(,\d+)? ?(ms|s)\b|US\$|agora mesmo|^há /),
    page.locator('main .tile-grid'),
  ];
}

async function settle(page: Page) {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() =>
    document
      .getAnimations()
      .every(
        (animation) =>
          animation.effect?.getComputedTiming().iterations === Infinity ||
          animation.playState === 'finished',
      ),
  );
}

for (const size of WIDTHS) {
  for (const theme of THEMES) {
    test(`every screen at ${size.width} px, ${theme} theme`, async ({ page }) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      await page.setViewportSize({ width: size.width, height: size.height });
      await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' });

      for (const screen of SCREENS) {
        await test.step(screen.name, async () => {
          await page.goto(screen.url());
          await screen.ready(page);
          await settle(page);

          const reflow = await page.evaluate(
            () => document.documentElement.scrollWidth - window.innerWidth,
          );
          expect(reflow, `${screen.name}: horizontal scroll`).toBeLessThanOrEqual(0);
          const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
          expect(
            results.violations.map(
              (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target)}`,
            ),
            screen.name,
          ).toEqual([]);
          if (size.screenshot) {
            await expect(page).toHaveScreenshot(`${screen.name}-${theme}-${size.width}.png`, {
              animations: 'disabled',
              caret: 'hide',
              mask: masks(page),
              maxDiffPixelRatio: 0.01,
            });
          }
        });
      }
      expect(errors).toEqual([]);
    });
  }
}
