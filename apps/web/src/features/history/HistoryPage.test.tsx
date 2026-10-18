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
import { groupByPeriod } from './periods';

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
    const main = screen.getByRole('main');
    const link = await within(main).findByRole('link', { name: summary.title });
    expect(link).toHaveAttribute('href', `/projects/${base.id}/conversations/${summary.id}`);
    const card = link.closest('li') as HTMLElement;
    expect(within(card).getByText(new RegExp(text.questionCount(3)))).toBeInTheDocument();
    expect(card).toHaveTextContent(formatDateTime(summary.updatedAt));
    const period = groupByPeriod([summary])[0]?.period ?? 'older';
    expect(within(main).getByRole('region', { name: text.periods[period] })).toContainElement(card);
    expect(screen.getByRole('link', { name: text.ask })).toHaveAttribute(
      'href',
      `/projects/${base.id}/ask`,
    );
    expect(await axeViolations()).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: messages.pagination.next }));

    expect(await within(main).findByRole('link', { name: 'Segunda página' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?page=2');
    // The sidebar reads the first page too, under the same cache key.
    expect(new Set(pages)).toEqual(new Set(['0', '1']));
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

    await openDelete();
    const dialog = await screen.findByRole('alertdialog', { name: text.deleteTitle });
    expect(dialog).toHaveTextContent(summary.title);
    expect(await axeViolations()).toEqual([]);
    await userEvent.click(within(dialog).getByRole('button', { name: text.deleteConfirm }));

    expect(await screen.findByRole('heading', { name: text.emptyTitle })).toBeInTheDocument();
    expect(deleted).toEqual([summary.id]);
    expect(await screen.findByText(messages.toast.conversationDeleted)).toBeInTheDocument();
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

    await openDelete();
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
    let failing = true;
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/conversations`, () =>
        failing ? problem(500, 'INTERNAL_ERROR') : HttpResponse.json(page([summary])),
      ),
    );
    const { unmount } = renderRoute(`/projects/${base.id}/history`);

    const retry = await screen.findByRole('button', { name: messages.errors.retry });
    failing = false;
    await userEvent.click(retry);
    expect(
      await within(screen.getByRole('main')).findByRole('link', { name: summary.title }),
    ).toBeInTheDocument();
    unmount();

    server.use(http.get(projectUrl, () => problem(404, 'PROJECT_NOT_FOUND')));
    renderRoute(`/projects/${base.id}/history`);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.errors.retry })).not.toBeInTheDocument();
  });
});

async function openDelete() {
  await userEvent.click(await screen.findByRole('button', { name: text.actions(summary.title) }));
  await userEvent.click(await screen.findByRole('menuitem', { name: text.deleteItem }));
}
