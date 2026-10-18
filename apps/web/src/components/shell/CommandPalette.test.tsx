import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, test } from 'vitest';
import { SINGLE_KEY_SHORTCUTS_KEY } from '../../app/shortcuts';
import { setThemePreference } from '../../app/theme';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import {
  indexJob,
  page,
  project,
  readySnapshot,
  settings,
  snapshotDetail,
  usage,
  workspace,
} from '../../test/fixtures';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';

const text = messages.palette;
const ws = workspace({ name: 'Clientes', projectCount: 2 });
const ready = project({ name: 'api', activeSnapshot: readySnapshot() });
const other = project({ id: '0192f5c4-0000-7000-8000-000000000102', name: 'web' });
const projectUrl = `/api/v1/projects/${ready.id}`;
const summary = {
  id: '0192f5c4-0000-7000-8000-000000000501',
  projectId: ready.id,
  title: 'Como o reembolso é calculado?',
  questionCount: 1,
  createdAt: '2026-10-16T12:00:00Z',
  updatedAt: '2026-10-16T12:00:02Z',
};

function handlers() {
  return [
    http.get('/api/v1/workspaces', () => HttpResponse.json(page([ws]))),
    http.get(`/api/v1/workspaces/${ws.id}`, () => HttpResponse.json(ws)),
    http.get(`/api/v1/workspaces/${ws.id}/projects`, () => HttpResponse.json(page([ready, other]))),
    http.get(projectUrl, () => HttpResponse.json(ready)),
    http.get(`${projectUrl}/conversations`, () => HttpResponse.json(page([summary]))),
    http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
    http.get(`${projectUrl}/index-jobs`, () => HttpResponse.json(page([]))),
    http.get(`${projectUrl}/snapshots`, () => HttpResponse.json(page([snapshotDetail()]))),
    http.get(`${projectUrl}/usage`, () => HttpResponse.json(usage())),
  ];
}

function palette() {
  return screen.findByRole('dialog', { name: text.title });
}

afterEach(() => setThemePreference('system'));

describe('CommandPalette', () => {
  test('Ctrl+K opens it with the project sections, conversations, projects and workspaces', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    renderRoute(`/projects/${ready.id}/history`);
    await screen.findByRole('heading', { level: 1, name: messages.pages.history });

    await user.keyboard('{Control>}k{/Control}');

    const dialog = await palette();
    expect(within(dialog).getByRole('combobox', { name: text.label })).toHaveFocus();
    expect(await within(dialog).findByRole('option', { name: summary.title })).toBeInTheDocument();
    const names = within(dialog)
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(names).toEqual(
      expect.arrayContaining([
        messages.projectArea.tabs.overview,
        messages.projectArea.tabs.ask,
        messages.projectArea.tabs.settings,
        text.reindex,
        summary.title,
        `api${messages.projects.status.READY}`,
        'Clientes' + text.current,
        text.newProject,
        text.shortcuts,
      ]),
    );
    expect(await axeViolations()).toEqual([]);
  });

  test('typing filters the list and Enter goes to the chosen screen', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    renderRoute(`/projects/${ready.id}/history`);
    await screen.findByRole('heading', { level: 1, name: messages.pages.history });

    await user.click(screen.getByRole('button', { name: messages.shell.paletteLabel }));
    const dialog = await palette();
    await within(dialog).findByRole('option', { name: summary.title });
    await user.keyboard('configura');

    expect(
      within(dialog)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual([messages.projectArea.tabs.settings]);
    await user.keyboard('{Enter}');

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.settings }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: text.title })).not.toBeInTheDocument();
  });

  test('says when nothing matches and closes with Escape, returning focus', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    renderRoute(`/workspaces/${ws.id}`);
    const trigger = await screen.findByRole('button', { name: messages.shell.paletteLabel });

    await user.click(trigger);
    const dialog = await palette();
    await user.keyboard('zzzzzz');
    expect(within(dialog).getByText(text.empty)).toBeInTheDocument();

    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: text.title })).not.toBeInTheDocument(),
    );
    // Radix returns focus on the next tick.
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  test('reindexing asks for confirmation, says where the chunks go, then starts the job', async () => {
    const user = userEvent.setup();
    let started = 0;
    server.use(
      ...handlers(),
      http.post(`${projectUrl}/index-jobs`, () => {
        started++;
        return HttpResponse.json(indexJob(ready.id), { status: 202 });
      }),
    );
    const { router } = renderRoute(`/projects/${ready.id}/history`);
    await screen.findByRole('heading', { level: 1, name: messages.pages.history });

    await user.keyboard('{Control>}k{/Control}');
    await user.click(within(await palette()).getByRole('option', { name: text.reindex }));

    const confirm = await screen.findByRole('alertdialog', { name: text.reindexDialog.title });
    expect(within(confirm).getByText(text.reindexDialog.detail('api'))).toBeInTheDocument();
    expect(within(confirm).getByText(messages.privacy.localOnly)).toBeInTheDocument();
    expect(started).toBe(0);

    await user.click(within(confirm).getByRole('button', { name: text.reindexDialog.confirm }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/projects/${ready.id}`));
    expect(started).toBe(1);
  });

  test('switches the theme', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    renderRoute(`/workspaces/${ws.id}`);
    await screen.findByRole('button', { name: messages.shell.paletteLabel });

    await user.keyboard('{Meta>}k{/Meta}');
    const dialog = await palette();
    await user.click(within(dialog).getByRole('option', { name: text.themes.dark }));

    expect(document.documentElement.dataset.theme).toBe('dark');
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: text.title })).not.toBeInTheDocument(),
    );
  });
});

describe('keyboard shortcuts in the shell', () => {
  test('? lists the shortcuts, unless single keys are turned off', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    renderRoute(`/workspaces/${ws.id}`);
    await screen.findByRole('button', { name: messages.shell.paletteLabel });

    await user.keyboard('?');
    const dialog = await screen.findByRole('dialog', { name: messages.shortcuts.title });
    expect(within(dialog).getByText(messages.shortcuts.actions.openPalette)).toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);
    await user.keyboard('{Escape}');
    await waitFor(() => expect(dialog).not.toBeInTheDocument());

    window.localStorage.setItem(SINGLE_KEY_SHORTCUTS_KEY, 'off');
    window.dispatchEvent(new StorageEvent('storage', { key: SINGLE_KEY_SHORTCUTS_KEY }));
    await user.keyboard('?');
    expect(
      screen.queryByRole('dialog', { name: messages.shortcuts.title }),
    ).not.toBeInTheDocument();
  });

  test('g then a letter moves between the sections of the project', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    const { router } = renderRoute(`/projects/${ready.id}/history`);
    await screen.findByRole('heading', { level: 1, name: messages.pages.history });

    await user.keyboard('gp');
    await waitFor(() => expect(router.state.location.pathname).toBe(`/projects/${ready.id}/ask`));
    await user.keyboard('gh');
    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/projects/${ready.id}/history`),
    );
  });
});
