import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { page, project, readySnapshot, workspace } from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';

const ws = workspace({ name: 'Clientes', projectCount: 2 });
const detail = `/api/v1/workspaces/${ws.id}`;
const projects = `${detail}/projects`;

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
    expect(api).toHaveTextContent(messages.projects.sourceLocal('default', 'codebase-copilot'));
    expect(screen.getByRole('link', { name: 'api' })).toHaveAttribute(
      'href',
      '/projects/0192f5c4-0000-7000-8000-000000000102',
    );
    const web = screen.getByRole('link', { name: 'web' }).closest('li');
    expect(web).toHaveTextContent(messages.projects.sourceZip);
    expect(web).toHaveTextContent(messages.projects.status.AWAITING_UPLOAD);
    expect(screen.getByRole('link', { name: 'core' }).closest('li')).toHaveTextContent(
      messages.projects.status.OUTDATED,
    );
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
    await userEvent.click(within(empty).getByRole('link', { name: messages.projects.newProject }));
    expect(router.state.location.pathname).toBe(`/workspaces/${ws.id}/new`);
  });

  test('an unknown workspace explains it and links back, without a retry', async () => {
    server.use(http.get(detail, () => problem(404, 'WORKSPACE_NOT_FOUND')));
    renderRoute(`/workspaces/${ws.id}`);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.recovery.goBack);
    expect(within(alert).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.projects.allWorkspaces })).toHaveAttribute(
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

    await userEvent.click(
      await screen.findByRole('button', { name: messages.projects.delete('api') }),
    );
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

    await userEvent.click(
      await screen.findByRole('button', { name: messages.projects.delete('api') }),
    );
    const dialog = await screen.findByRole('alertdialog');
    await userEvent.click(
      within(dialog).getByRole('button', { name: messages.projects.deleteConfirm }),
    );

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      messages.recovery.waitForJob,
    );
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
