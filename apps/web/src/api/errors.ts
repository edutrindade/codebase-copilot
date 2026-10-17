import type { components } from './schema';

export type Problem = components['schemas']['Problem'];
export type ErrorCode = Problem['code'];
export type FieldError = components['schemas']['FieldError'];

const GATEWAY_STATUSES = new Set([502, 503, 504]);

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;

  constructor(status: number, problem: Problem | null) {
    super(problem ? `${problem.code} (${status})` : `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get code(): ErrorCode | null {
    return this.problem?.code ?? null;
  }

  get isNetworkError(): boolean {
    return this.status === 0;
  }

  // The backend always answers with a ProblemDetail; a bare 502-504 comes from the proxy in front of it.
  get isServerUnreachable(): boolean {
    return this.isNetworkError || (this.problem === null && GATEWAY_STATUSES.has(this.status));
  }
}

export function isProblem(value: unknown): value is Problem {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.code === 'string' &&
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number'
  );
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

function isAbort(cause: unknown): boolean {
  return (
    typeof cause === 'object' && cause !== null && 'name' in cause && cause.name === 'AbortError'
  );
}

export async function unwrap<T>(request: Promise<FetchResult<T>>): Promise<T> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch (cause) {
    if (isAbort(cause)) {
      throw cause;
    }
    throw new ApiError(0, null);
  }
  const { data, error, response } = result;
  if (!response.ok || error !== undefined) {
    throw new ApiError(response.status, isProblem(error) ? error : null);
  }
  return data as T;
}
