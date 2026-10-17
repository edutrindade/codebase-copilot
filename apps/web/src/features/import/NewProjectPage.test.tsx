import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import {
  importJob,
  indexJob,
  inventory,
  page,
  project,
  providers,
  settings,
  workspace,
} from '../../test/fixtures';
import { problem } from '../../test/problems';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';
import type { Inventory, Settings } from './api';

const ws = workspace();
const newProjectPath = `/workspaces/${ws.id}/new`;
const created = project();
const reviewPath = `${newProjectPath}?project=${created.id}`;
const projectUrl = `/api/v1/projects/${created.id}`;
const zipProject = project({ source: { type: 'ZIP', currentImport: null } });
const roots = [{ key: 'default', label: 'Projetos' }];

function directory(name: string, relativePath: string, overrides = {}) {
  return { name, relativePath, hasChildren: true, readable: true, ...overrides };
}

function reviewHandlers(
  options: { inventory?: Inventory; settings?: Settings; remote?: boolean } = {},
) {
  return [
    http.get(projectUrl, () => HttpResponse.json(created)),
    http.get(`${projectUrl}/inventory`, () => HttpResponse.json(options.inventory ?? inventory())),
    http.get(`${projectUrl}/settings`, () => HttpResponse.json(options.settings ?? settings())),
    http.get('/api/v1/system/providers', () => HttpResponse.json(providers(options.remote))),
  ];
}

afterEach(() => window.localStorage.clear());

