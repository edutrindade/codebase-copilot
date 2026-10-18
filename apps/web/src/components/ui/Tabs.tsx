import { Tabs as RadixTabs } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cx } from './cx';
import './Tabs.css';

export const Tabs = RadixTabs.Root;

export function TabsList({ className, ...rest }: ComponentProps<typeof RadixTabs.List>) {
  return <RadixTabs.List className={cx('tabs', className)} {...rest} />;
}

export function TabsTrigger({ className, ...rest }: ComponentProps<typeof RadixTabs.Trigger>) {
  return <RadixTabs.Trigger className={cx('tabs__trigger', className)} {...rest} />;
}

export function TabsContent({ className, ...rest }: ComponentProps<typeof RadixTabs.Content>) {
  return <RadixTabs.Content className={cx('tabs__content', className)} {...rest} />;
}
