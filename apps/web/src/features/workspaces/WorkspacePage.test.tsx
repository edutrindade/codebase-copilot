import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { indexJob, page, project, readySnapshot, workspace } from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';

const ws = workspace({ name: 'Clientes', projectCount: 2 });
const detail = `/api/v1/workspaces/${ws.id}`;
const projects = `${detail}/projects`;

async function chooseFromMenu(trigger: string, item: string) {
  await userEvent.click(await screen.findByRole('button', { name: trigger }));
  await userEvent.click(await screen.findByRole('menuitem', { name: item }));
}

describe('WorkspacePage', () => {
  test('lists the projects with their source and status, not only by color', async () => {
    server.use(
      http.get(detail, () => HttpResponse.json(ws)),
      http.get(projects, () =>
        HttpResponse.json(
          page([
            project({
              id: '0192f5c4-0000-7000-8000-000000000102',
              name: 'api',
              indexing: {
                id: '0192f5c4-0000-7000-8000-000000000301',
                status: 'RUNNING',
                stage: 'PROCESSING_FILES',
                progress: { total: 10, completed: 4 },
                cancelRequested: false,
              },
            }),
            project({
              id: '0192f5c4-0000-7000-8000-000000000103',
              name: 'web',
              source: { type: 'ZIP', currentImport: null },
            }),
            project({
              id: '0192f5c4-0000-7000-8000-000000000104',
              name: 'core',
              activeSnapshot: readySnapshot(),
              outdatedReasons: ['SETTINGS_CHANGED'],
            }),
          ]),
        ),
      ),
    );
    renderRoute(`/workspaces/${ws.id}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Clientes' })).toBeInTheDocument();
    const api = (await screen.findByRole('link', { name: 'api' })).closest('li');
    expect(api).toHaveTextContent(messages.projects.status.INDEXING);
    expect(api).toHaveTextContent(messages.projects.progress(4, 10));
    expect(
      within(api as HTMLElement).getByRole('progressbar', {
        name: messages.projects.progressLabel('api'),
      }),
    ).toHaveAttribute('aria-valuenow', '4');
    expect(api).toHaveTextContent(messages.projects.sourceLocal('default', 'codebase-copilot'));
    expect(screen.getByRole('link', { name: 'api' })).toHaveAttribute(
      'href',
      '/projects/0192f5c4-0000-7000-8000-000000000102',
    );
    const web = screen.getByRole('link', { name: 'web' }).closest('li');
    expect(web).toHaveTextContent(messages.projects.sourceZip);
    expect(web).toHaveTextContent(messages.projects.status.AWAITING_UPLOAD);
    const core = screen.getByRole('link', { name: 'core' }).closest('li');
    expect(core).toHaveTextContent(messages.projects.status.OUTDATED);
    expect(core).toHaveTextContent(messages.projects.snapshot);
    expect(web).toHaveTextContent(messages.projects.noSnapshot);
    expect(await axeViolations()).toEqual([]);
  });

  test('an empty workspace points to the new project flow', async () => {
    server.use(
      http.get(detail, () => HttpResponse.json(workspace({ ...ws, projectCount: 0 }))),
      http.get(projects, () => HttpResponse.json(page([]))),
    );
    const { router } = renderRoute(`/workspaces/${ws.id}`);

    expect(
      await screen.findByRole('heading', { level: 2, name: messages.projects.emptyTitle }),
    ).toBeInTheDocument();
    const empty = screen.getByRole('heading', { level: 2 }).closest('div') as HTMLElement;
    await userEvent.click(within(empty).getByRole('link', { name: messages.projects.emptyAction }));
    expect(router.state.location.pathname).toBe(`/workspaces/${ws.id}/new`);
  });

  test('an unknown workspace explains it and links back, without a retry', async () => {
    server.use(http.get(detail, () => problem(404, 'WORKSPACE_NOT_FOUND')));
    renderRoute(`/workspaces/${ws.id}`);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.recovery.goBack);
    expect(
      within(alert).queryByRole('button', { name: messages.errors.retry }),
    ).not.toBeInTheDocument();
    const trail = screen.getByRole('navigation', { name: messages.breadcrumbs.label });
    expect(within(trail).getByRole('link', { name: messages.pages.workspaces })).toHaveAttribute(
      'href',
      '/',
    );
  });

  test('deletes a project after confirmation', async () => {
    let items = [project({ name: 'api' })];
    server.use(
      http.get(detail, () => HttpResponse.json(ws)),
      http.get(projects, () => HttpResponse.json(page(items))),
      http.delete('/api/v1/projects/:id', () => {
        items = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoute(`/workspaces/${ws.id}`);

    await chooseFromMenu(messages.projects.actions('api'), messages.projects.deleteItem);
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent(messages.projects.deleteDetail);
    await userEvent.click(
      within(dialog).getByRole('button', { name: messages.projects.deleteConfirm }),
    );

    expect(
      await screen.findByRole('heading', { level: 2, name: messages.projects.emptyTitle }),
    ).toBeInTheDocument();
  });

  test('explains a busy project in the dialog', async () => {
    server.use(
      http.get(detail, () => HttpResponse.json(ws)),
      http.get(projects, () => HttpResponse.json(page([project({ name: 'api' })]))),
      http.delete('/api/v1/projects/:id', () => problem(409, 'PROJECT_BUSY')),
    );
    renderRoute(`/workspaces/${ws.id}`);

    await chooseFromMenu(messages.projects.actions('api'), messages.projects.deleteItem);
    const dialog = await screen.findByRole('alertdialog');
    await userEvent.click(
      within(dialog).getByRole('button', { name: messages.projects.deleteConfirm }),
    );

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      messages.recovery.waitForJob,
    );
  });

  test('the menu offers asking and reindexing only for an indexed project', async () => {
    const user = userEvent.setup();
    let started = 0;
    const ready = project({ name: 'core', activeSnapshot: readySnapshot() });
    server.use(
      http.get(detail, () => HttpResponse.json(ws)),
      http.get(projects, () => HttpResponse.json(page([project({ name: 'api' }), ready]))),
      http.post(`/api/v1/projects/${ready.id}/index-jobs`, () => {
        started++;
        return HttpResponse.json(indexJob(ready.id), { status: 202 });
      }),
    );
    const { router } = renderRoute(`/workspaces/${ws.id}`);

    await user.click(await screen.findByRole('button', { name: messages.projects.actions('api') }));
    let menu = await screen.findByRole('menu');
    expect(
      within(menu).getByRole('menuitem', { name: messages.projects.open }),
    ).toBeInTheDocument();
    expect(
      within(menu).queryByRole('menuitem', { name: messages.projects.ask }),
    ).not.toBeInTheDocument();
    expect(
      within(menu).queryByRole('menuitem', { name: messages.projects.reindex }),
    ).not.toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(screen.getByRole('button', { name: messages.projects.actions('core') }));
    menu = await screen.findByRole('menu');
    expect(within(menu).getByRole('menuitem', { name: messages.projects.ask })).toBeInTheDocument();
    await user.click(within(menu).getByRole('menuitem', { name: messages.projects.reindex }));

    const confirm = await screen.findByRole('alertdialog', { name: messages.reindexDialog.title });
    expect(within(confirm).getByText(messages.privacy.localOnly)).toBeInTheDocument();
    expect(started).toBe(0);
    await user.click(within(confirm).getByRole('button', { name: messages.reindexDialog.confirm }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/projects/${ready.id}`));
    expect(started).toBe(1);
  });

  test('asking from the menu opens the question screen of the project', async () => {
    const ready = project({ name: 'core', activeSnapshot: readySnapshot() });
    server.use(
      http.get(detail, () => HttpResponse.json(ws)),
      http.get(projects, () => HttpResponse.json(page([ready]))),
    );
    const { router } = renderRoute(`/workspaces/${ws.id}`);

    await chooseFromMenu(messages.projects.actions('core'), messages.projects.ask);

    await waitFor(() => expect(router.state.location.pathname).toBe(`/projects/${ready.id}/ask`));
  });

  test('a failed project list offers a retry', async () => {
    server.use(
      http.get(detail, () => HttpResponse.json(ws)),
      http.get(projects, () => HttpResponse.error()),
    );
    renderRoute(`/workspaces/${ws.id}`);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.errors.networkTitle);
    expect(within(alert).getByRole('button', { name: messages.errors.retry })).toBeInTheDocument();
  });
});
