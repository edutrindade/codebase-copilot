import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import {
  indexJob,
  inventory,
  page,
  project,
  providers,
  readySnapshot,
  snapshotDetail,
  usage,
} from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';
import type { Project } from '../workspaces/api';

const base = project();
const projectUrl = `/api/v1/projects/${base.id}`;
const snapshotId = '0192f5c4-0000-7000-8000-000000000201';
const reviewHref = `/workspaces/${base.workspaceId}/new?project=${base.id}`;

function handlers(
  current: () => Project,
  options: { jobs?: unknown[]; providerNone?: boolean } = {},
) {
  return [
    http.get(projectUrl, () => HttpResponse.json(current())),
    http.get(`${projectUrl}/index-jobs`, () => HttpResponse.json(page(options.jobs ?? []))),
    http.get(`${projectUrl}/snapshots`, () => HttpResponse.json(page([snapshotDetail()]))),
    http.get(`/api/v1/snapshots/${snapshotId}`, () => HttpResponse.json(snapshotDetail())),
    http.get(`/api/v1/snapshots/${snapshotId}/files`, () => HttpResponse.json(page([]))),
    http.get(`${projectUrl}/usage`, () => HttpResponse.json(usage())),
    http.get('/api/v1/system/providers', () =>
      HttpResponse.json(
        options.providerNone
          ? {
              ...providers(),
              embeddings: { provider: 'none', model: null, dimensions: null, remote: false },
            }
          : providers(),
      ),
    ),
  ];
}

