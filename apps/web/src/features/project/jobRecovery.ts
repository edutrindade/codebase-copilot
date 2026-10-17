import type { Recovery } from '../../api/recovery';

// Index job error codes (06 §7) are plain strings in the contract, so unknown ones fall back to a retry.
const recoveryByJobCode: Record<string, Recovery> = {
  SOURCE_ROOT_NOT_CONFIGURED: 'reviewServerConfig',
  SOURCE_DIRECTORY_NOT_FOUND: 'checkSource',
  SOURCE_IS_SYMLINK: 'checkSource',
  SOURCE_NOT_READABLE: 'checkSource',
  SOURCE_NOT_IMPORTED: 'uploadZip',
  SCAN_LIMIT_REACHED: 'reviewExclusions',
  PROJECT_SIZE_LIMIT_EXCEEDED: 'reviewExclusions',
  NO_INCLUDED_FILES: 'reviewExclusions',
  EMBEDDING_PROVIDER_UNAUTHORIZED: 'configureProvider',
  EMBEDDING_PROVIDER_UNAVAILABLE: 'providerLimit',
  EMBEDDING_RATE_LIMITED: 'providerLimit',
  EMBEDDING_QUOTA_EXCEEDED: 'providerLimit',
  EMBEDDING_REQUEST_REJECTED: 'providerLimit',
  EMBEDDING_CHUNK_LIMIT_EXCEEDED: 'providerLimit',
};

export function jobRecovery(code: string): Recovery {
  return recoveryByJobCode[code] ?? 'retry';
}
