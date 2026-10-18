import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { project, readySnapshot, settings } from '../../test/fixtures';
import { renderRoute } from '../../test/render';
import { server } from '../../test/server';

const base = project({ activeSnapshot: readySnapshot() });
const projectUrl = `/api/v1/projects/${base.id}`;

describe('ProjectSettingsPage', () => {
  test('edits the exclusions outside the wizard and warns the snapshot becomes outdated', async () => {
    let saved: unknown;
    let projectReads = 0;
    server.use(
      http.get(projectUrl, () => {
        projectReads++;
        return HttpResponse.json(base);
      }),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
      http.put(`${projectUrl}/settings`, async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json(settings({ excludePatterns: ['docs/'], version: 1 }));
      }),
    );
    renderRoute(`/projects/${base.id}/settings`);

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.pages.settings }),
    ).toBeInTheDocument();
    expect(screen.getByText(messages.projectSettings.outdatedWarning)).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: messages.projectSettings.overviewLink }),
    ).toHaveAttribute('href', `/projects/${base.id}`);
    await userEvent.type(await screen.findByLabelText(messages.settings.patternsLabel), 'docs/');
    expect(await axeViolations()).toEqual([]);
    const readsBeforeSave = projectReads;
    await userEvent.click(screen.getByRole('button', { name: messages.settings.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(messages.projectSettings.saved);
    expect(saved).toMatchObject({ excludePatterns: ['docs/'] });
    expect(projectReads).toBeGreaterThan(readsBeforeSave);
    expect(document.title).toBe(`${messages.pages.settings} · ${base.name} · Codebase Copilot`);
  });

  test('without a snapshot there is nothing to become outdated', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(project())),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
    );
    renderRoute(`/projects/${base.id}/settings`);

    expect(await screen.findByLabelText(messages.settings.patternsLabel)).toBeInTheDocument();
    expect(screen.queryByText(messages.projectSettings.outdatedWarning)).not.toBeInTheDocument();
  });
});
