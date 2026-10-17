import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Play, Trash } from 'lucide-react';
import { expect, test, vi } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { Button } from './Button';
import { Dialog } from './Dialog';
import { Menu, MenuItem } from './Menu';

test('a dialog opens from its trigger and closes with its close button or Escape', async () => {
  render(
    <Dialog
      trigger={<Button>Abrir</Button>}
      title="Detalhes"
      description={<p>Descrição do diálogo.</p>}
    />,
  );

  await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));
  const dialog = screen.getByRole('dialog', { name: 'Detalhes' });
  expect(dialog).toHaveAccessibleDescription('Descrição do diálogo.');
  expect(await axeViolations()).toEqual([]);

  await userEvent.click(screen.getByRole('button', { name: messages.dialog.close }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));
  await userEvent.keyboard('{Escape}');
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

test('a menu runs the chosen item', async () => {
  const reindex = vi.fn();
  const remove = vi.fn();
  render(
    <Menu trigger={<Button>Ações</Button>}>
      <MenuItem icon={Play} onSelect={reindex}>
        Indexar de novo
      </MenuItem>
      <MenuItem icon={Trash} tone="danger" onSelect={remove}>
        Excluir
      </MenuItem>
    </Menu>,
  );

  await userEvent.click(screen.getByRole('button', { name: 'Ações' }));
  expect(screen.getByRole('menu')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('menuitem', { name: 'Indexar de novo' }));

  expect(reindex).toHaveBeenCalledOnce();
  expect(remove).not.toHaveBeenCalled();
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
});
