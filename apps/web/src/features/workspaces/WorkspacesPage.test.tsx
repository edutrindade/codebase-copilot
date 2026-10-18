import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { RECENT_PROJECTS_KEY } from '../../app/recentProjects';
import { page, project, workspace } from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';
import type { Workspace } from './api';

const workspaces = '/api/v1/workspaces';

describe('WorkspacesPage', () => {
  test('shows a loading state, then the first use with an optional name', async () => {
    server.use(http.get(workspaces, () => HttpResponse.json(page([]))));

    renderRoute('/');

    expect(screen.getByRole('status')).toHaveTextContent(messages.loading);
    expect(
      await screen.findByRole('heading', { level: 1, name: messages.workspaces.firstUseTitle }),
    ).toBeInTheDocument();
    expect(screen.getByText(messages.workspaces.firstUseDetail)).toBeInTheDocument();
    expect(screen.getByLabelText(messages.workspaces.nameLabel)).not.toBeRequired();
    expect(await axeViolations()).toEqual([]);
  });

  test('creating a workspace without a name sends null and opens it', async () => {
    let body: unknown;
    const created = workspace({ id: '0192f5c4-0000-7000-8000-000000000009' });
    server.use(
      http.get(workspaces, () => HttpResponse.json(page([]))),
      http.post(workspaces, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(created, { status: 201 });
      }),
    );
    const { router } = renderRoute('/');

    await userEvent.click(await screen.findByRole('button', { name: messages.workspaces.create }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/workspaces/${created.id}`));
    expect(body).toEqual({ name: null });
  });

  test('shows the field errors of a rejected name', async () => {
    server.use(
      http.get(workspaces, () => HttpResponse.json(page([]))),
      http.post(workspaces, () =>
        problem(400, 'VALIDATION_FAILED', {
          errors: [{ field: 'name', message: 'O nome tem no máximo 100 caracteres.' }],
        }),
      ),
    );
    renderRoute('/');

    await userEvent.type(await screen.findByLabelText(messages.workspaces.nameLabel), 'x');
    await userEvent.click(screen.getByRole('button', { name: messages.workspaces.create }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('O nome tem no máximo 100 caracteres.');
    expect(alert).toHaveTextContent(messages.recovery.fixInput);
  });

  test('lists workspaces with their project count and pages through them', async () => {
    const requested: (string | null)[] = [];
    server.use(
      http.get(workspaces, ({ request }) => {
        const pageParam = new URL(request.url).searchParams.get('page');
        requested.push(pageParam);
        const name = pageParam === '1' ? 'Segunda página' : 'Clientes';
        return HttpResponse.json({
          ...page([workspace({ name, projectCount: 3 })], {
            page: Number(pageParam),
            totalPages: 2,
          }),
          totalItems: 21,
        });
      }),
    );
    const { router } = renderRoute('/');

    expect(await screen.findByRole('link', { name: 'Clientes' })).toBeInTheDocument();
    expect(screen.getByText(/3 projetos/)).toBeInTheDocument();
    expect(screen.getByText(messages.pagination.status(1, 2))).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 2, name: messages.workspaces.newTitle }),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: messages.pagination.next }));

    expect(await screen.findByRole('link', { name: 'Segunda página' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?page=2');
    expect(requested).toEqual(['0', '1']);
    expect(await axeViolations()).toEqual([]);
  });

  test('deletes a workspace after confirmation', async () => {
    let items: Workspace[] = [workspace({ name: 'Antigo' })];
    server.use(
      http.get(workspaces, () => HttpResponse.json(items.length > 0 ? page(items) : page([]))),
      http.delete(`${workspaces}/:id`, () => {
        items = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoute('/');

    await userEvent.click(
      await screen.findByRole('button', { name: messages.workspaces.delete('Antigo') }),
    );
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent(messages.workspaces.deleteTitle('Antigo'));
    expect(await axeViolations()).toEqual([]);
    await userEvent.click(
      within(dialog).getByRole('button', { name: messages.workspaces.deleteConfirm }),
    );

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.workspaces.firstUseTitle }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  test('explains a workspace that still has projects and keeps the dialog open', async () => {
    server.use(
      http.get(workspaces, () =>
        HttpResponse.json(page([workspace({ name: 'Clientes', projectCount: 2 })])),
      ),
      http.delete(`${workspaces}/:id`, () => problem(409, 'WORKSPACE_NOT_EMPTY')),
    );
    renderRoute('/');

    await userEvent.click(
      await screen.findByRole('button', { name: messages.workspaces.delete('Clientes') }),
    );
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent(messages.workspaces.deleteNotEmpty(2));
    await userEvent.click(
      within(dialog).getByRole('button', { name: messages.workspaces.deleteConfirm }),
    );

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      messages.recovery.removeProjects,
    );
    await userEvent.click(within(dialog).getByRole('button', { name: messages.confirm.cancel }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  test('a failed load offers a retry', async () => {
    let calls = 0;
    server.use(
      http.get(workspaces, () => {
        calls += 1;
        return calls === 1
          ? problem(500, 'INTERNAL_ERROR')
          : HttpResponse.json(page([workspace({ name: 'Clientes' })]));
      }),
    );
    renderRoute('/');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.recovery.retry);
    await userEvent.click(within(alert).getByRole('button', { name: messages.errors.retry }));

    expect(await screen.findByRole('link', { name: 'Clientes' })).toBeInTheDocument();
  });

  test('continues from the projects opened in this browser, dropping removed ones', async () => {
    const kept = project({ name: 'sample-shop' });
    const removedId = '0192f5c4-0000-7000-8000-000000000199';
    window.localStorage.setItem(RECENT_PROJECTS_KEY, JSON.stringify([removedId, kept.id]));
    server.use(
      http.get(workspaces, () => HttpResponse.json(page([workspace()]))),
      http.get(`/api/v1/projects/${kept.id}`, () => HttpResponse.json(kept)),
      http.get(`/api/v1/projects/${removedId}`, () => problem(404, 'PROJECT_NOT_FOUND')),
    );
    renderRoute('/');

    const recent = await screen.findByRole('region', { name: messages.workspaces.continueTitle });
    expect(within(recent).getByRole('link', { name: 'sample-shop' })).toHaveAttribute(
      'href',
      `/projects/${kept.id}`,
    );
    expect(within(recent).getAllByRole('listitem')).toHaveLength(1);
    await waitFor(() =>
      expect(window.localStorage.getItem(RECENT_PROJECTS_KEY)).toBe(JSON.stringify([kept.id])),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
