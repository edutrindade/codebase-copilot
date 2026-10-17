import { Check, type LucideIcon } from 'lucide-react';
import { DropdownMenu } from 'radix-ui';
import type { ReactElement, ReactNode } from 'react';
import { cx } from './cx';
import './Menu.css';

interface MenuProps {
  trigger: ReactElement;
  children: ReactNode;
  align?: 'start' | 'center' | 'end';
}

export function Menu({ trigger, children, align = 'end' }: MenuProps) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>{trigger}</DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content className="menu" align={align} sideOffset={6} collisionPadding={8}>
          {children}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

interface MenuItemProps {
  icon?: LucideIcon;
  tone?: 'default' | 'danger';
  disabled?: boolean;
  onSelect: () => void;
  children: ReactNode;
}

export function MenuItem({
  icon: Icon,
  tone = 'default',
  disabled,
  onSelect,
  children,
}: MenuItemProps) {
  return (
    <DropdownMenu.Item
      className={cx('menu__item', tone === 'danger' && 'menu__item--danger')}
      disabled={disabled}
      onSelect={onSelect}
    >
      {Icon ? <Icon aria-hidden="true" className="menu__icon" /> : <span className="menu__icon" />}
      {children}
    </DropdownMenu.Item>
  );
}

export function MenuLabel({ children }: { children: ReactNode }) {
  return <DropdownMenu.Label className="menu__label">{children}</DropdownMenu.Label>;
}

export function MenuSeparator() {
  return <DropdownMenu.Separator className="menu__separator" />;
}

interface MenuRadioGroupProps {
  value: string;
  onValueChange: (value: string) => void;
  children: ReactNode;
}

export function MenuRadioGroup({ value, onValueChange, children }: MenuRadioGroupProps) {
  return (
    <DropdownMenu.RadioGroup value={value} onValueChange={onValueChange}>
      {children}
    </DropdownMenu.RadioGroup>
  );
}

interface MenuRadioItemProps {
  value: string;
  icon?: LucideIcon;
  children: ReactNode;
}

export function MenuRadioItem({ value, icon: Icon, children }: MenuRadioItemProps) {
  return (
    <DropdownMenu.RadioItem className="menu__item" value={value}>
      {Icon ? <Icon aria-hidden="true" className="menu__icon" /> : <span className="menu__icon" />}
      {children}
      <DropdownMenu.ItemIndicator className="menu__check">
        <Check aria-hidden="true" />
      </DropdownMenu.ItemIndicator>
    </DropdownMenu.RadioItem>
  );
}
