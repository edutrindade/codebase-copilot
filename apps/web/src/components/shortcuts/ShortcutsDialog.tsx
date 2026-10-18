import { Link } from 'react-router';
import { messages } from '../../messages';
import { Dialog } from '../ui/Dialog';
import { ShortcutList } from './ShortcutList';

interface ShortcutsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ShortcutsDialog({ open, onOpenChange }: ShortcutsDialogProps) {
  const text = messages.shortcuts;
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={text.title}>
      <ShortcutList />
      <p className="muted">{text.detail}</p>
      <p>
        <Link to="/system" onClick={() => onOpenChange(false)}>
          {text.manage}
        </Link>
      </p>
    </Dialog>
  );
}