describe('ProjectPage', () => {
  test('summarizes the active snapshot, why it is outdated, the unindexed files and the history', async () => {
    const partial = readySnapshot('PARTIAL');
    partial.counts = { ...partial.counts, failedFiles: 2, unsupportedFiles: 1 };
    const current = project({ activeSnapshot: partial, outdatedReasons: ['SETTINGS_CHANGED'] });
    const statuses: string[] = [];
    server.use(
      http.get(`/api/v1/snapshots/${snapshotId}`, () =>
        HttpResponse.json(
          snapshotDetail({
            status: 'PARTIAL',
            embedding: { provider: 'fake', model: 'fake', dimensions: 8, tokens: null },
            inventory: { ...inventory(), truncated: false },
          }),
        ),
      ),
      http.get(`${projectUrl}/snapshots`, () =>
        HttpResponse.json(
          page([
            snapshotDetail({
              status: 'PARTIAL',
              changes: { added: 3, modified: 12, removed: 1, unchanged: 959 },
            }),
            snapshotDetail({
              id: '0192f5c4-0000-7000-8000-000000000200',
              active: false,
              prunedAt: '2026-10-16T13:00:00Z',
            }),
          ]),
        ),
      ),
      http.get(`/api/v1/snapshots/${snapshotId}/files`, ({ request }) => {
        const status = new URL(request.url).searchParams.get('status') ?? '';
        statuses.push(status);
        return HttpResponse.json(
          page(
            status === 'FAILED'
              ? [
                  {
                    relativePath: 'src/Broken.java',
                    status: 'FAILED',
                    reason: 'READ_ERROR',
                    language: 'java',
                    sizeBytes: 100,
                    lineCount: null,
                    contentHash: null,
                    chunkCount: 0,
                  },
                ]
              : [
                  {
                    relativePath: 'data/dump.sql',
                    status: 'UNSUPPORTED',
                    reason: 'LINE_TOO_LONG',
                    language: 'sql',
                    sizeBytes: 524288,
                    lineCount: null,
                    contentHash: null,
                    chunkCount: 0,
                  },
                  {
                    relativePath: 'scripts/publish.sh',
                    status: 'UNSUPPORTED',
                    reason: 'SENSITIVE_CONTENT',
                    language: 'shell',
                    sizeBytes: 120,
                    lineCount: null,
                    contentHash: null,
                    chunkCount: 0,
                  },
                ],
          ),
        );
      }),
      ...handlers(() => current),
    );
    renderRoute(`/projects/${base.id}`);

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.overview }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.projectArea.newQuestion })).toHaveAttribute(
      'href',
      `/projects/${base.id}/ask`,
    );
    const active = screen.getByRole('region', { name: messages.project.snapshot.title });
    expect(active).toHaveTextContent(messages.project.snapshot.status.PARTIAL);
    expect(
      await within(active).findByText(messages.project.snapshot.embedding('fake', 'fake', 8)),
    ).toBeInTheDocument();
    expect(within(active).getByRole('rowheader', { name: 'java' })).toBeInTheDocument();
    expect(
      screen.getByText(messages.project.outdated.reasons.SETTINGS_CHANGED),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.project.reindexTitle })).toBeInTheDocument();

    const unindexed = screen.getByRole('region', { name: messages.project.unindexed.title });
    expect(await within(unindexed).findByText('src/Broken.java')).toBeInTheDocument();
    expect(unindexed).toHaveTextContent(messages.project.unindexed.reasons.READ_ERROR);
    await userEvent.click(
      within(unindexed).getByRole('radio', { name: messages.project.unindexed.UNSUPPORTED(1) }),
    );
    expect(await within(unindexed).findByText('data/dump.sql')).toBeInTheDocument();
    expect(unindexed).toHaveTextContent(messages.project.unindexed.reasons.LINE_TOO_LONG);
    expect(within(unindexed).getByRole('row', { name: /scripts\/publish\.sh/ })).toHaveTextContent(
      messages.project.unindexed.reasons.SENSITIVE_CONTENT,
    );
    expect(statuses).toEqual(['FAILED', 'UNSUPPORTED']);

    const history = screen.getByRole('region', { name: messages.project.snapshots.title });
    expect(
      await within(history).findByText(messages.project.snapshots.changes(3, 12, 1, 959)),
    ).toBeInTheDocument();
    expect(history).toHaveTextContent(messages.project.snapshots.active);
    expect(history).toHaveTextContent(messages.project.snapshots.pruned);
    expect(await axeViolations()).toEqual([]);
  });

  test('follows the indexing stage by stage and shows the snapshot when it ends', async () => {
    let current = project({
      indexing: {
        id: '0192f5c4-0000-7000-8000-000000000301',
        status: 'RUNNING',
        stage: 'PROCESSING_FILES',
        progress: { total: 10, completed: 4 },
        cancelRequested: false,
      },
    });
    let snapshotCalls = 0;
    server.use(
      http.get(`${projectUrl}/snapshots`, () => {
        snapshotCalls++;
        return HttpResponse.json(page(snapshotCalls > 1 ? [snapshotDetail()] : []));
      }),
      http.get(`/api/v1/workspaces/${base.workspaceId}/projects`, () =>
        HttpResponse.json(page([current])),
      ),
      ...handlers(() => current, { providerNone: true }),
    );
    renderRoute(`/projects/${base.id}`);

    const progress = await screen.findByRole('region', {
      name: messages.project.progress.title,
    });
    const stages = within(progress).getByRole('list', {
      name: messages.project.progress.stagesLabel,
    });
    await waitFor(() => expect(within(stages).getAllByRole('listitem')).toHaveLength(4));
    expect(stages).not.toHaveTextContent(messages.project.progress.stages.EMBEDDING);
    expect(
      within(stages).getByText(messages.project.progress.stages.SCANNING).closest('li'),
    ).toHaveTextContent(messages.project.progress.state.done);
    const currentStage = stages.querySelector('[aria-current="step"]');
    expect(currentStage).toHaveTextContent(messages.project.progress.stages.PROCESSING_FILES);
    expect(currentStage).toHaveTextContent(messages.project.progress.files(4, 10));
    expect(
      screen.queryByRole('button', { name: messages.project.indexTitle }),
    ).not.toBeInTheDocument();
    expect(await screen.findByText(messages.project.snapshots.empty)).toBeInTheDocument();
    expect(document.title).toMatch(/^\(40%\) /);
    expect(await axeViolations()).toEqual([]);

    current = project({
      activeSnapshot: readySnapshot(),
      lastJob: {
        id: '0192f5c4-0000-7000-8000-000000000301',
        status: 'COMPLETED',
        errorCode: null,
        finishedAt: '2026-10-16T12:10:00Z',
      },
    });

    expect(
      await screen.findByRole(
        'region',
        { name: messages.project.snapshot.title },
        { timeout: 3000 },
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('region', { name: messages.project.progress.title }),
    ).not.toBeInTheDocument();
    await waitFor(() => expect(snapshotCalls).toBeGreaterThan(1));
    expect(document.title).not.toMatch(/^\(/);
    expect((await screen.findAllByText(messages.toast.indexed)).length).toBeGreaterThan(0);
  });

  test('explains the last failed job with its message and the action for its code', async () => {
    server.use(
      ...handlers(() => project({ activeSnapshot: readySnapshot() }), {
        jobs: [
          indexJob(base.id, {
            status: 'FAILED',
            error: { code: 'NO_INCLUDED_FILES', message: 'Nenhum arquivo incluído.' },
            completedAt: '2026-10-16T12:10:00Z',
          }),
        ],
      }),
    );
    renderRoute(`/projects/${base.id}`);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.project.failure.title);
    expect(alert).toHaveTextContent('Nenhum arquivo incluído.');
    expect(alert).toHaveTextContent(messages.project.failure.previousInUse);
    expect(alert).toHaveTextContent(messages.recovery.reviewExclusions);
    expect(
      within(alert).getByRole('link', { name: messages.project.reviewPreAnalysis }),
    ).toHaveAttribute('href', reviewHref);
  });

  test('a project without a snapshot points to the pre-analysis and can be indexed', async () => {
    let current = project();
    server.use(
      http.get(`${projectUrl}/snapshots`, () => HttpResponse.json(page([]))),
      http.post(`${projectUrl}/index-jobs`, () => {
        current = project({
          indexing: {
            id: '0192f5c4-0000-7000-8000-000000000301',
            status: 'PENDING',
            stage: null,
            progress: null,
            cancelRequested: false,
          },
        });
        return HttpResponse.json(indexJob(base.id), { status: 202 });
      }),
      ...handlers(() => current),
    );
    renderRoute(`/projects/${base.id}`);

    expect(await screen.findByText(messages.project.notIndexed)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.project.reviewPreAnalysis })).toHaveAttribute(
      'href',
      reviewHref,
    );
    expect(
      screen.queryByRole('link', { name: messages.projectArea.newQuestion }),
    ).not.toBeInTheDocument();
    const start = screen.getByRole('button', { name: messages.project.indexTitle });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start);

    expect(await screen.findByText(messages.project.progress.queued)).toBeInTheDocument();
  });

  test('a ZIP project without upload links to the upload instead of indexing', async () => {
    server.use(
      http.get(`${projectUrl}/snapshots`, () => HttpResponse.json(page([]))),
      ...handlers(() => project({ source: { type: 'ZIP', currentImport: null } })),
    );
    renderRoute(`/projects/${base.id}`);

    expect(await screen.findByText(messages.project.awaitingUpload)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.project.sendZip })).toHaveAttribute(
      'href',
      reviewHref,
    );
    expect(
      screen.queryByRole('button', { name: messages.project.indexTitle }),
    ).not.toBeInTheDocument();
  });

  test('cancels the running indexing after confirmation and says it was asked', async () => {
    const jobId = '0192f5c4-0000-7000-8000-000000000301';
    const running = {
      id: jobId,
      status: 'RUNNING' as const,
      stage: 'PROCESSING_FILES' as const,
      progress: { total: 10, completed: 4 },
      cancelRequested: false,
    };
    let current = project({ activeSnapshot: readySnapshot(), indexing: running });
    const cancelled: string[] = [];
    server.use(
      ...handlers(() => current),
      http.post(`/api/v1/index-jobs/${jobId}/cancel`, () => {
        cancelled.push(jobId);
        current = { ...current, indexing: { ...running, cancelRequested: true } };
        return HttpResponse.json(indexJob(base.id, { status: 'RUNNING', cancelRequested: true }), {
          status: 202,
        });
      }),
    );
    renderRoute(`/projects/${base.id}`);
    const text = messages.project.progress;

    await userEvent.click(await screen.findByRole('button', { name: text.cancelLabel }));
    const dialog = await screen.findByRole('alertdialog', { name: text.cancelTitle });
    expect(dialog).toHaveTextContent(text.cancelDetail);
    await userEvent.click(within(dialog).getByRole('button', { name: text.cancelConfirm }));

    expect(await screen.findByText(text.cancelRequested)).toBeInTheDocument();
    expect(cancelled).toEqual([jobId]);
    expect(screen.queryByRole('button', { name: text.cancelLabel })).not.toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);
  });

  test('a cancelled last job is explained, with the previous snapshot still in use', async () => {
    const current = project({ activeSnapshot: readySnapshot() });
    server.use(
      ...handlers(() => current, {
        jobs: [
          indexJob(base.id, {
            status: 'CANCELLED',
            cancelRequested: true,
            completedAt: '2026-10-16T12:05:00Z',
          }),
        ],
      }),
    );
    renderRoute(`/projects/${base.id}`);

    expect(await screen.findByText(messages.project.cancelled.title)).toBeInTheDocument();
    expect(screen.getByText(messages.project.cancelled.previousInUse)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('shows the aggregated usage of the project', async () => {
    const current = project({ activeSnapshot: readySnapshot() });
    server.use(...handlers(() => current));
    renderRoute(`/projects/${base.id}`);
    const text = messages.project.usage;

    const panel = await screen.findByRole('region', { name: text.title });
    await within(panel).findByText(text.indexing);
    const totals = (title: string) =>
      within(panel).getByRole('heading', { name: title }).parentElement as HTMLElement;
    expect(within(totals(text.indexing)).getByText(text.failed).nextSibling).toHaveTextContent('1');
    expect(totals(text.indexing)).toHaveTextContent('1,5 s');
    expect(totals(text.questions)).toHaveTextContent('820 ms');
    expect(totals(text.tokens)).toHaveTextContent('12.000');
    expect(totals(text.tokens)).toHaveTextContent('US$');
    expect(panel).toHaveTextContent(text.unknownCost(1));
  });

  test('an unknown project explains it without a retry', async () => {
    server.use(http.get(projectUrl, () => problem(404, 'PROJECT_NOT_FOUND')));
    renderRoute(`/projects/${base.id}`);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.recovery.goBack);
    expect(
      within(alert).queryByRole('button', { name: messages.errors.retry }),
    ).not.toBeInTheDocument();
  });

  test('a ready project starts with suggested questions that fill the question without sending', async () => {
    let asked = false;
    server.use(
      http.post(`${projectUrl}/questions`, () => {
        asked = true;
        return HttpResponse.json({});
      }),
      ...handlers(() => project({ activeSnapshot: readySnapshot() })),
    );
    renderRoute(`/projects/${base.id}`);

    const start = await screen.findByRole('region', { name: messages.project.startHere.title });
    expect(
      await screen.findByRole('button', { name: messages.project.reindexTitle }),
    ).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);

    const [suggestion] = messages.ask.suggestions;
    await userEvent.click(within(start).getByRole('link', { name: suggestion }));

    expect(await screen.findByLabelText(messages.ask.form.label)).toHaveValue(suggestion);
    expect(asked).toBe(false);
  });
});
