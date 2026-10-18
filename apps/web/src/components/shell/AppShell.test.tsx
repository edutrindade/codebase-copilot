import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { page, project, providers, readySnapshot, settings, workspace } from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';
import { LAST_WORKSPACE_KEY, SIDEBAR_COLLAPSED_KEY } from './storageKeys';

const ws = workspace({ name: 'Clientes', projectCount: 2 });
const ready = project({ name: 'api', activeSnapshot: readySnapshot() });
const indexing = project({
  id: '0192f5c4-0000-7000-8000-000000000102',
  name: 'web',
  indexing: {
    id: '0192f5c4-0000-7000-8000-000000000301',
    status: 'RUNNING',
    stage: 'PROCESSING_FILES',
    progress: { total: 10, completed: 4 },
    cancelRequested: false,
  },
});
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
    http.get(`/api/v1/workspaces/${ws.id}`, () => HttpResponse.json(ws)),
    http.get(`/api/v1/workspaces/${ws.id}/projects`, () =>
      HttpResponse.json(page([ready, indexing])),
    ),
    http.get(`/api/v1/projects/${ready.id}`, () => HttpResponse.json(ready)),
    http.get(`/api/v1/projects/${ready.id}/conversations`, () =>
      HttpResponse.json(page([summary])),
    ),
    http.get(`/api/v1/projects/${ready.id}/settings`, () => HttpResponse.json(settings())),
  ];
}

