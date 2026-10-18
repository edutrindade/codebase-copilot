import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, test, vi } from 'vitest';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import {
  answer,
  conversation,
  evidence,
  evidenceDetail,
  fileContent,
  project,
  providers,
  readySnapshot,
} from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';
import type { Project } from '../workspaces/api';
import type { Answer } from './api';

const text = messages.ask;
const base = project({ activeSnapshot: readySnapshot() });
const projectUrl = `/api/v1/projects/${base.id}`;
const conversationId = answer().conversationId;
const conversationUrl = `/api/v1/conversations/${conversationId}`;
const answeredSnapshot = '0192f5c4-0000-7000-8000-000000000201';
const activeSnapshot = '0192f5c4-0000-7000-8000-000000000202';
const citationName = text.citation('E1', 'src/billing/Refund.java', 'linhas 12–14');

const sizes = ['clientHeight', 'scrollHeight'] as const;

// jsdom has no layout: the code view gets a height and scrolling moves scrollTop.
beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
    configurable: true,
    get(this: HTMLElement) {
      return this.classList.contains('code-view') ? 400 : 0;
    },
  });
  Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
    configurable: true,
    get(this: HTMLElement) {
      return this.classList.contains('code-view') ? Number.MAX_SAFE_INTEGER : 0;
    },
  });
  HTMLElement.prototype.scrollTo = function (
    this: HTMLElement,
    options?: ScrollToOptions | number,
  ) {
    if (typeof options === 'object' && options.top !== undefined) {
      this.scrollTop = options.top;
      setTimeout(() => this.dispatchEvent(new Event('scroll')));
    }
  } as HTMLElement['scrollTo'];
});

afterAll(() => {
  for (const name of sizes) {
    Reflect.deleteProperty(HTMLElement.prototype, name);
  }
  Reflect.deleteProperty(HTMLElement.prototype, 'scrollTo');
});

afterEach(() => {
  window.localStorage.clear();
  vi.unstubAllGlobals();
});

function handlers(
  options: {
    current?: Project;
    remote?: boolean;
    answers?: Answer[];
  } = {},
) {
  return [
    http.get(projectUrl, () => HttpResponse.json(options.current ?? base)),
    http.get('/api/v1/system/providers', () => HttpResponse.json(providers(options.remote))),
    http.get(conversationUrl, () => HttpResponse.json(conversation(options.answers))),
  ];
}

function questionBox() {
  return screen.getByRole('textbox', { name: text.form.label });
}

