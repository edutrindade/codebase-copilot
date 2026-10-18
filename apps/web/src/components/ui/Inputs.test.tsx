import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InMain } from '../../test/render';
import { afterEach, expect, test, vi } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { CopyButton } from './CopyButton';
import { TextField } from './Field';

afterEach(() => {
  vi.unstubAllGlobals();
});

test('a field announces its hint and error with the control', async () => {
  render(<TextField label="Nome" hint="Até 100 caracteres." error="O nome é obrigatório." />, {
    wrapper: InMain,
  });

  const input = screen.getByRole('textbox', { name: 'Nome' });
  expect(input).toHaveAttribute('aria-invalid', 'true');
  expect(input).toHaveAccessibleDescription('O nome é obrigatório. Até 100 caracteres.');
  expect(await axeViolations()).toEqual([]);
});

test('a field without hint or error has no description', () => {
  render(<TextField label="Nome" />, { wrapper: InMain });

  const input = screen.getByRole('textbox', { name: 'Nome' });
  expect(input).not.toHaveAttribute('aria-describedby');
  expect(input).not.toHaveAttribute('aria-invalid');
});

test('copying announces the result', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
  render(<CopyButton text="0192f5c4" label="Copiar o identificador" />, { wrapper: InMain });

  await userEvent.click(screen.getByRole('button', { name: 'Copiar o identificador' }));

  expect(writeText).toHaveBeenCalledWith('0192f5c4');
  await waitFor(() => expect(screen.getByText(messages.copy.copied)).toBeInTheDocument());
});

test('a copy the browser refuses is announced too', async () => {
  vi.stubGlobal('navigator', {
    ...navigator,
    clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
  });
  render(<CopyButton text="0192f5c4" label="Copiar o identificador" />, { wrapper: InMain });

  await userEvent.click(screen.getByRole('button', { name: 'Copiar o identificador' }));

  await waitFor(() => expect(screen.getByText(messages.copy.failed)).toBeInTheDocument());
});
