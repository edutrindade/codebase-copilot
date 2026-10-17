import { render, screen, within } from '@testing-library/react';
import { InMain } from '../../test/render';
import { expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { Loading } from '../Loading';
import { Callout } from './Callout';
import { EmptyState } from './EmptyState';
import { Progress } from './Progress';
import { Stat, StatGrid } from './Stat';
import { StatusBadge } from './StatusBadge';

test('a status badge says its state in text, with a decorative icon', async () => {
  render(<StatusBadge tone="success">Pronto</StatusBadge>, { wrapper: InMain });

  const badge = screen.getByText('Pronto');
  expect(badge).toHaveClass('badge', 'badge--success');
  expect(badge.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  expect(await axeViolations()).toEqual([]);
});

test('a callout keeps the role and element it is given', async () => {
  render(
    <>
      <Callout role="alert" tone="danger" title="Falhou">
        <p>Detalhe</p>
      </Callout>
      <ul>
        <Callout as="li" tone="warning">
          <p>Aviso da lista</p>
        </Callout>
      </ul>
    </>,
    { wrapper: InMain },
  );

  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent('Falhou');
  expect(alert).toHaveTextContent('Detalhe');
  expect(screen.getByRole('listitem')).toHaveTextContent('Aviso da lista');
  expect(await axeViolations()).toEqual([]);
});

test('loading is announced as a status with text, behind decorative placeholders', async () => {
  render(<Loading />, { wrapper: InMain });

  const status = screen.getByRole('status');
  expect(status).toHaveTextContent(messages.loading);
  expect(status.querySelectorAll('.skeleton[aria-hidden="true"]')).toHaveLength(3);
  expect(await axeViolations()).toEqual([]);
});

test('an empty state uses the heading level it is given', () => {
  render(
    <EmptyState title="Nada aqui" headingLevel={1}>
      <p>Comece importando um projeto.</p>
    </EmptyState>,
    { wrapper: InMain },
  );

  expect(screen.getByRole('heading', { level: 1, name: 'Nada aqui' })).toBeInTheDocument();
});

test('stats are terms and definitions', async () => {
  render(
    <StatGrid>
      <Stat label="Arquivos" value="1.240" hint="incluídos" />
    </StatGrid>,
    { wrapper: InMain },
  );

  expect(screen.getByRole('term')).toHaveTextContent('Arquivos');
  expect(screen.getAllByRole('definition').map((item) => item.textContent)).toEqual([
    '1.240',
    'incluídos',
  ]);
  expect(await axeViolations()).toEqual([]);
});

test('progress exposes its value, and none when indeterminate', async () => {
  render(
    <>
      <Progress value={42} max={100} label="Arquivos: 42 de 100" />
      <Progress value={null} label="Aguardando" />
    </>,
    { wrapper: InMain },
  );

  const [known, unknown] = screen.getAllByRole('progressbar');
  expect(known).toHaveAccessibleName('Arquivos: 42 de 100');
  expect(known).toHaveAttribute('aria-valuenow', '42');
  expect(known).toHaveAttribute('aria-valuemax', '100');
  expect(unknown).not.toHaveAttribute('aria-valuenow');
  expect(within(document.body).getAllByRole('progressbar')).toHaveLength(2);
  expect(await axeViolations()).toEqual([]);
});
