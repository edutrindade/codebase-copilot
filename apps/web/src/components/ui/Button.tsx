import type { LucideIcon } from 'lucide-react';
import type { ButtonHTMLAttributes, Ref } from 'react';
import { Link, type LinkProps } from 'react-router';
import { cx } from './cx';
import { Spinner } from './Spinner';
import './Button.css';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'danger-ghost';
export type ButtonSize = 'sm' | 'md';

interface ButtonStyle {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

export function buttonClassName({ variant = 'secondary', size = 'md' }: ButtonStyle = {}): string {
  return cx('button', `button--${variant}`, size === 'sm' && 'button--sm');
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, ButtonStyle {
  icon?: LucideIcon;
  // Disables the button and swaps the icon for a spinner; the label should say what is happening.
  pending?: boolean;
  ref?: Ref<HTMLButtonElement>;
}

export function Button({
  variant,
  size,
  icon: Icon,
  pending = false,
  className,
  type = 'button',
  disabled,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cx(buttonClassName({ variant, size }), className)}
      disabled={disabled || pending}
      aria-busy={pending || undefined}
      {...rest}
    >
      {pending ? <Spinner /> : Icon && <Icon aria-hidden="true" className="button__icon" />}
      {children}
    </button>
  );
}

interface ButtonLinkProps extends LinkProps, ButtonStyle {
  icon?: LucideIcon;
}

export function ButtonLink({
  variant,
  size,
  icon: Icon,
  className,
  children,
  ...rest
}: ButtonLinkProps) {
  return (
    <Link className={cx(buttonClassName({ variant, size }), className)} {...rest}>
      {Icon && <Icon aria-hidden="true" className="button__icon" />}
      {children}
    </Link>
  );
}
