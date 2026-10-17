import type { Recovery } from '../../api/recovery';

// Answer error codes (06 §8) are plain strings in the contract, so unknown ones fall back to asking again.
const recoveryByAnswerCode: Record<string, Recovery> = {
  GENERATION_PROVIDER_UNAUTHORIZED: 'configureProvider',
  GENERATION_PROVIDER_UNAVAILABLE: 'providerLimit',
  GENERATION_QUOTA_EXCEEDED: 'providerLimit',
  GENERATION_RATE_LIMITED: 'providerLimit',
  GENERATION_REQUEST_REJECTED: 'providerLimit',
  EMBEDDING_PROVIDER_UNAUTHORIZED: 'configureProvider',
  EMBEDDING_PROVIDER_UNAVAILABLE: 'providerLimit',
  EMBEDDING_QUOTA_EXCEEDED: 'providerLimit',
  EMBEDDING_RATE_LIMITED: 'providerLimit',
  EMBEDDING_REQUEST_REJECTED: 'providerLimit',
};

export function answerRecovery(code: string): Recovery {
  return recoveryByAnswerCode[code] ?? 'askAgain';
}
