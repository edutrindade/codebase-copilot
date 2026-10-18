import { Popover as RadixPopover } from 'radix-ui';
import { X } from 'lucide-react';
import type { ReactElement, ReactNode } from 'react';
import { messages } from '../../messages';
import { IconButton } from './IconButton';
import './Popover.css';

interface PopoverProps {
  trigger: ReactElement;
  label: string;
  children: ReactNode;
  side?: 'top' | 'right' | 'bottom' | 'left';
}

export function Popover({ trigger, label, children, side = 'top' }: PopoverProps) {
  return (
    <RadixPopover.Root>
      <RadixPopover.Trigger asChild>{trigger}</RadixPopover.Trigger>
      <RadixPopover.Portal>
        <RadixPopover.Content
          className="popover"
          aria-label={label}
          side={side}
          align="start"
          sideOffset={6}
          collisionPadding={8}
        >
          <div className="popover__header">
            <p className="popover__title">{label}</p>
            <RadixPopover.Close asChild>
              <IconButton label={messages.dialog.close} icon={X} size="sm" tooltip={false} />
            </RadixPopover.Close>
          </div>
          {children}
        </RadixPopover.Content>
      </RadixPopover.Portal>
    </RadixPopover.Root>
  );
}
