import { useId, useState, type ReactNode } from 'react';
import { Upload } from 'lucide-react';
import { buttonClassName } from './Button';
import { cx } from './cx';
import './Dropzone.css';

interface DropzoneProps {
  // The button text, which also names the file input.
  label: string;
  prompt: string;
  hint: string;
  accept: string;
  disabled?: boolean;
  invalid?: boolean;
  errorId?: string;
  onFile: (file: File) => void;
  children?: ReactNode;
}

// The button is the alternative to dragging (WCAG 2.5.7); the hidden input keeps the keyboard path.
export function Dropzone({
  label,
  prompt,
  hint,
  accept,
  disabled,
  invalid,
  errorId,
  onFile,
  children,
}: DropzoneProps) {
  const inputId = useId();
  const hintId = useId();
  const [over, setOver] = useState(false);

  return (
    <div
      className={cx('dropzone', over && 'dropzone--over', disabled && 'dropzone--disabled')}
      onDragOver={(event) => {
        if (!disabled) {
          event.preventDefault();
          setOver(true);
        }
      }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setOver(false);
        }
      }}
      onDrop={(event) => {
        event.preventDefault();
        setOver(false);
        const file = event.dataTransfer.files[0];
        if (!disabled && file) {
          onFile(file);
        }
      }}
    >
      <Upload aria-hidden="true" className="dropzone__icon" />
      <p>{prompt}</p>
      <label htmlFor={inputId} className={buttonClassName()}>
        {label}
      </label>
      <input
        id={inputId}
        type="file"
        className="visually-hidden"
        accept={accept}
        disabled={disabled}
        aria-invalid={invalid || undefined}
        aria-describedby={cx(hintId, invalid && errorId)}
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            onFile(file);
          }
        }}
      />
      <p id={hintId} className="dropzone__hint">
        {hint}
      </p>
      {children}
    </div>
  );
}
