import { ApiError } from '../api/errors';
import { messages } from '../messages';

export function ProblemAlert({ error }: { error: unknown }) {
  const problem = error instanceof ApiError ? error.problem : null;
  const network = error instanceof ApiError && error.isNetworkError;
  const title =
    problem?.title ?? (network ? messages.errors.networkTitle : messages.errors.unexpectedTitle);
  const detail =
    problem?.detail ?? (network ? messages.errors.networkDetail : messages.errors.unexpectedDetail);

  return (
    <div role="alert" className="problem-alert">
      <p className="problem-alert__title">{title}</p>
      <p>{detail}</p>
      {problem && (
        <p className="problem-alert__request">
          {messages.errors.requestId}: <code>{problem.requestId}</code>
        </p>
      )}
    </div>
  );
}
