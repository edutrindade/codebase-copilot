import { expect, test } from '@playwright/test';
import { messages } from '../src/messages';

// Only in playwright.jar.config.ts: what the backend adds when it serves the UI itself.
test('the packaged UI reloads on a client route and is served with the security headers', async ({
  page,
}) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  const response = await page.goto('/projects/0192f5c4-0000-7000-8000-000000000999');

  expect(response?.status()).toBe(200);
  const headers = response?.headers() ?? {};
  expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(headers['content-security-policy']).toContain("script-src 'self'");
  expect(headers['x-content-type-options']).toBe('nosniff');
  expect(headers['referrer-policy']).toBe('no-referrer');
  await expect(page.getByRole('link', { name: messages.app.skipToContent })).toBeAttached();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

  const api = await page.request.get('/api/v1/unknown');
  expect(api.status()).toBe(404);
  expect(api.headers()['content-type']).toContain('application/problem+json');
  // 404 of the unknown project above, from the API, is expected; nothing else.
  expect(consoleErrors.filter((error) => !error.includes('404'))).toEqual([]);
});

test('state changes from the packaged UI pass the origin check', async ({ page }) => {
  await page.goto('/');
  const status = await page.evaluate(async () => {
    const created = await fetch('/api/v1/workspaces', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'e2e-packaged-origin' }),
    });
    const body = (await created.json()) as { id: string };
    await fetch(`/api/v1/workspaces/${body.id}`, { method: 'DELETE' });
    return created.status;
  });
  expect(status).toBe(201);
});
