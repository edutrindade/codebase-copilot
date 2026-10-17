import { X } from 'lucide-react';
import { Dialog as RadixDialog } from 'radix-ui';
import type { ReactElement, ReactNode } from 'react';
import { messages } from '../../messages';
import { IconButton } from './IconButton';
import './Dialog.css';

interface DialogProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  trigger?: ReactElement;
  title: string;
  description?: ReactNode;
  children?: ReactNode;
  footer?: ReactNode;
}

export function Dialog({
  open,
  onOpenChange,
  trigger,
  title,
  description,
  children,
  footer,
}: DialogProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      {trigger && <RadixDialog.Trigger asChild>{trigger}</RadixDialog.Trigger>}
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="dialog-overlay" />
        <RadixDialog.Content
          className="dialog"
          // Without a description, Radix expects the attribute to be cleared explicitly.
          {...(description ? {} : { 'aria-describedby': undefined })}
        >
          <div className="dialog__header">
            <RadixDialog.Title className="dialog__title">{title}</RadixDialog.Title>
            <RadixDialog.Close asChild>
              <IconButton label={messages.dialog.close} icon={X} size="sm" tooltip={false} />
            </RadixDialog.Close>
          </div>
          {description && (
            <RadixDialog.Description asChild>
              <div className="dialog__description">{description}</div>
            </RadixDialog.Description>
          )}
          {children}
          {footer && <div className="dialog__actions">{footer}</div>}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