describe('NewProjectPage: source', () => {
  test('browses the allowed roots and creates the project from the selected folder', async () => {
    let body: unknown;
    server.use(
      http.get('/api/v1/local-roots', () => HttpResponse.json(roots)),
      http.get('/api/v1/local-roots/default/directories', ({ request }) => {
        const path = new URL(request.url).searchParams.get('path');
        return HttpResponse.json(
          path === ''
            ? {
                path: '',
                directories: [
                  directory('clientes', 'clientes'),
                  directory('privado', 'privado', { readable: false }),
                ],
                truncated: true,
              }
            : { path: 'clientes', directories: [], truncated: false },
        );
      }),
      http.post(`/api/v1/workspaces/${ws.id}/projects`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(created, { status: 201 });
      }),
      ...reviewHandlers(),
    );
    const { router } = renderRoute(newProjectPath);

    expect(await screen.findByText(messages.newProject.browser.truncated)).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: new RegExp(messages.newProject.sourceLocal) }),
    ).toBeChecked();
    expect(screen.getByRole('button', { name: /privado/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /privado/ })).toHaveTextContent(
      messages.newProject.browser.unreadable,
    );
    expect(await axeViolations()).toEqual([]);

    await userEvent.click(
      screen.getByRole('button', { name: messages.newProject.browser.open('clientes') }),
    );
    expect(await screen.findByText(messages.newProject.browser.empty)).toBeInTheDocument();
    expect(
      screen.getByText(messages.newProject.browser.selected('Projetos/clientes')),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(messages.newProject.nameLabel)).toHaveValue('clientes');

    await userEvent.click(screen.getByRole('button', { name: messages.newProject.create }));

    await waitFor(() => expect(router.state.location.search).toBe(`?project=${created.id}`));
    expect(body).toEqual({
      sourceType: 'LOCAL_FOLDER',
      name: 'clientes',
      rootKey: 'default',
      relativePath: 'clientes',
    });
    expect(
      await screen.findByRole('heading', { level: 2, name: messages.preAnalysis.title }),
    ).toBeInTheDocument();
  });

  test('without local roots only the ZIP is offered', async () => {
    let body: unknown;
    server.use(
      http.get('/api/v1/local-roots', () => HttpResponse.json([])),
      http.post(`/api/v1/workspaces/${ws.id}/projects`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(zipProject, { status: 201 });
      }),
      http.get(projectUrl, () => HttpResponse.json(zipProject)),
      http.get(`${projectUrl}/imports`, () => HttpResponse.json(page([]))),
    );
    renderRoute(newProjectPath);

    expect(await screen.findByText(messages.newProject.noRoots)).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: new RegExp(messages.newProject.sourceLocal) }),
    ).toBeDisabled();
    expect(
      screen.getByRole('radio', { name: new RegExp(messages.newProject.sourceZip) }),
    ).toBeChecked();

    await userEvent.type(screen.getByLabelText(messages.newProject.nameLabel), 'projeto-zip');
    await userEvent.click(screen.getByRole('button', { name: messages.newProject.create }));

    expect(
      await screen.findByRole('heading', { level: 2, name: messages.newProject.upload.title }),
    ).toBeInTheDocument();
    expect(body).toEqual({ sourceType: 'ZIP', name: 'projeto-zip' });
  });

  test('explains a duplicated folder', async () => {
    server.use(
      http.get('/api/v1/local-roots', () => HttpResponse.json(roots)),
      http.get('/api/v1/local-roots/default/directories', () =>
        HttpResponse.json({ path: '', directories: [], truncated: false }),
      ),
      http.post(`/api/v1/workspaces/${ws.id}/projects`, () =>
        problem(409, 'PROJECT_ALREADY_EXISTS'),
      ),
    );
    renderRoute(newProjectPath);

    await userEvent.click(await screen.findByRole('button', { name: messages.newProject.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.recovery.chooseAnother);
  });

  test('a folder that disappeared offers to go back to the root', async () => {
    server.use(
      http.get('/api/v1/local-roots', () => HttpResponse.json(roots)),
      http.get('/api/v1/local-roots/default/directories', ({ request }) =>
        new URL(request.url).searchParams.get('path') === ''
          ? HttpResponse.json({
              path: '',
              directories: [directory('api', 'api')],
              truncated: false,
            })
          : problem(404, 'DIRECTORY_NOT_FOUND'),
      ),
    );
    renderRoute(newProjectPath);

    await userEvent.click(
      await screen.findByRole('button', { name: messages.newProject.browser.open('api') }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.recovery.chooseAnother);

    await userEvent.click(
      screen.getByRole('button', { name: messages.newProject.browser.backToRoot }),
    );
    expect(
      await screen.findByRole('button', { name: messages.newProject.browser.open('api') }),
    ).toBeInTheDocument();
  });
});

describe('NewProjectPage: ZIP upload', () => {
  test('uploads the file, follows the extraction and moves on to the pre-analysis', async () => {
    let current = zipProject;
    let job = importJob();
    let uploaded: string | null = null;
    server.use(
      http.get(projectUrl, () => HttpResponse.json(current)),
      http.get(`${projectUrl}/imports`, () => HttpResponse.json(page([job]))),
      http.post(`${projectUrl}/imports/zip`, async ({ request }) => {
        // jsdom's File reaches Node's FormData as a plain Blob, so the content is checked, not the name.
        const file = (await request.formData()).get('file');
        uploaded = typeof file === 'string' || file === null ? null : await file.text();
        job = importJob({ status: 'EXTRACTING' });
        return HttpResponse.json(importJob(), { status: 202 });
      }),
      http.get(`${projectUrl}/inventory`, () => HttpResponse.json(inventory())),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
      http.get('/api/v1/system/providers', () => HttpResponse.json(providers())),
    );
    renderRoute(reviewPath);

    expect(await screen.findByText(messages.newProject.upload.pending)).toBeInTheDocument();
    const input = screen.getByLabelText(messages.newProject.upload.fileLabel);
    expect(input).toBeDisabled();

    job = importJob({
      status: 'REJECTED',
      error: { code: 'ZIP_UNSAFE_PATH', message: 'O ZIP contém caminhos inseguros.' },
    });
    const alert = await screen.findByRole('alert', {}, { timeout: 3000 });
    expect(alert).toHaveTextContent(messages.newProject.upload.rejectedTitle);
    expect(alert).toHaveTextContent('O ZIP contém caminhos inseguros.');
    expect(await axeViolations()).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: messages.newProject.upload.submit }));
    expect(screen.getByText(messages.newProject.upload.chooseFile)).toBeInTheDocument();

    await userEvent.upload(input, new File(['PK'], 'projeto.zip', { type: 'application/zip' }));
    await userEvent.click(screen.getByRole('button', { name: messages.newProject.upload.submit }));
    expect(
      await screen.findByText(messages.newProject.upload.extracting, {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(uploaded).toBe('PK');

    current = project({
      source: { type: 'ZIP', currentImport: { id: job.id, status: 'COMPLETED' } },
    });
    job = importJob({ status: 'COMPLETED' });
    expect(
      await screen.findByRole(
        'heading',
        { level: 2, name: messages.preAnalysis.title },
        { timeout: 3000 },
      ),
    ).toBeInTheDocument();
  });

  test('explains a file above the upload limit', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(zipProject)),
      http.get(`${projectUrl}/imports`, () => HttpResponse.json(page([]))),
      http.post(`${projectUrl}/imports/zip`, () => problem(413, 'UPLOAD_TOO_LARGE')),
    );
    renderRoute(reviewPath);

    await userEvent.upload(
      await screen.findByLabelText(messages.newProject.upload.fileLabel),
      new File(['PK'], 'grande.zip', { type: 'application/zip' }),
    );
    await userEvent.click(screen.getByRole('button', { name: messages.newProject.upload.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.recovery.chooseAnother);
  });
});

