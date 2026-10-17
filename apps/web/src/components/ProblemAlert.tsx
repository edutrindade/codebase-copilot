import { ApiError } from '../api/errors';
import { recoveryFor } from '../api/recovery';
import { messages } from '../messages';

interface ProblemAlertProps {
  error: unknown;
  onRetry?: () => void;
}

export function ProblemAlert({ error, onRetry }: ProblemAlertProps) {
  const problem = error instanceof ApiError ? error.problem : null;
  const network = error instanceof ApiError && error.isNetworkError;
  const title =
    problem?.title ?? (network ? messages.errors.networkTitle : messages.errors.unexpectedTitle);
  const detail =
    problem?.detail ?? (network ? messages.errors.networkDetail : messages.errors.unexpectedDetail);
  const recovery = recoveryFor(error);

  return (
    <div role="alert" className="problem-alert">
      <p className="problem-alert__title">{title}</p>
      <p>{detail}</p>
      {problem?.errors && problem.errors.length > 0 && (
        <ul>
          {problem.errors.map((fieldError) => (
            <li key={`${fieldError.field}:${fieldError.message}`}>{fieldError.message}</li>
          ))}
        </ul>
      )}
      <p>{messages.recovery[recovery]}</p>
      {recovery === 'retry' && onRetry && (
        <button type="button" className="button" onClick={onRetry}>
          {messages.errors.retry}
        </button>
      )}
      {problem && (
        <p className="problem-alert__request">
          {messages.errors.requestId}: <code>{problem.requestId}</code>
        </p>
      )}
    </div>
  );
}
