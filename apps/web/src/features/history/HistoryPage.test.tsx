import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { conversation, page, project, readySnapshot } from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';

const text = messages.history;
const base = project({ activeSnapshot: readySnapshot() });
const projectUrl = `/api/v1/projects/${base.id}`;
const summary = conversation().conversation;

describe('HistoryPage', () => {
  test('lists the conversations with links, counts and dates, page by page', async () => {
    const pages: (string | null)[] = [];
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/conversations`, ({ request }) => {
        const requested = new URL(request.url).searchParams.get('page');
        pages.push(requested);
        const item =
          requested === '1'
            ? { ...summary, id: '0192f5c4-0000-7000-8000-000000000502', title: 'Segunda página' }
            : { ...summary, questionCount: 3 };
        return HttpResponse.json(page([item], { page: Number(requested ?? 0), totalPages: 2 }));
      }),
    );
    const { router } = renderRoute(`/projects/${base.id}/history`);

    expect(screen.getByRole('status')).toHaveTextContent(messages.loading);
    const link = await screen.findByRole('link', { name: summary.title });
    expect(link).toHaveAttribute('href', `/projects/${base.id}/conversations/${summary.id}`);
    const card = link.closest('li') as HTMLElement;
    expect(within(card).getByText(new RegExp(text.questionCount(3)))).toBeInTheDocument();
    expect(
      within(card).getByText(new RegExp(text.updatedAt(formatDateTime(summary.updatedAt)))),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: text.ask })).toHaveAttribute(
      'href',
      `/projects/${base.id}/ask`,
    );
    expect(await axeViolations()).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: messages.pagination.next }));

    expect(await screen.findByRole('link', { name: 'Segunda página' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?page=2');
    expect(pages).toEqual(['0', '1']);
  });

  test('deletes a conversation after confirmation and refreshes the list', async () => {
    let items = [summary];
    const deleted: string[] = [];
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/conversations`, () => HttpResponse.json(page(items))),
      http.delete(`/api/v1/conversations/${summary.id}`, () => {
        deleted.push(summary.id);
        items = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoute(`/projects/${base.id}/history`);

    await userEvent.click(await screen.findByRole('button', { name: text.delete(summary.title) }));
    const dialog = await screen.findByRole('alertdialog', { name: text.deleteTitle });
    expect(dialog).toHaveTextContent(summary.title);
    expect(await axeViolations()).toEqual([]);
    await userEvent.click(within(dialog).getByRole('button', { name: text.deleteConfirm }));

    expect(await screen.findByRole('heading', { name: text.emptyTitle })).toBeInTheDocument();
    expect(deleted).toEqual([summary.id]);
  });

  test('a failed deletion keeps the dialog open with the problem', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/conversations`, () => HttpResponse.json(page([summary]))),
      http.delete(`/api/v1/conversations/${summary.id}`, () =>
        problem(404, 'CONVERSATION_NOT_FOUND', { title: 'Conversa não encontrada' }),
      ),
    );
    renderRoute(`/projects/${base.id}/history`);

    await userEvent.click(await screen.findByRole('button', { name: text.delete(summary.title) }));
    const dialog = await screen.findByRole('alertdialog', { name: text.deleteTitle });
    await userEvent.click(within(dialog).getByRole('button', { name: text.deleteConfirm }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Conversa não encontrada');
  });

  test('without conversations, invites to ask only when there is an active snapshot', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/conversations`, () => HttpResponse.json(page([]))),
    );
    const { unmount } = renderRoute(`/projects/${base.id}/history`);

    expect(await screen.findByRole('heading', { name: text.emptyTitle })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: text.ask })).toBeInTheDocument();
    unmount();

    server.use(http.get(projectUrl, () => HttpResponse.json(project())));
    renderRoute(`/projects/${base.id}/history`);
    expect(await screen.findByRole('heading', { name: text.emptyTitle })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: text.ask })).not.toBeInTheDocument();
  });

  test('a failed list offers a retry; a missing project does not', async () => {
    let calls = 0;
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/conversations`, () => {
        calls++;
        return calls === 1 ? problem(500, 'INTERNAL_ERROR') : HttpResponse.json(page([summary]));
      }),
    );
    const { unmount } = renderRoute(`/projects/${base.id}/history`);

    await userEvent.click(await screen.findByRole('button', { name: messages.errors.retry }));
    expect(await screen.findByRole('link', { name: summary.title })).toBeInTheDocument();
    unmount();

    server.use(http.get(projectUrl, () => problem(404, 'PROJECT_NOT_FOUND')));
    renderRoute(`/projects/${base.id}/history`);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.errors.retry })).not.toBeInTheDocument();
  });
});
