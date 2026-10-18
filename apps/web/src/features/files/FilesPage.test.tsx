import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterAll, beforeAll, describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { page, project, readySnapshot } from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';
import type { TreeEntry } from './api';

const text = messages.files;
const base = project({ activeSnapshot: readySnapshot() });
const snapshotId = readySnapshot().id;
const projectUrl = `/api/v1/projects/${base.id}`;
const filesUrl = `/projects/${base.id}/files`;
const SOURCE = 'class App {\n  int x;\n  int y;\n}\n';

function directory(path: string, counts: Partial<TreeEntry> = {}): TreeEntry {
  return {
    name: path.slice(path.lastIndexOf('/') + 1),
    path,
    type: 'DIRECTORY',
    files: 2,
    indexedFiles: 2,
    unsupportedFiles: 0,
    failedFiles: 0,
    sizeBytes: 200,
    status: null,
    reason: null,
    language: null,
    ...counts,
  };
}

function file(path: string, status: TreeEntry['status'] = 'INDEXED', reason: string | null = null) {
  return {
    ...directory(path),
    type: 'FILE',
    files: 1,
    indexedFiles: status === 'INDEXED' ? 1 : 0,
    unsupportedFiles: status === 'UNSUPPORTED' ? 1 : 0,
    status,
    reason,
    language: 'java',
  } satisfies TreeEntry;
}

const TREE: Record<string, TreeEntry[]> = {
  '': [directory('src', { files: 3, unsupportedFiles: 1 }), file('README.md')],
  src: [directory('src/main'), file('src/secret.sh', 'UNSUPPORTED', 'SENSITIVE_CONTENT')],
  'src/main': [file('src/main/App.java'), file('src/main/Util.java')],
};

interface Requests {
  tree: string[];
}

function handlers(requests: Requests = { tree: [] }) {
  return [
    http.get(projectUrl, () => HttpResponse.json(base)),
    http.get(`/api/v1/snapshots/${snapshotId}/tree`, ({ request }) => {
      const query = new URL(request.url).searchParams;
      const path = query.get('path') ?? '';
      const status = query.get('status');
      requests.tree.push(`${path}|${status ?? ''}`);
      const entries = (TREE[path] ?? []).filter(
        (entry) =>
          !status ||
          (entry.type === 'FILE'
            ? entry.status === status
            : status === 'UNSUPPORTED'
              ? entry.unsupportedFiles > 0
              : true),
      );
      return HttpResponse.json({ path, entries });
    }),
    http.get(`/api/v1/snapshots/${snapshotId}/files`, ({ request }) => {
      const prefix = new URL(request.url).searchParams.get('pathPrefix') ?? '';
      const entry = Object.values(TREE)
        .flat()
        .find((item) => item.path === prefix && item.type === 'FILE');
      return HttpResponse.json(
        page(
          entry
            ? [
                {
                  relativePath: entry.path,
                  status: entry.status ?? 'INDEXED',
                  reason: entry.reason,
                  language: 'java',
                  sizeBytes: 34,
                  lineCount: entry.status === 'INDEXED' ? 4 : null,
                  contentHash: null,
                  chunkCount: 1,
                },
              ]
            : [],
        ),
      );
    }),
    http.get(`/api/v1/snapshots/${snapshotId}/files/content`, ({ request }) =>
      HttpResponse.json({
        snapshotId,
        path: new URL(request.url).searchParams.get('path') ?? '',
        language: 'java',
        contentHash: 'abc',
        sizeBytes: 34,
        lineCount: 4,
        content: SOURCE,
      }),
    ),
  ];
}

// jsdom has no layout: the code view gets a height so the virtualizer renders rows.
beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
    configurable: true,
    get(this: HTMLElement) {
      return this.classList.contains('code-view') ? 400 : 0;
    },
  });
});

afterAll(() => {
  Reflect.deleteProperty(HTMLElement.prototype, 'clientHeight');
});

