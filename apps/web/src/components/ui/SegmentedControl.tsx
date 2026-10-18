import { ToggleGroup } from 'radix-ui';
import { cx } from './cx';
import './SegmentedControl.css';

interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  disabled?: boolean;
}

interface SegmentedControlProps<T extends string> {
  label: string;
  value: T;
  options: readonly SegmentedOption<T>[];
  onChange: (value: T) => void;
  size?: 'sm' | 'md';
}

// A radio group drawn as joined buttons; one option is always chosen.
export function SegmentedControl<T extends string>({
  label,
  value,
  options,
  onChange,
  size = 'sm',
}: SegmentedControlProps<T>) {
  return (
    <ToggleGroup.Root
      type="single"
      className={cx('segmented', size === 'sm' && 'segmented--sm')}
      aria-label={label}
      value={value}
      onValueChange={(next) => {
        const option = options.find((item) => item.value === next);
        if (option) {
          onChange(option.value);
        }
      }}
    >
      {options.map((option) => (
        <ToggleGroup.Item
          key={option.value}
          value={option.value}
          disabled={option.disabled}
          className="segmented__item"
        >
          {option.label}
        </ToggleGroup.Item>
      ))}
    </ToggleGroup.Root>
  );
}
