import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, test } from 'vitest';
import { SINGLE_KEY_SHORTCUTS_KEY } from '../../app/shortcuts';
import { setThemePreference } from '../../app/theme';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { providers } from '../../test/fixtures';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';

afterEach(() => setThemePreference('system'));

describe('SystemPage', () => {
  test('local providers: nothing leaves the machine', async () => {
    renderRoute('/system');

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.system }),
    ).toBeInTheDocument();
    const table = await screen.findByRole('table');
    const embeddings = within(table).getByRole('row', {
      name: new RegExp(messages.system.embeddings),
    });
    expect(embeddings).toHaveTextContent(messages.system.dimensions(8));
    expect(embeddings).toHaveTextContent(messages.system.local);
    expect(screen.getByText(messages.privacy.localOnly)).toBeInTheDocument();
    expect(screen.getByText(messages.system.questionsLocal)).toBeInTheDocument();
    expect(document.title).toBe(`${messages.pages.system} · Codebase Copilot`);
    expect(await axeViolations()).toEqual([]);
  });

  test('remote providers say what is sent and to whom', async () => {
    server.use(http.get('/api/v1/system/providers', () => HttpResponse.json(providers(true))));
    renderRoute('/system');

    expect(
      await screen.findByText(messages.ask.privacy.remote('openai', 'gpt-6-luna')),
    ).toBeInTheDocument();
    expect(
      screen.getByText(messages.privacy.remote('openai', 'text-embedding-3-small')),
    ).toBeInTheDocument();
    expect(screen.getByText(messages.ask.privacy.remoteEmbeddings('openai'))).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByText(messages.system.remote)).toHaveLength(2);
  });

  test('a provider that is not configured says so', async () => {
    server.use(
      http.get('/api/v1/system/providers', () =>
        HttpResponse.json({
          ...providers(),
          generation: { provider: 'none', model: null, remote: false },
        }),
      ),
    );
    renderRoute('/system');

    expect(await screen.findByText(messages.system.notConfigured)).toBeInTheDocument();
    expect(screen.getByText(messages.system.noGeneration)).toBeInTheDocument();
  });

  test('the theme can be chosen here too', async () => {
    renderRoute('/system');

    const group = await screen.findByRole('group', { name: messages.system.themeLegend });
    expect(within(group).getByRole('radio', { name: messages.theme.options.system })).toBeChecked();
    await userEvent.click(within(group).getByRole('radio', { name: messages.theme.options.dark }));

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(within(group).getByRole('radio', { name: messages.theme.options.dark })).toBeChecked();
  });

  test('lists the keyboard shortcuts and turns the single-key ones off', async () => {
    const user = userEvent.setup();
    renderRoute('/system');

    const section = await screen.findByRole('region', { name: messages.shortcuts.title });
    expect(within(section).getByText(messages.shortcuts.actions.openPalette)).toBeInTheDocument();
    const toggle = within(section).getByRole('checkbox', { name: messages.shortcuts.singleKey });
    expect(toggle).toBeChecked();

    await user.click(toggle);

    expect(toggle).not.toBeChecked();
    expect(window.localStorage.getItem(SINGLE_KEY_SHORTCUTS_KEY)).toBe('off');
    expect(
      within(section).getAllByText(`(${messages.shortcuts.singleKeyOff})`).length,
    ).toBeGreaterThan(0);
    expect(await axeViolations()).toEqual([]);
  });
});