describe('NewProjectPage: pre-analysis', () => {
  test('shows totals, languages, directories, exclusions with examples and warnings', async () => {
    server.use(
      ...reviewHandlers({
        inventory: inventory({
          warnings: [
            {
              code: 'CASE_VARIANT_PROJECT',
              message: 'Outro projeto aponta para a mesma pasta.',
              relatedProjectIds: ['0192f5c4-0000-7000-8000-000000000199'],
            },
          ],
        }),
      }),
    );
    renderRoute(reviewPath);

    const totals = await screen.findByRole('region', { name: messages.preAnalysis.totalsTitle });
    expect(totals).toHaveTextContent(`${messages.preAnalysis.totals.includedFiles}980`);
    expect(totals).toHaveTextContent(`${messages.preAnalysis.totals.includedBytes}7 MB`);
    expect(screen.getByRole('rowheader', { name: 'java' })).toBeInTheDocument();
    expect(
      screen.getByRole('rowheader', { name: messages.preAnalysis.rootDirectory }),
    ).toBeInTheDocument();
    const exclusion = screen.getByText(/Regra padrão “node-modules”: 1 pasta/);
    await userEvent.click(exclusion);
    expect(screen.getByText('web/node_modules')).toBeVisible();
    expect(screen.getByText('Outro projeto aponta para a mesma pasta.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.preAnalysis.relatedProject })).toHaveAttribute(
      'href',
      '/projects/0192f5c4-0000-7000-8000-000000000199',
    );
    expect(screen.getByRole('button', { name: messages.indexing.start })).toBeEnabled();
    expect(await axeViolations()).toEqual([]);
  });

  test('blocks indexing when no file would be indexed', async () => {
    server.use(
      ...reviewHandlers({
        inventory: inventory({
          warnings: [
            {
              code: 'NO_INCLUDED_FILES',
              message: 'Nenhum arquivo será indexado.',
              relatedProjectIds: [],
            },
          ],
        }),
      }),
    );
    renderRoute(reviewPath);

    expect(await screen.findByText(messages.preAnalysis.blocking)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.indexing.start })).toBeDisabled();
  });

  test('a missing source folder explains how to recover', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(created)),
      http.get(`${projectUrl}/inventory`, () => problem(409, 'SOURCE_DIRECTORY_NOT_FOUND')),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
      http.get('/api/v1/system/providers', () => HttpResponse.json(providers())),
    );
    renderRoute(reviewPath);

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.recovery.checkSource);
    expect(screen.getByRole('button', { name: messages.indexing.start })).toBeDisabled();
  });

  test('saves exclusions, shows field errors next to the pattern, and recalculates', async () => {
    let saved = settings();
    let bodies: unknown[] = [];
    let inventoryCalls = 0;
    server.use(
      http.get(projectUrl, () => HttpResponse.json(created)),
      http.get(`${projectUrl}/inventory`, () => {
        inventoryCalls++;
        return HttpResponse.json(inventory({ settingsVersion: saved.version }));
      }),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(saved)),
      http.get('/api/v1/system/providers', () => HttpResponse.json(providers())),
      http.put(`${projectUrl}/settings`, async ({ request }) => {
        const body = (await request.json()) as {
          excludePatterns: string[];
          disabledDefaultRules: string[];
        };
        bodies = [...bodies, body];
        if (body.excludePatterns.includes('[bad]')) {
          return problem(400, 'VALIDATION_FAILED', {
            errors: [{ field: 'excludePatterns[1]', message: 'Padrão com caractere inválido.' }],
          });
        }
        saved = settings({
          excludePatterns: body.excludePatterns,
          disabledDefaultRules: body.disabledDefaultRules,
          version: 1,
        });
        return HttpResponse.json(saved);
      }),
    );
    renderRoute(reviewPath);

    const patterns = await screen.findByLabelText(messages.settings.patternsLabel);
    expect(
      screen.getByRole('checkbox', { name: messages.settings.ruleEnabled('env-files') }),
    ).toBeDisabled();
    await userEvent.click(
      screen.getByRole('checkbox', { name: messages.settings.ruleEnabled('vendor') }),
    );
    await userEvent.type(patterns, 'docs/generated/{Enter}[[bad]');

    expect(screen.getByText(messages.indexing.dirty)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.indexing.start })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: messages.settings.save }));
    expect(
      await screen.findByText(
        messages.settings.patternError('[bad]', 'Padrão com caractere inválido.'),
      ),
    ).toBeInTheDocument();
    expect(patterns).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent(messages.recovery.fixInput);

    await userEvent.type(patterns, '{Backspace>6}');
    await userEvent.click(screen.getByRole('button', { name: messages.settings.save }));

    expect(await screen.findByText(messages.settings.saved)).toBeInTheDocument();
    expect(bodies.at(-1)).toEqual({
      excludePatterns: ['docs/generated/'],
      disabledDefaultRules: ['vendor'],
      maxFileBytes: null,
      maxProjectBytes: null,
    });
    await waitFor(() => expect(inventoryCalls).toBeGreaterThan(1));
    expect(await screen.findByLabelText(messages.settings.patternsLabel)).toHaveValue(
      'docs/generated/',
    );
    expect(screen.getByRole('button', { name: messages.indexing.start })).toBeEnabled();
  });

  test('a remote provider must be acknowledged before indexing', async () => {
    let indexed = false;
    server.use(
      ...reviewHandlers({ remote: true }),
      http.post(`${projectUrl}/index-jobs`, () => {
        indexed = true;
        return HttpResponse.json(indexJob(created.id), { status: 202 });
      }),
    );
    const { router } = renderRoute(reviewPath);

    expect(
      await screen.findByText(messages.privacy.remote('OpenAI', 'text-embedding-3-small')),
    ).toBeInTheDocument();
    await screen.findByRole('region', { name: messages.preAnalysis.totalsTitle });
    const start = screen.getByRole('button', { name: messages.indexing.start });
    expect(start).toBeDisabled();
    expect(screen.getByRole('link', { name: messages.privacy.review })).toHaveAttribute(
      'href',
      '#exclusions',
    );

    await userEvent.click(screen.getByRole('checkbox', { name: messages.privacy.acknowledge }));
    await userEvent.click(start);

    await waitFor(() => expect(router.state.location.pathname).toBe(`/projects/${created.id}`));
    expect(indexed).toBe(true);
    expect(window.localStorage.getItem(`privacy-ack:${created.id}`)).toBe('true');
  });

  test('a local provider indexes without the acknowledgement', async () => {
    server.use(
      ...reviewHandlers(),
      http.post(`${projectUrl}/index-jobs`, () => problem(409, 'PROJECT_BUSY')),
    );
    renderRoute(reviewPath);

    expect(await screen.findByText(messages.privacy.localOnly)).toBeInTheDocument();
    const start = screen.getByRole('button', { name: messages.indexing.start });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(messages.recovery.waitForJob);
    expect(
      within(alert).queryByRole('button', { name: messages.errors.retry }),
    ).not.toBeInTheDocument();
  });
});
