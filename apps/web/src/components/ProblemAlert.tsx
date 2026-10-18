import { RotateCw } from 'lucide-react';
import { ApiError } from '../api/errors';
import { recoveryFor } from '../api/recovery';
import { messages } from '../messages';
import { Button } from './ui/Button';
import { Callout } from './ui/Callout';
import { CopyButton } from './ui/CopyButton';
import './ProblemAlert.css';

interface ProblemAlertProps {
  error: unknown;
  onRetry?: () => void;
  // Off when the form shows each field error next to its field.
  showFieldErrors?: boolean;
}

export function ProblemAlert({ error, onRetry, showFieldErrors = true }: ProblemAlertProps) {
  const problem = error instanceof ApiError ? error.problem : null;
  const network = error instanceof ApiError && error.isServerUnreachable;
  const title =
    problem?.title ?? (network ? messages.errors.networkTitle : messages.errors.unexpectedTitle);
  const detail =
    problem?.detail ?? (network ? messages.errors.networkDetail : messages.errors.unexpectedDetail);
  const recovery = recoveryFor(error);

  return (
    <Callout role="alert" tone="danger" title={title}>
      <p>{detail}</p>
      {showFieldErrors && problem?.errors && problem.errors.length > 0 && (
        <ul>
          {problem.errors.map((fieldError) => (
            <li key={`${fieldError.field}:${fieldError.message}`}>{fieldError.message}</li>
          ))}
        </ul>
      )}
      <p>{messages.recovery[recovery]}</p>
      {recovery === 'retry' && onRetry && (
        <div>
          <Button size="sm" icon={RotateCw} onClick={onRetry}>
            {messages.errors.retry}
          </Button>
        </div>
      )}
      {problem && (
        <p className="problem-alert__request">
          {messages.errors.requestId}: <code>{problem.requestId}</code>
          <CopyButton text={problem.requestId} label={messages.errors.copyRequestId} />
        </p>
      )}
    </Callout>
  );
}
