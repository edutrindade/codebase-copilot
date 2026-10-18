import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InMain } from '../test/render';
import { afterEach, expect, test } from 'vitest';
import { setThemePreference } from '../app/theme';
import { messages } from '../messages';
import { axeViolations } from '../test/axe';
import { ThemeMenu } from './ThemeMenu';

afterEach(() => setThemePreference('system'));

test('choosing a theme applies it and names the trigger after it', async () => {
  render(<ThemeMenu />, { wrapper: InMain });
  const text = messages.theme;

  await userEvent.click(screen.getByRole('button', { name: text.label(text.options.system) }));
  expect(screen.getByRole('menuitemradio', { name: text.options.system })).toBeChecked();
  await userEvent.click(screen.getByRole('menuitemradio', { name: text.options.dark }));

  expect(document.documentElement.dataset.theme).toBe('dark');
  expect(screen.getByRole('button', { name: text.label(text.options.dark) })).toBeInTheDocument();
  // Focus returns to the trigger and opens its tooltip, which lives in a portal outside <main>.
  await userEvent.keyboard('{Escape}');
  expect(await axeViolations()).toEqual([]);
});
