import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test, vi } from 'vitest';
import {
  SINGLE_KEY_SHORTCUTS_KEY,
  setSingleKeyShortcuts,
  useShortcut,
  useShortcutListener,
  type ShortcutAction,
} from './shortcuts';

interface HarnessProps {
  bindings: Partial<Record<ShortcutAction, () => void>>;
}

function Binding({ action, handler }: { action: ShortcutAction; handler: () => void }) {
  useShortcut(action, handler);
  return null;
}

function Harness({ bindings }: HarnessProps) {
  useShortcutListener();
  return (
    <>
      {Object.entries(bindings).map(([action, handler]) => (
        <Binding key={action} action={action as ShortcutAction} handler={handler} />
      ))}
      <label>
        Campo
        <textarea />
      </label>
      <button type="button">Botão</button>
      <div role="dialog" aria-label="Diálogo">
        <button type="button">Dentro do diálogo</button>
      </div>
    </>
  );
}

describe('shortcuts', () => {
  test('a single key runs the registered action', async () => {
    const user = userEvent.setup();
    const focusQuestion = vi.fn();
    const nextEvidence = vi.fn();
    render(<Harness bindings={{ focusQuestion, nextEvidence }} />);

    await user.keyboard('/');
    await user.keyboard(']');

    expect(focusQuestion).toHaveBeenCalledOnce();
    expect(nextEvidence).toHaveBeenCalledOnce();
  });

  test('single keys do nothing while typing or inside a dialog', async () => {
    const user = userEvent.setup();
    const focusQuestion = vi.fn();
    render(<Harness bindings={{ focusQuestion }} />);

    await user.click(screen.getByRole('textbox', { name: 'Campo' }));
    await user.keyboard('/');
    expect(screen.getByRole('textbox', { name: 'Campo' })).toHaveValue('/');

    screen.getByRole('button', { name: 'Dentro do diálogo' }).focus();
    await user.keyboard('/');

    expect(focusQuestion).not.toHaveBeenCalled();
  });

  test('the palette opens with Ctrl or ⌘ + K, even from a text field', async () => {
    const user = userEvent.setup();
    const openPalette = vi.fn();
    render(<Harness bindings={{ openPalette }} />);

    await user.click(screen.getByRole('textbox', { name: 'Campo' }));
    await user.keyboard('{Control>}k{/Control}');
    await user.keyboard('{Meta>}k{/Meta}');

    expect(openPalette).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('textbox', { name: 'Campo' })).toHaveValue('');
  });

  test('g followed by a letter goes to a section; any other key cancels the sequence', async () => {
    const user = userEvent.setup();
    const goAsk = vi.fn();
    const goHistory = vi.fn();
    render(<Harness bindings={{ goAsk, goHistory }} />);

    await user.keyboard('gp');
    await user.keyboard('gxh');
    await user.keyboard('h');

    expect(goAsk).toHaveBeenCalledOnce();
    expect(goHistory).not.toHaveBeenCalled();
  });

  test('turning single keys off keeps only the shortcuts with a modifier', async () => {
    const user = userEvent.setup();
    const focusQuestion = vi.fn();
    const openPalette = vi.fn();
    render(<Harness bindings={{ focusQuestion, openPalette }} />);

    setSingleKeyShortcuts(false);
    await user.keyboard('/');
    await user.keyboard('{Control>}k{/Control}');

    expect(window.localStorage.getItem(SINGLE_KEY_SHORTCUTS_KEY)).toBe('off');
    expect(focusQuestion).not.toHaveBeenCalled();
    expect(openPalette).toHaveBeenCalledOnce();

    setSingleKeyShortcuts(true);
    await user.keyboard('/');
    expect(focusQuestion).toHaveBeenCalledOnce();
  });

  test('the latest registration of an action wins', async () => {
    const user = userEvent.setup();
    const shell = vi.fn();
    const screenHandler = vi.fn();
    const { rerender } = render(<Harness bindings={{ showShortcuts: shell }} />);
    rerender(
      <>
        <Harness bindings={{ showShortcuts: shell }} />
        <Binding action="showShortcuts" handler={screenHandler} />
      </>,
    );

    await user.keyboard('?');
    expect(screenHandler).toHaveBeenCalledOnce();
    expect(shell).not.toHaveBeenCalled();
  });
});