// jsdom trims the hidden ", " between the name and the status; browsers read it.
function projectLink(name: string, status: string) {
  const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escape(name)},\\s?${escape(status)}$`);
}

function switcher(name: string) {
  return new RegExp(`^${messages.shell.workspaceFallback}:\\s?${name}$`);
}

function sidebar() {
  return screen.getByRole('navigation', { name: messages.shell.mainNav });
}

function trail() {
  return screen.getByRole('navigation', { name: messages.breadcrumbs.label });
}

afterEach(() => vi.unstubAllGlobals());

describe('AppShell', () => {
  test('the sidebar lists the projects of the workspace with their status in words', async () => {
    server.use(...handlers());
    renderRoute(`/workspaces/${ws.id}`);

    const nav = sidebar();
    expect(
      await within(nav).findByRole('link', {
        name: projectLink('api', messages.projects.status.READY),
      }),
    ).toHaveAttribute('href', `/projects/${ready.id}`);
    expect(
      within(nav).getByRole('link', {
        name: projectLink(
          'web',
          `${messages.projects.status.INDEXING} · ${messages.projects.progress(4, 10)}`,
        ),
      }),
    ).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: messages.shell.newProject })).toHaveAttribute(
      'href',
      `/workspaces/${ws.id}/new`,
    );
    expect(within(nav).getByRole('button', { name: switcher('Clientes') })).toBeInTheDocument();
    expect(within(trail()).getByRole('link', { name: messages.pages.workspaces })).toHaveAttribute(
      'href',
      '/',
    );
    expect(within(trail()).getByText('Clientes')).toHaveAttribute('aria-current', 'page');
    await waitFor(() => expect(document.title).toBe('Clientes · Codebase Copilot'));
    expect(window.localStorage.getItem(LAST_WORKSPACE_KEY)).toBe(ws.id);
    expect(await axeViolations()).toEqual([]);
  });

  test('the project area has a common header, tabs as links and the recent conversations', async () => {
    server.use(...handlers());
    renderRoute(`/projects/${ready.id}/history`);

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.history }),
    ).toBeInTheDocument();
    const tabs = screen.getByRole('navigation', { name: messages.projectArea.tabsLabel });
    expect(
      within(tabs).getByRole('link', { name: messages.projectArea.tabs.history }),
    ).toHaveAttribute('aria-current', 'page');
    expect(
      within(tabs).getByRole('link', { name: messages.projectArea.tabs.overview }),
    ).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('link', { name: messages.projectArea.newQuestion })).toHaveAttribute(
      'href',
      `/projects/${ready.id}/ask`,
    );
    expect(await within(sidebar()).findByRole('link', { name: summary.title })).toHaveAttribute(
      'href',
      `/projects/${ready.id}/conversations/${summary.id}`,
    );
    expect(
      within(sidebar()).getByRole('link', {
        name: projectLink('api', messages.projects.status.READY),
      }),
    ).toHaveAttribute('aria-current', 'true');
    expect(within(trail()).getByRole('link', { name: 'Clientes' })).toBeInTheDocument();
    expect(within(trail()).getByRole('link', { name: 'api' })).toHaveAttribute(
      'href',
      `/projects/${ready.id}`,
    );
    expect(within(trail()).getByText(messages.pages.history)).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(document.title).toBe('Histórico · api · Codebase Copilot');
    expect(await axeViolations()).toEqual([]);
  });

  test('a navigation moves focus to the new heading and announces the screen', async () => {
    server.use(...handlers());
    renderRoute(`/projects/${ready.id}/history`);
    const tabs = await screen.findByRole('navigation', { name: messages.projectArea.tabsLabel });

    await userEvent.click(
      within(tabs).getByRole('link', { name: messages.projectArea.tabs.settings }),
    );

    const heading = await screen.findByRole('heading', { level: 1, name: messages.pages.settings });
    await waitFor(() => expect(heading).toHaveFocus());
    expect(document.title).toBe('Configurações · api · Codebase Copilot');
    expect(document.querySelector('[aria-live="polite"]')).toHaveTextContent(
      'Configurações · api · Codebase Copilot',
    );
  });

  test('the collapsed sidebar is remembered and keeps the names for screen readers', async () => {
    server.use(...handlers());
    const { unmount } = renderRoute(`/workspaces/${ws.id}`);

    await userEvent.click(within(sidebar()).getByRole('button', { name: messages.shell.collapse }));

    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY)).toBe('true');
    expect(sidebar()).toHaveClass('sidebar--collapsed');
    expect(
      within(sidebar()).getByRole('link', { name: messages.shell.newProject }),
    ).toBeInTheDocument();
    unmount();

    renderRoute(`/workspaces/${ws.id}`);
    expect(
      within(sidebar()).getByRole('button', { name: messages.shell.expand }),
    ).toBeInTheDocument();
  });

  test('the workspace menu switches workspaces', async () => {
    const other = workspace({ id: '0192f5c4-0000-7000-8000-000000000002', name: 'Interno' });
    server.use(
      ...handlers(),
      http.get('/api/v1/workspaces', () => HttpResponse.json(page([ws, other]))),
    );
    const { router } = renderRoute(`/workspaces/${ws.id}`);

    await userEvent.click(
      await within(sidebar()).findByRole('button', {
        name: switcher('Clientes'),
      }),
    );
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Interno' }));

    expect(router.state.location.pathname).toBe(`/workspaces/${other.id}`);
  });

  test('a last workspace that no longer exists is dropped', async () => {
    window.localStorage.setItem(LAST_WORKSPACE_KEY, ws.id);
    server.use(http.get(`/api/v1/workspaces/${ws.id}`, () => problem(404, 'WORKSPACE_NOT_FOUND')));
    renderRoute('/system');

    expect(
      await within(sidebar()).findByRole('button', { name: messages.shell.chooseWorkspace }),
    ).toBeInTheDocument();
    await waitFor(() => expect(window.localStorage.getItem(LAST_WORKSPACE_KEY)).toBeNull());
  });

  test('the top bar says whether data leaves the machine and links to the details', async () => {
    server.use(http.get('/api/v1/system/providers', () => HttpResponse.json(providers(true))));
    renderRoute('/system');

    const indicator = await screen.findByRole('link', {
      name: messages.shell.providers.label(messages.shell.providers.remote),
    });
    expect(indicator).toHaveAttribute('href', '/system');
    expect(indicator).toHaveTextContent(messages.shell.providers.remote);
  });

  test('on a phone the sidebar is a drawer that closes on navigation', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: false, addEventListener: () => {}, removeEventListener: () => {} })),
    );
    server.use(...handlers());
    const { router } = renderRoute(`/workspaces/${ws.id}`);
    expect(
      screen.queryByRole('navigation', { name: messages.shell.mainNav }),
    ).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: messages.shell.openNav }));
    const drawer = await screen.findByRole('dialog', { name: messages.shell.navTitle });
    await userEvent.click(
      await within(drawer).findByRole('link', {
        name: projectLink('api', messages.projects.status.READY),
      }),
    );

    expect(router.state.location.pathname).toBe(`/projects/${ready.id}`);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
