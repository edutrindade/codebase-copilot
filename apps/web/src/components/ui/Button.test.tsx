import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Plus } from 'lucide-react';
import { MemoryRouter } from 'react-router';
import { InMain } from '../../test/render';
import { expect, test, vi } from 'vitest';
import { axeViolations } from '../../test/axe';
import { Button, ButtonLink } from './Button';
import { IconButton } from './IconButton';

test('a button is a plain button by default, with its variant and an icon hidden from assistive tech', async () => {
  render(
    <Button variant="primary" icon={Plus}>
      Novo projeto
    </Button>,
    { wrapper: InMain },
  );

  const button = screen.getByRole('button', { name: 'Novo projeto' });
  expect(button).toHaveAttribute('type', 'button');
  expect(button).toHaveClass('button', 'button--primary');
  expect(button.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  expect(await axeViolations()).toEqual([]);
});

test('a pending button is disabled and busy, and keeps the label it was given', async () => {
  const onClick = vi.fn();
  render(
    <Button pending onClick={onClick}>
      Salvando…
    </Button>,
    { wrapper: InMain },
  );

  const button = screen.getByRole('button', { name: 'Salvando…' });
  expect(button).toBeDisabled();
  expect(button).toHaveAttribute('aria-busy', 'true');
  await userEvent.click(button);
  expect(onClick).not.toHaveBeenCalled();
});

test('a link can look like a button', () => {
  render(
    <MemoryRouter>
      <ButtonLink to="/projects/1/ask" variant="primary">
        Perguntar
      </ButtonLink>
    </MemoryRouter>,
    { wrapper: InMain },
  );

  const link = screen.getByRole('link', { name: 'Perguntar' });
  expect(link).toHaveAttribute('href', '/projects/1/ask');
  expect(link).toHaveClass('button--primary');
});

test('an icon button is named by its label', async () => {
  const onClick = vi.fn();
  render(<IconButton label="Adicionar" icon={Plus} onClick={onClick} />, { wrapper: InMain });

  await userEvent.click(screen.getByRole('button', { name: 'Adicionar' }));

  expect(onClick).toHaveBeenCalledOnce();
  expect(await axeViolations()).toEqual([]);
});