describe('ChatPage', () => {
  test('asks from a suggestion, shows the wait and opens the new conversation', async () => {
    let release = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    let body: unknown;
    server.use(
      ...handlers(),
      http.post(`${projectUrl}/questions`, async ({ request }) => {
        body = await request.json();
        await gate;
        return HttpResponse.json(answer({ question: text.suggestions[0] }), { status: 201 });
      }),
    );
    const { router } = renderRoute(`/projects/${base.id}/ask`);

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.ask }),
    ).toBeVisible();
    expect(await screen.findByText(text.empty)).toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);
    await userEvent.click(screen.getByRole('button', { name: text.suggestions[0] }));
    expect(questionBox()).toHaveValue(text.suggestions[0]);
    expect(questionBox()).toHaveFocus();
    await userEvent.click(screen.getByRole('button', { name: text.form.submit }));

    expect(await screen.findByRole('status')).toHaveTextContent(text.waiting.status);
    expect(screen.getByText(text.waiting.limit)).toBeInTheDocument();
    expect(screen.getByText(text.suggestions[0], { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: text.form.submitting })).toBeDisabled();
    expect(body).toEqual({
      question: text.suggestions[0],
      conversationId: null,
      options: { retrievalMode: 'HYBRID', maxEvidence: 8 },
    });

    release();
    expect(await screen.findByRole('button', { name: citationName })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(
      `/projects/${base.id}/conversations/${conversationId}`,
    );
    expect(screen.getByText(text.status.ANSWERED)).toBeInTheDocument();
    expect(questionBox()).toHaveValue('');
    expect(screen.queryByText(text.suggestionsTitle)).not.toBeInTheDocument();
  });

  test('renders the answer as text, with citations, metrics and the evidence cited first', async () => {
    const answers = [
      answer({
        answer: 'Linha 1 <b>sem HTML</b> [E2]\nLinha 2 [E1] [E9]',
        evidence: [
          evidence(),
          evidence({
            id: '0192f5c4-0000-7000-8000-000000000702',
            citationLabel: 'E2',
            path: 'src/billing/Fees.java',
            otherPaths: ['copy/Fees.java'],
            citedStartLine: null,
            citedEndLine: null,
            startLine: 3,
            endLine: 3,
            rank: 2,
          }),
          evidence({
            id: '0192f5c4-0000-7000-8000-000000000703',
            citationLabel: 'E3',
            path: 'src/Other.java',
            citedStartLine: null,
            citedEndLine: null,
            validationStatus: 'NOT_CITED',
            rank: 0,
          }),
          evidence({
            id: '0192f5c4-0000-7000-8000-000000000704',
            citationLabel: 'E4',
            path: 'src/Bad.java',
            citedStartLine: null,
            citedEndLine: null,
            validationStatus: 'INVALID_RANGE',
            rank: 3,
          }),
        ],
        warnings: [
          { code: 'INVALID_CITATIONS_REMOVED', message: '1 citação inválida foi removida.' },
        ],
        metrics: {
          ...answer().metrics,
          costStatus: 'UNKNOWN',
          estimatedCost: null,
          costCurrency: null,
        },
      }),
    ];
    server.use(...handlers({ answers }));
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    expect(
      await screen.findByRole('heading', { level: 1, name: answers[0]?.question }),
    ).toBeInTheDocument();
    const body = screen.getByText(/sem HTML/);
    expect(body.querySelector('b')).toBeNull();
    expect(body).toHaveTextContent('Linha 1 <b>sem HTML</b> E2 Linha 2 E1 [E9]');
    expect(screen.getByText('1 citação inválida foi removida.')).toBeInTheDocument();
    expect(screen.getByText(text.metrics.costUnknown)).toBeInTheDocument();
    expect(screen.getByText(text.metrics.evidenceCount(4, 2))).toBeInTheDocument();
    expect(screen.getByText(text.metrics.tokensValue('4.210', '380'))).toBeInTheDocument();
    expect(screen.getByText('1,8 s')).toBeInTheDocument();

    const panel = screen.getByRole('region', { name: text.evidence.title });
    const titles = [...panel.querySelectorAll('.evidence-list > li .viewer__label')].map(
      (label) => label.textContent,
    );
    expect(titles).toEqual(['E1', 'E2', 'E4', 'E3']);
    expect(screen.getByText(new RegExp(text.evidence.status.NOT_CITED))).toBeInTheDocument();
    expect(screen.getByText(new RegExp(text.evidence.status.INVALID_RANGE))).toBeInTheDocument();
    expect(screen.getByText(text.evidence.otherPaths(1))).toBeInTheDocument();
    expect(screen.getByText('copy/Fees.java')).toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);
  });

  test('opens a citation in the viewer with the cited lines highlighted and in view', async () => {
    server.use(
      ...handlers(),
      http.get(`${projectUrl}/evidence/${evidence().id}`, () =>
        HttpResponse.json(evidenceDetail()),
      ),
      http.get(`/api/v1/snapshots/${answeredSnapshot}/files/content`, ({ request }) => {
        expect(new URL(request.url).searchParams.get('path')).toBe('src/billing/Refund.java');
        return HttpResponse.json(fileContent());
      }),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    await userEvent.click(await screen.findByRole('button', { name: citationName }));

    const code = await screen.findByRole('region', {
      name: text.viewer.codeLabel('src/billing/Refund.java'),
    });
    expect(screen.getByText(text.viewer.highlighted('linhas 12–14'))).toBeInTheDocument();
    expect(
      screen.getByText(text.viewer.fileInfo('java', 40, formatBytes(fileContent().sizeBytes))),
    ).toBeInTheDocument();
    const cited = code.querySelectorAll('.code-view__line--cited');
    expect([...cited].map((line) => line.getAttribute('data-line'))).toEqual(['12', '13', '14']);
    expect(within(cited[0] as HTMLElement).getByText(text.viewer.citedMarker)).toBeInTheDocument();
    expect(code.querySelector('[data-line="11"]')).not.toHaveClass('code-view__line--cited');
    expect(screen.getByText(new RegExp(text.evidence.selected))).toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);
  });

  test('renders a 1 MB file by window and scrolls to the cited lines', async () => {
    const lines = Array.from({ length: 50_000 }, (_, index) => `const value${index + 1} = 1;`);
    const cited = evidence({
      startLine: 30_000,
      endLine: 30_040,
      citedStartLine: 30_010,
      citedEndLine: 30_012,
    });
    server.use(
      ...handlers({ answers: [answer({ evidence: [cited] })] }),
      http.get(`${projectUrl}/evidence/${cited.id}`, () =>
        HttpResponse.json(
          evidenceDetail({
            startLine: 30_000,
            endLine: 30_040,
            citedStartLine: 30_010,
            citedEndLine: 30_012,
          }),
        ),
      ),
      http.get(`/api/v1/snapshots/${answeredSnapshot}/files/content`, () =>
        HttpResponse.json(fileContent({ content: lines.join('\n'), lineCount: lines.length })),
      ),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    await userEvent.click(
      await screen.findByRole('button', {
        name: text.citation('E1', 'src/billing/Refund.java', 'linhas 30010–30012'),
      }),
    );

    const code = await screen.findByRole('region', {
      name: text.viewer.codeLabel('src/billing/Refund.java'),
    });
    await waitFor(() => expect(code.querySelector('[data-line="30011"]')).not.toBeNull());
    expect(code.querySelectorAll('[data-line]').length).toBeLessThan(100);
    expect(code.querySelector('[data-line="1"]')).toBeNull();
  });

  test('warns when the file changed and opens the version of the active snapshot', async () => {
    const current = project({ activeSnapshot: { ...readySnapshot(), id: activeSnapshot } });
    server.use(
      ...handlers({ current }),
      http.get(`${projectUrl}/evidence/${evidence().id}`, () =>
        HttpResponse.json(evidenceDetail({ activeVersion: 'CHANGED' })),
      ),
      http.get(`/api/v1/snapshots/${answeredSnapshot}/files/content`, () =>
        HttpResponse.json(fileContent()),
      ),
      http.get(`/api/v1/snapshots/${activeSnapshot}/files/content`, () =>
        HttpResponse.json(fileContent({ content: 'novo\n', lineCount: 1, sizeBytes: 5 })),
      ),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    expect(await screen.findByText(new RegExp(text.olderSnapshot))).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: citationName }));
    expect(await screen.findByText(text.viewer.activeVersion.CHANGED)).toBeInTheDocument();
    expect(await screen.findByText(text.viewer.answeredVersion)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: text.viewer.showCurrent }));

    expect(await screen.findByText(text.viewer.currentVersion)).toBeInTheDocument();
    const code = screen.getByRole('region', {
      name: text.viewer.codeLabel('src/billing/Refund.java'),
    });
    expect(within(code).getByText('novo')).toBeInTheDocument();
    expect(code.querySelector('.code-view__line--cited')).toBeNull();
    expect(screen.getByRole('button', { name: text.viewer.showAnswered })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  test('shows only the stored snippet when the snapshot was pruned', async () => {
    server.use(
      ...handlers(),
      http.get(`${projectUrl}/evidence/${evidence().id}`, () =>
        HttpResponse.json(
          evidenceDetail({
            snapshotStatus: 'PRUNED',
            activeVersion: 'REMOVED',
            startLine: 11,
            endLine: 13,
            citedStartLine: 12,
            citedEndLine: 12,
            snippet: 'a\nb\nc',
          }),
        ),
      ),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    await userEvent.click(await screen.findByRole('button', { name: citationName }));

    expect(await screen.findByText(text.viewer.pruned)).toBeInTheDocument();
    expect(screen.getByText(text.viewer.activeVersion.REMOVED)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: text.viewer.snippetTitle })).toBeInTheDocument();
    const code = screen.getByRole('region', {
      name: text.viewer.codeLabel('src/billing/Refund.java'),
    });
    expect([...code.querySelectorAll('[data-line]')].map((line) => line.textContent)).toEqual([
      '11a',
      `12▶${text.viewer.citedMarker}b`,
      '13c',
    ]);
  });

  test('a failed answer and an answer without evidence read as answers, not screen errors', async () => {
    const answers = [
      answer({
        status: 'FAILED',
        answer: null,
        evidence: [],
        model: null,
        error: { code: 'GENERATION_RATE_LIMITED', message: 'O provedor limitou os pedidos.' },
      }),
      answer({
        answerId: '0192f5c4-0000-7000-8000-000000000602',
        question: 'E o frete?',
        status: 'ABSTAINED',
        answer: 'Não encontrei evidência suficiente.',
        evidence: [],
        model: null,
        warnings: [{ code: 'NO_EVIDENCE', message: 'Nenhum trecho relevante foi encontrado.' }],
        metrics: {
          latencyMs: 80,
          retrievalMs: 80,
          generationMs: null,
          inputTokens: null,
          outputTokens: null,
          estimatedCost: null,
          costCurrency: null,
          costStatus: 'UNKNOWN',
        },
      }),
    ];
    server.use(...handlers({ answers }));
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    expect(await screen.findByText('O provedor limitou os pedidos.')).toBeInTheDocument();
    expect(screen.getByText(text.failed)).toBeInTheDocument();
    expect(screen.getByText(messages.recovery.providerLimit)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByText(text.status.ABSTAINED)).toBeInTheDocument();
    expect(screen.getByText(text.warningHints.NO_EVIDENCE)).toBeInTheDocument();
    expect(screen.getAllByText(text.metrics.noModel)).toHaveLength(2);
    expect(screen.getByText(text.evidence.none)).toBeInTheDocument();
  });

  test('a pending answer refreshes until it is ready, and the header links to the history', async () => {
    let reads = 0;
    server.use(...handlers());
    server.use(
      http.get(conversationUrl, () => {
        reads++;
        return HttpResponse.json(
          conversation([
            reads === 1
              ? answer({
                  status: 'PENDING',
                  answer: null,
                  evidence: [],
                  model: null,
                  completedAt: null,
                })
              : answer(),
          ]),
        );
      }),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    expect(await screen.findByText(text.pending)).toBeInTheDocument();
    expect(screen.getByText(text.status.PENDING)).toBeInTheDocument();
    expect(screen.queryByText(text.metrics.title)).not.toBeInTheDocument();
    const tabs = screen.getByRole('navigation', { name: messages.projectArea.tabsLabel });
    expect(
      within(tabs).getByRole('link', { name: messages.projectArea.tabs.history }),
    ).toHaveAttribute('href', `/projects/${base.id}/history`);
    expect(within(tabs).getByRole('link', { name: messages.projectArea.tabs.ask })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(screen.getByRole('link', { name: text.newConversation })).toHaveAttribute(
      'href',
      `/projects/${base.id}/ask`,
    );

    expect(
      await screen.findByRole('button', { name: citationName }, { timeout: 4000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText(text.pending)).not.toBeInTheDocument();
  });

  test('a follow-up question goes to the same conversation and is appended', async () => {
    let body: unknown;
    const followUp = answer({
      answerId: '0192f5c4-0000-7000-8000-000000000602',
      question: 'E o frete?',
      answer: 'O frete não entra [E1].',
    });
    server.use(
      ...handlers(),
      http.post(`${projectUrl}/questions`, async ({ request }) => {
        body = await request.json();
        server.use(
          http.get(conversationUrl, () => HttpResponse.json(conversation([answer(), followUp]))),
        );
        return HttpResponse.json(followUp, { status: 201 });
      }),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    await userEvent.type(
      await screen.findByRole('textbox', { name: text.form.label }),
      'E o frete?',
    );
    await userEvent.keyboard('{Control>}{Enter}{/Control}');

    expect(await screen.findByText(/O frete não entra/)).toBeInTheDocument();
    expect(body).toMatchObject({ question: 'E o frete?', conversationId });
    expect(screen.getAllByRole('button', { name: text.showEvidence })[1]).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  test('keeps the draft and explains an error before the question is recorded', async () => {
    server.use(
      ...handlers(),
      http.post(`${projectUrl}/questions`, () =>
        problem(409, 'SNAPSHOT_NOT_SEARCHABLE', { title: 'Snapshot indisponível' }),
      ),
    );
    renderRoute(`/projects/${base.id}/ask`);

    await userEvent.click(await screen.findByRole('button', { name: text.form.submit }));
    expect(screen.getByText(text.form.empty)).toBeInTheDocument();
    expect(questionBox()).toHaveAttribute('aria-invalid', 'true');

    await userEvent.type(questionBox(), 'Onde fica o main?');
    await userEvent.click(screen.getByRole('button', { name: text.form.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Snapshot indisponível');
    expect(screen.getByText(messages.recovery.reindex)).toBeInTheDocument();
    expect(questionBox()).toHaveValue('Onde fica o main?');
  });

  test('asks for acknowledgement once per project before sending to a remote provider', async () => {
    server.use(
      ...handlers({ remote: true }),
      http.post(`${projectUrl}/questions`, () => HttpResponse.json(answer(), { status: 201 })),
    );
    const { unmount } = renderRoute(`/projects/${base.id}/ask`);

    expect(
      await screen.findByText(text.privacy.remote('OpenAI', 'gpt-6-luna')),
    ).toBeInTheDocument();
    expect(screen.getByText(text.privacy.remoteEmbeddings('OpenAI'))).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.privacy.review })).toHaveAttribute(
      'href',
      `/workspaces/${base.workspaceId}/new?project=${base.id}`,
    );
    await userEvent.type(questionBox(), 'Onde fica o main?');
    expect(screen.getByRole('button', { name: text.form.submit })).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: text.privacy.acknowledge }));
    await userEvent.click(screen.getByRole('button', { name: text.form.submit }));

    expect(await screen.findByText(/O reembolso é o total/)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(text.privacy.reminder('OpenAI')))).toBeInTheDocument();
    unmount();

    renderRoute(`/projects/${base.id}/ask`);
    expect(
      await screen.findByText(new RegExp(text.privacy.reminder('OpenAI'))),
    ).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  test('offers only the lexical search when the snapshot has no usable embeddings', async () => {
    let body: unknown;
    server.use(
      ...handlers({ current: { ...base, outdatedReasons: ['EMBEDDINGS_MISSING'] } }),
      http.post(`${projectUrl}/questions`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(answer(), { status: 201 });
      }),
    );
    renderRoute(`/projects/${base.id}/ask`);

    expect(await screen.findByText(new RegExp(text.outdated))).toBeInTheDocument();
    await userEvent.click(screen.getByText(text.form.advanced));
    expect(screen.getByRole('radio', { name: text.form.modes.HYBRID })).toBeDisabled();
    expect(screen.getByRole('radio', { name: text.form.modes.VECTOR })).toBeDisabled();
    expect(screen.getByRole('radio', { name: text.form.modes.LEXICAL })).toBeChecked();
    expect(screen.getByText(text.form.vectorUnavailable)).toBeInTheDocument();
    const max = screen.getByRole('spinbutton', { name: text.form.maxEvidenceLabel });
    await userEvent.clear(max);
    await userEvent.type(max, '3');
    await userEvent.type(questionBox(), 'Onde fica o main?');
    await userEvent.click(screen.getByRole('button', { name: text.form.submit }));

    await waitFor(() =>
      expect(body).toMatchObject({ options: { retrievalMode: 'LEXICAL', maxEvidence: 3 } }),
    );
  });

  test('explains why asking is unavailable', async () => {
    server.use(...handlers({ current: project() }));
    const { unmount } = renderRoute(`/projects/${base.id}/ask`);

    expect(await screen.findByText(text.noSnapshot)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    unmount();

    server.use(
      http.get('/api/v1/system/providers', () =>
        HttpResponse.json({
          ...providers(),
          generation: { provider: 'none', model: null, remote: false },
        }),
      ),
    );
    renderRoute(`/projects/${base.id}/ask`);
    expect(await screen.findByText(text.noGeneration)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  test('a missing conversation shows the error without a retry', async () => {
    server.use(...handlers());
    server.use(http.get(conversationUrl, () => problem(404, 'CONVERSATION_NOT_FOUND')));
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    expect(await screen.findByRole('alert')).toHaveTextContent('Título do erro');
    expect(screen.queryByRole('button', { name: messages.errors.retry })).not.toBeInTheDocument();
    const trail = screen.getByRole('navigation', { name: messages.breadcrumbs.label });
    expect(within(trail).getByRole('link', { name: base.name })).toHaveAttribute(
      'href',
      `/projects/${base.id}`,
    );
  });

  test('on narrow screens the areas become tabs and a citation opens the code tab', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: false, addEventListener: () => {}, removeEventListener: () => {} })),
    );
    server.use(
      ...handlers(),
      http.get(`${projectUrl}/evidence/${evidence().id}`, () =>
        HttpResponse.json(evidenceDetail()),
      ),
      http.get(`/api/v1/snapshots/${answeredSnapshot}/files/content`, () =>
        HttpResponse.json(fileContent()),
      ),
    );
    renderRoute(`/projects/${base.id}/conversations/${conversationId}`);

    const tabs = await screen.findByRole('tablist', { name: text.tabs.label });
    expect(within(tabs).getByRole('tab', { name: text.tabs.conversation })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await userEvent.click(screen.getByRole('button', { name: citationName }));

    expect(within(tabs).getByRole('tab', { name: text.tabs.code })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(
      await screen.findByRole('region', { name: text.viewer.codeLabel('src/billing/Refund.java') }),
    ).toBeInTheDocument();
    expect(await axeViolations()).toEqual([]);
  });
});
