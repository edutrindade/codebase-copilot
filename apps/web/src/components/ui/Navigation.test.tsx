import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { InMain } from '../../test/render';
import { expect, test } from 'vitest';
import { messages } from '../../messages';
import { axeViolations } from '../../test/axe';
import { Breadcrumbs } from './Breadcrumbs';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './Tabs';

test('breadcrumbs link every level but the current page', async () => {
  render(
    <MemoryRouter>
      <Breadcrumbs
        items={[
          { label: 'Workspaces', to: '/' },
          { label: 'Loja', to: '/workspaces/1' },
          { label: 'sample-shop' },
        ]}
      />
    </MemoryRouter>,
    { wrapper: InMain },
  );

  const nav = screen.getByRole('navigation', { name: messages.breadcrumbs.label });
  expect(nav).toBeInTheDocument();
  expect(screen.getAllByRole('listitem')).toHaveLength(3);
  expect(screen.getByRole('link', { name: 'Loja' })).toHaveAttribute('href', '/workspaces/1');
  expect(screen.queryByRole('link', { name: 'sample-shop' })).not.toBeInTheDocument();
  expect(screen.getByText('sample-shop')).toHaveAttribute('aria-current', 'page');
  expect(await axeViolations()).toEqual([]);
});

test('tabs switch their panel by keyboard', async () => {
  render(
    <Tabs defaultValue="a">
      <TabsList aria-label="Seções">
        <TabsTrigger value="a">Primeira</TabsTrigger>
        <TabsTrigger value="b">Segunda</TabsTrigger>
      </TabsList>
      <TabsContent value="a">Conteúdo A</TabsContent>
      <TabsContent value="b">Conteúdo B</TabsContent>
    </Tabs>,
    { wrapper: InMain },
  );

  await userEvent.click(screen.getByRole('tab', { name: 'Primeira' }));
  await userEvent.keyboard('{ArrowRight}');

  expect(screen.getByRole('tab', { name: 'Segunda' })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByRole('tabpanel')).toHaveTextContent('Conteúdo B');
  expect(await axeViolations()).toEqual([]);
});
