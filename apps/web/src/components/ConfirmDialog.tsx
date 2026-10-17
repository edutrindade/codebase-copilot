import { AlertDialog } from 'radix-ui';
import { useState, type MouseEvent, type ReactNode } from 'react';
import { messages } from '../messages';
import { ProblemAlert } from './ProblemAlert';

interface ConfirmDialogProps {
  trigger: string;
  triggerLabel: string;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  onConfirm: () => Promise<unknown>;
}

export function ConfirmDialog({
  trigger,
  triggerLabel,
  title,
  description,
  confirmLabel,
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
        <button type="button" className="button button--danger-quiet" aria-label={triggerLabel}>
          {trigger}
        </button>
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
              <button type="button" className="button" disabled={pending}>
                {messages.confirm.cancel}
              </button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <button
                type="button"
                className="button button--danger"
                disabled={pending}
                onClick={confirm}
              >
                {pending ? messages.confirm.working : confirmLabel}
              </button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
