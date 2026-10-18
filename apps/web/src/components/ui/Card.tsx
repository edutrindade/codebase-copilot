import type { HTMLAttributes } from 'react';
import { cx } from './cx';
import './Card.css';

interface CardProps extends HTMLAttributes<HTMLElement> {
  as?: 'div' | 'section' | 'article' | 'form';
}

export function Card({ as: Element = 'div', className, ...rest }: CardProps) {
  return <Element className={cx('panel', className)} {...rest} />;
}
