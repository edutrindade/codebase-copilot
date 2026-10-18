import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useRef, useState } from 'react';
import { expect, test, vi } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { InMain } from '../../test/render';
import { Button } from './Button';
import { HoverCard } from './HoverCard';
import { Popover } from './Popover';
import { SegmentedControl } from './SegmentedControl';
import { Splitter } from './Splitter';

function SplitPanes({ onChange }: { onChange?: (value: number) => void }) {
  const [value, setValue] = useState(50);
  const container = useRef<HTMLDivElement>(null);
  return (
    <InMain>
      <div ref={container}>
        <div id="left">Esquerda</div>
        <Splitter
          value={value}
          min={30}
          max={70}
          onChange={(next) => {
            setValue(next);
            onChange?.(next);
          }}
          containerRef={container}
          controls="left"
          label="Divisor"
          valueText={(percent) => `${percent}%`}
          decreaseLabel="Estreitar"
          increaseLabel="Alargar"
        />
      </div>
    </InMain>
  );
}

test('a splitter moves by arrows, Home and End and stays within its limits', async () => {
  render(<SplitPanes />);
  const separator = screen.getByRole('separator', { name: 'Divisor' });
  expect(separator).toHaveAttribute('aria-orientation', 'vertical');
  expect(separator).toHaveAttribute('aria-controls', 'left');

  separator.focus();
  await userEvent.keyboard('{ArrowLeft}');
  expect(separator).toHaveAttribute('aria-valuenow', '45');
  expect(separator).toHaveAttribute('aria-valuetext', '45%');
  await userEvent.keyboard('{Home}{ArrowLeft}');
  expect(separator).toHaveAttribute('aria-valuenow', '30');
  expect(screen.getByRole('button', { name: 'Estreitar' })).toBeDisabled();
  await userEvent.keyboard('{End}');
  expect(separator).toHaveAttribute('aria-valuenow', '70');
  expect(await axeViolations()).toEqual([]);
});

test('a splitter follows the pointer while dragging, relative to its container', async () => {
  const onChange = vi.fn();
  render(<SplitPanes onChange={onChange} />);
  const separator = screen.getByRole('separator', { name: 'Divisor' });
  const container = separator.closest('main > div') as HTMLElement;
  container.getBoundingClientRect = () => ({ left: 100, width: 1000 }) as DOMRect;
  Object.assign(separator, {
    setPointerCapture: () => {},
    hasPointerCapture: () => true,
    releasePointerCapture: () => {},
  });

  const user = userEvent.setup();
  await user.pointer([
    { keys: '[MouseLeft>]', target: separator, coords: { clientX: 600 } },
    { target: separator, coords: { clientX: 450 } },
    { target: separator, coords: { clientX: 50 } },
    { keys: '[/MouseLeft]', target: separator },
    { target: separator, coords: { clientX: 900 } },
  ]);

  expect(onChange.mock.calls.map(([value]) => value)).toEqual([35, 30]);
  expect(separator).toHaveAttribute('aria-valuenow', '30');
});

test('a segmented control is a radio group with one choice', async () => {
  const onChange = vi.fn();
  render(
    <InMain>
      <SegmentedControl
        label="Versão"
        value="a"
        options={[
          { value: 'a', label: 'Da resposta' },
          { value: 'b', label: 'Atual' },
          { value: 'c', label: 'Outra', disabled: true },
        ]}
        onChange={onChange}
      />
    </InMain>,
  );
  const group = screen.getByRole('radiogroup', { name: 'Versão' });
  expect(screen.getByRole('radio', { name: 'Da resposta' })).toBeChecked();
  expect(screen.getByRole('radio', { name: 'Outra' })).toBeDisabled();

  await userEvent.click(screen.getByRole('radio', { name: 'Da resposta' }));
  expect(onChange).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('radio', { name: 'Atual' }));
  expect(onChange).toHaveBeenCalledWith('b');
  expect(group).toBeInTheDocument();
  expect(await axeViolations()).toEqual([]);
});

test('a hover card opens on focus and is hidden from assistive technology', async () => {
  render(
    <InMain>
      <HoverCard content={<p>src/App.java, linhas 1–3</p>}>
        <button type="button" aria-label="Abrir a evidência E1: src/App.java, linhas 1–3">
          E1
        </button>
      </HoverCard>
    </InMain>,
  );

  screen.getByRole('button').focus();
  await waitFor(() => expect(screen.getByText('src/App.java, linhas 1–3')).toBeInTheDocument());
  expect(document.querySelector('.hover-card')).toHaveAttribute('aria-hidden', 'true');
  await userEvent.keyboard('{Escape}');
  await waitFor(() => expect(document.querySelector('.hover-card')).toBeNull());
});

test('a popover is a named dialog that closes with its button or Escape', async () => {
  render(
    <InMain>
      <Popover label="Detalhes da resposta" trigger={<Button>Detalhes</Button>}>
        <p>1,2 s</p>
      </Popover>
    </InMain>,
  );

  await userEvent.click(screen.getByRole('button', { name: 'Detalhes' }));
  expect(screen.getByRole('dialog', { name: 'Detalhes da resposta' })).toHaveTextContent('1,2 s');
  expect(await axeViolations()).toEqual([]);
  await userEvent.click(screen.getByRole('button', { name: messages.dialog.close }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Detalhes' }));
  await userEvent.keyboard('{Escape}');
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Detalhes' })).toHaveFocus();
});
