import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { cx } from './cx';

interface ControlProps {
  id: string;
  'aria-describedby': string | undefined;
  'aria-invalid': true | undefined;
}

interface FieldProps {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  wide?: boolean;
  children: (control: ControlProps) => ReactNode;
}

// Label, hint and error wired to the control by id, so each one is announced with it.
export function Field({ label, hint, error, wide = false, children }: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ');
  return (
    <div className={cx('field', wide && 'field--wide')}>
      <label htmlFor={id}>{label}</label>
      {children({
        id,
        'aria-describedby': describedBy || undefined,
        'aria-invalid': error ? true : undefined,
      })}
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      )}
    </div>
  );
}

type FieldLabels = Omit<FieldProps, 'children'>;

export function TextField({
  label,
  hint,
  error,
  wide,
  ...input
}: FieldLabels & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <Field label={label} hint={hint} error={error} wide={wide}>
      {(control) => <input type="text" {...input} {...control} />}
    </Field>
  );
}

export function TextAreaField({
  label,
  hint,
  error,
  wide,
  ...textarea
}: FieldLabels & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <Field label={label} hint={hint} error={error} wide={wide}>
      {(control) => <textarea {...textarea} {...control} />}
    </Field>
  );
}

export function SelectField({
  label,
  hint,
  error,
  wide,
  ...select
}: FieldLabels & SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <Field label={label} hint={hint} error={error} wide={wide}>
      {(control) => <select {...select} {...control} />}
    </Field>
  );
}
