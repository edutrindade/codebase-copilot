import { Trash, type LucideIcon } from 'lucide-react';
import { AlertDialog } from 'radix-ui';
import { useState, type MouseEvent, type ReactNode } from 'react';
import { messages } from '../messages';
import { ProblemAlert } from './ProblemAlert';
import { Button } from './ui/Button';
import './ui/Dialog.css';

interface ConfirmDialogProps {
  trigger: string;
  triggerLabel: string;
  triggerIcon?: LucideIcon;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  dismissLabel?: string;
  workingLabel?: string;
  onConfirm: () => Promise<unknown>;
}

export function ConfirmDialog({
  trigger,
  triggerLabel,
  triggerIcon = Trash,
  title,
  description,
  confirmLabel,
  dismissLabel = messages.confirm.cancel,
  workingLabel = messages.confirm.working,
  onConfirm,
}: ConfirmDialogProps) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  function changeOpen(next: boolean) {
    if (pending) {
      return;
    }
    setOpen(next);
    if (next) {
      setError(null);
    }
  }

  async function confirm(event: MouseEvent) {
    // Keep the dialog open until the request settles, so an error can be shown in place.
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await onConfirm();
      setOpen(false);
    } catch (cause) {
      setError(cause);
    } finally {
      setPending(false);
    }
  }

  return (
    <AlertDialog.Root open={open} onOpenChange={changeOpen}>
      <AlertDialog.Trigger asChild>
        <Button variant="danger-ghost" size="sm" icon={triggerIcon} aria-label={triggerLabel}>
          {trigger}
        </Button>
      </AlertDialog.Trigger>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="dialog-overlay" />
        <AlertDialog.Content className="dialog">
          <AlertDialog.Title className="dialog__title">{title}</AlertDialog.Title>
          <AlertDialog.Description asChild>
            <div className="dialog__description">{description}</div>
          </AlertDialog.Description>
          {error !== null && <ProblemAlert error={error} />}
          <div className="dialog__actions">
            <AlertDialog.Cancel asChild>
              <Button disabled={pending}>{dismissLabel}</Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button variant="danger" pending={pending} onClick={confirm}>
                {pending ? workingLabel : confirmLabel}
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
