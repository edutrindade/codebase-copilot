import { screen, within } from '@testing-library/react';
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
    const patterns = await screen.findByLabelText(messages.settings.patternsLabel);
    expect(
      screen.queryByRole('region', { name: messages.settings.barLabel }),
    ).not.toBeInTheDocument();
    await userEvent.type(patterns, 'docs/');
    const bar = screen.getByRole('region', { name: messages.settings.barLabel });
    expect(bar).toHaveTextContent(messages.projectSettings.outdatedWarning);
    expect(await axeViolations()).toEqual([]);
    const readsBeforeSave = projectReads;
    await userEvent.click(within(bar).getByRole('button', { name: messages.settings.saveChanges }));

    expect(await screen.findByText(messages.toast.settingsSaved)).toBeInTheDocument();
    expect(saved).toMatchObject({ excludePatterns: ['docs/'] });
    expect(projectReads).toBeGreaterThan(readsBeforeSave);
    expect(
      screen.queryByRole('region', { name: messages.settings.barLabel }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.settings.title })).toHaveFocus();
    expect(document.title).toBe(`${messages.pages.settings} · ${base.name} · Codebase Copilot`);
  });

  test('discarding hides the bar; an outdated snapshot links to the overview', async () => {
    const outdated = { ...base, outdatedReasons: ['SETTINGS_CHANGED' as const] };
    server.use(
      http.get(projectUrl, () => HttpResponse.json(outdated)),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
    );
    renderRoute(`/projects/${base.id}/settings`);

    expect(await screen.findByText(messages.projectSettings.outdated)).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: messages.projectSettings.overviewLink }),
    ).toHaveAttribute('href', `/projects/${base.id}`);
    const patterns = await screen.findByLabelText(messages.settings.patternsLabel);
    await userEvent.type(patterns, 'docs/');
    const bar = screen.getByRole('region', { name: messages.settings.barLabel });
    await userEvent.click(within(bar).getByRole('button', { name: messages.settings.discard }));

    expect(patterns).toHaveValue('');
    expect(
      screen.queryByRole('region', { name: messages.settings.barLabel }),
    ).not.toBeInTheDocument();
  });

  test('groups the default rules by category; a group with a rule turned off starts open', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(base)),
      http.get(`${projectUrl}/settings`, () =>
        HttpResponse.json(settings({ disabledDefaultRules: ['vendor'] })),
      ),
    );
    renderRoute(`/projects/${base.id}/settings`);

    const text = messages.settings;
    const dependencies = (await screen.findByText(text.categories.DEPENDENCIES)).closest('details');
    expect(dependencies).toHaveAttribute('open');
    expect(dependencies).toHaveTextContent(text.applied(0, 1));
    expect(screen.getByText(text.categories.SENSITIVE).closest('details')).not.toHaveAttribute(
      'open',
    );
    await userEvent.click(screen.getByRole('checkbox', { name: text.ruleEnabled('vendor') }));

    expect(dependencies).toHaveAttribute('open');
    expect(dependencies).toHaveTextContent(text.applied(1, 1));
    expect(screen.getByRole('region', { name: text.barLabel })).toBeInTheDocument();
  });

  test('without a snapshot there is nothing to become outdated', async () => {
    server.use(
      http.get(projectUrl, () => HttpResponse.json(project())),
      http.get(`${projectUrl}/settings`, () => HttpResponse.json(settings())),
    );
    renderRoute(`/projects/${base.id}/settings`);

    await userEvent.type(await screen.findByLabelText(messages.settings.patternsLabel), 'docs/');
    expect(screen.getByRole('region', { name: messages.settings.barLabel })).toBeInTheDocument();
    expect(screen.queryByText(messages.projectSettings.outdatedWarning)).not.toBeInTheDocument();
  });
});