describe('FilesPage', () => {
  test('browses the tree folder by folder and opens a file', async () => {
    server.use(...handlers());
    const { router } = renderRoute(filesUrl);

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.files }),
    ).toBeInTheDocument();
    const tree = await screen.findByRole('list', { name: text.treeLabel });
    const src = await within(tree).findByRole('button', { name: /^src/ });
    expect(src).toHaveAttribute('aria-expanded', 'false');
    expect(src).toHaveTextContent(text.unindexedCount(1));
    await userEvent.click(src);
    await userEvent.click(await within(tree).findByRole('button', { name: /^main/ }));
    await userEvent.click(await within(tree).findByRole('link', { name: 'App.java' }));

    expect(router.state.location.search).toBe('?file=src%2Fmain%2FApp.java');
    const viewer = screen.getByRole('region', { name: text.viewerTitle });
    const code = await within(viewer).findByRole('region', {
      name: messages.ask.viewer.codeLabel('src/main/App.java'),
    });
    expect(code).toHaveTextContent('int x;');
    expect(within(tree).getByRole('link', { name: 'App.java' })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(within(viewer).getByRole('link', { name: text.askAbout })).toHaveAttribute(
      'href',
      `/projects/${base.id}/ask`,
    );
    expect(document.title).toBe(
      `App.java · ${messages.pages.files} · ${base.name} · Codebase Copilot`,
    );
    expect(await axeViolations()).toEqual([]);
  });

  test('a link to a file opens its folders and highlights the lines', async () => {
    server.use(...handlers());
    renderRoute(`${filesUrl}?file=src/main/App.java&lines=2-3`);

    const tree = await screen.findByRole('list', { name: text.treeLabel });
    expect(await within(tree).findByRole('link', { name: 'App.java' })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(within(tree).getByRole('button', { name: /^src/ })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    const code = await screen.findByRole('region', {
      name: messages.ask.viewer.codeLabel('src/main/App.java'),
    });
    expect(code.querySelectorAll('.code-view__line--cited')).toHaveLength(2);
    expect(screen.getByText(new RegExp(text.lines(2, 3)))).toBeInTheDocument();
  });

  test('filters by state and explains why a file was not indexed', async () => {
    const requests: Requests = { tree: [] };
    server.use(...handlers(requests));
    const { router } = renderRoute(filesUrl);

    await userEvent.click(await screen.findByRole('radio', { name: text.filters.UNSUPPORTED(1) }));
    expect(router.state.location.search).toBe('?status=UNSUPPORTED');
    const tree = screen.getByRole('list', { name: text.treeLabel });
    await userEvent.click(await within(tree).findByRole('button', { name: /^src/ }));
    const secret = await within(tree).findByRole('link', { name: /secret\.sh/ });
    expect(secret).toHaveTextContent(text.status.UNSUPPORTED);
    expect(within(tree).queryByRole('link', { name: 'README.md' })).not.toBeInTheDocument();
    await userEvent.click(secret);

    expect(
      await screen.findByText(
        text.notIndexed(messages.project.unindexed.reasons.SENSITIVE_CONTENT),
      ),
    ).toBeInTheDocument();
    expect(requests.tree).toEqual(expect.arrayContaining(['|UNSUPPORTED', 'src|UNSUPPORTED']));
  });

  test('the arrows move between rows and open or close folders', async () => {
    const user = userEvent.setup();
    server.use(...handlers());
    renderRoute(filesUrl);

    const tree = await screen.findByRole('list', { name: text.treeLabel });
    const src = await within(tree).findByRole('button', { name: /^src/ });
    src.focus();
    await user.keyboard('{ArrowRight}');
    const main = await within(tree).findByRole('button', { name: /^main/ });
    expect(src).toHaveAttribute('aria-expanded', 'true');
    await user.keyboard('{ArrowDown}');
    expect(main).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(src).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(src).toHaveAttribute('aria-expanded', 'false');
    await user.keyboard('{End}');
    expect(within(tree).getByRole('link', { name: 'README.md' })).toHaveFocus();
  });

  test('a folder that fails to load offers a retry', async () => {
    let failing = true;
    server.use(
      http.get(`/api/v1/snapshots/${snapshotId}/tree`, () =>
        failing
          ? problem(500, 'INTERNAL_ERROR', { title: 'Erro interno' })
          : HttpResponse.json({ path: '', entries: TREE[''] }),
      ),
      ...handlers(),
    );
    renderRoute(filesUrl);

    expect(await screen.findByText(text.loadFailed)).toBeInTheDocument();
    failing = false;
    await userEvent.click(screen.getByRole('button', { name: text.retry }));
    expect(await screen.findByRole('link', { name: 'README.md' })).toBeInTheDocument();
  });

  test('without a snapshot, points to the overview', async () => {
    server.use(http.get(projectUrl, () => HttpResponse.json(project())));
    renderRoute(filesUrl);

    expect(await screen.findByRole('heading', { name: text.noSnapshotTitle })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: text.openOverview })).toHaveAttribute(
      'href',
      `/projects/${base.id}`,
    );
  });
});
