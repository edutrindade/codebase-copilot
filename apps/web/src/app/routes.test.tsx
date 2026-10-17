import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, test } from 'vitest';
import { messages } from '../messages';
import { axeViolations } from '../test/axe';
import { page } from '../test/fixtures';
import { renderRoute } from '../test/render';
import { server } from '../test/server';

describe('routes', () => {
  beforeEach(() => {
    server.use(http.get('/api/v1/workspaces', () => HttpResponse.json(page([]))));
  });

  test.each([['/workspaces/w1/new', messages.pages.newProject]])(
    '%s renders its screen',
    async (path, title) => {
      renderRoute(path);

      expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
    },
  );

  test('an unknown path renders the not found screen with a way back', async () => {
    const { router } = renderRoute('/nope');

    expect(
      await screen.findByRole('heading', { level: 1, name: messages.notFound.title }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: messages.notFound.back }));
    expect(router.state.location.pathname).toBe('/');
  });

  test('the layout has a skip link to the main content and no axe violations', async () => {
    renderRoute('/');
    await screen.findByRole('heading', { level: 1 });

    const skip = screen.getByRole('link', { name: messages.app.skipToContent });
    expect(skip).toHaveAttribute('href', '#content');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'content');
    expect(await axeViolations()).toEqual([]);
  });
});
