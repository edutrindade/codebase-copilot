import { X } from 'lucide-react';
import { Dialog as RadixDialog } from 'radix-ui';
import type { ReactNode } from 'react';
import { messages } from '../../messages';
import { IconButton } from '../ui/IconButton';

interface NavDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}

export function NavDrawer({ open, onOpenChange, children }: NavDrawerProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="drawer-overlay" />
        <RadixDialog.Content className="drawer" aria-describedby={undefined}>
          <RadixDialog.Title className="visually-hidden">
            {messages.shell.navTitle}
          </RadixDialog.Title>
          <RadixDialog.Close asChild>
            <IconButton
              label={messages.dialog.close}
              icon={X}
              size="sm"
              tooltip={false}
              className="drawer__close"
            />
          </RadixDialog.Close>
          {children}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
