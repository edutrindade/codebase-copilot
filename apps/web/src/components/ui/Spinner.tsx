import { LoaderCircle } from 'lucide-react';
import { cx } from './cx';
import './Spinner.css';

export function Spinner({ className }: { className?: string }) {
  return <LoaderCircle aria-hidden="true" className={cx('spinner', className)} />;
}
