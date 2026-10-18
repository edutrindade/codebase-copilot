import type { LucideIcon } from 'lucide-react';
import { Button, type ButtonProps } from './Button';
import { cx } from './cx';
import { Tooltip } from './Tooltip';

interface IconButtonProps extends Omit<ButtonProps, 'icon' | 'children' | 'aria-label'> {
  label: string;
  icon: LucideIcon;
  tooltip?: boolean;
}

export function IconButton({
  label,
  icon,
  variant = 'ghost',
  tooltip = true,
  className,
  ...rest
}: IconButtonProps) {
  const button = (
    <Button
      aria-label={label}
      icon={icon}
      variant={variant}
      className={cx('button--icon', className)}
      {...rest}
    />
  );
  return tooltip ? <Tooltip content={label}>{button}</Tooltip> : button;
}
