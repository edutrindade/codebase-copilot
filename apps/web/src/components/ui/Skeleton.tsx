import { cx } from './cx';
import './Skeleton.css';

interface SkeletonProps {
  width?: string;
  height?: string;
  className?: string;
}

// Decorative: the surrounding region says what is loading.
export function Skeleton({ width, height, className }: SkeletonProps) {
  return (
    <span aria-hidden="true" className={cx('skeleton', className)} style={{ width, height }} />
  );
}
