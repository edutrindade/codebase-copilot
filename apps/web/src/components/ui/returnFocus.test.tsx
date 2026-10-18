import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, test } from 'vitest';
import { Dialog } from './Dialog';

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <main>
      <button type="button" onClick={() => setOpen(true)}>
        Abrir
      </button>
      <h1 tabIndex={-1}>Título</h1>
      {open && (
        <Dialog open onOpenChange={setOpen} title="Sem gatilho">
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              // After the dialog is gone, as the route announcer does after a navigation.
              setTimeout(() => screen.getByRole('heading', { name: 'Título' }).focus());
            }}
          >
            Ir para o título
          </button>
        </Dialog>
      )}
    </main>
  );
}

describe('dialog without a trigger', () => {
  test('returns focus to where it was when it opened', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole('button', { name: 'Abrir' }));
    await screen.findByRole('dialog', { name: 'Sem gatilho' });
    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.getByRole('button', { name: 'Abrir' })).toHaveFocus());
  });

  test('leaves focus alone when something else took it, as a navigation does', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole('button', { name: 'Abrir' }));
    await user.click(await screen.findByRole('button', { name: 'Ir para o título' }));
    await new Promise((resolve) => setTimeout(resolve, 10));

    expect(screen.getByRole('heading', { name: 'Título' })).toHaveFocus();
  });
});
