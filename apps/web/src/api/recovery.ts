import { ApiError, type ErrorCode } from './errors';

export type Recovery =
  | 'retry'
  | 'reload'
  | 'fixInput'
  | 'goBack'
  | 'removeProjects'
  | 'waitForJob'
  | 'chooseAnother'
  | 'checkSource'
  | 'uploadZip'
  | 'reindex'
  | 'reviewServerConfig'
  | 'configureProvider'
  | 'providerLimit'
  | 'useEvidenceCopy'
  | 'openLocally'
  | 'reviewExclusions'
  | 'askAgain';

// A Record forces a decision for every code the contract adds.
export const recoveryByCode: Record<ErrorCode, Recovery> = {
  VALIDATION_FAILED: 'fixInput',
  MALFORMED_REQUEST: 'reload',
  RESOURCE_NOT_FOUND: 'reload',
  METHOD_NOT_ALLOWED: 'reload',
  UNSUPPORTED_MEDIA_TYPE: 'reload',
  INTERNAL_ERROR: 'retry',
  WORKSPACE_NOT_FOUND: 'goBack',
  WORKSPACE_NOT_EMPTY: 'removeProjects',
  PROJECT_NOT_FOUND: 'goBack',
  LOCAL_ROOTS_NOT_CONFIGURED: 'reviewServerConfig',
  ROOT_NOT_FOUND: 'reviewServerConfig',
  PATH_OUTSIDE_ROOT: 'fixInput',
  PROJECT_ALREADY_EXISTS: 'chooseAnother',
  DIRECTORY_NOT_FOUND: 'chooseAnother',
  DIRECTORY_NOT_READABLE: 'chooseAnother',
  PROJECT_BUSY: 'waitForJob',
  PROJECT_SOURCE_MISMATCH: 'reload',
  UPLOAD_TOO_LARGE: 'chooseAnother',
  IMPORT_NOT_FOUND: 'goBack',
  SOURCE_ROOT_NOT_CONFIGURED: 'reviewServerConfig',
  SOURCE_DIRECTORY_NOT_FOUND: 'checkSource',
  SOURCE_IS_SYMLINK: 'checkSource',
  SOURCE_NOT_READABLE: 'checkSource',
  SOURCE_NOT_IMPORTED: 'uploadZip',
  INDEX_JOB_NOT_FOUND: 'goBack',
  JOB_NOT_ACTIVE: 'reload',
  SNAPSHOT_NOT_FOUND: 'goBack',
  SNAPSHOT_NOT_SEARCHABLE: 'reindex',
  SNAPSHOT_LEXICAL_MISMATCH: 'reindex',
  EMBEDDINGS_NOT_AVAILABLE: 'configureProvider',
  SNAPSHOT_EMBEDDING_MISMATCH: 'reindex',
  CONVERSATION_NOT_FOUND: 'goBack',
  ANSWER_NOT_FOUND: 'goBack',
  EVIDENCE_NOT_FOUND: 'goBack',
  GENERATION_NOT_AVAILABLE: 'configureProvider',
  SNAPSHOT_PRUNED: 'useEvidenceCopy',
  FILE_NOT_FOUND: 'goBack',
  FILE_NOT_INDEXED: 'chooseAnother',
  FORBIDDEN_HOST: 'openLocally',
  FORBIDDEN_ORIGIN: 'openLocally',
  EMBEDDING_PROVIDER_UNAVAILABLE: 'providerLimit',
  EMBEDDING_PROVIDER_UNAUTHORIZED: 'configureProvider',
  EMBEDDING_QUOTA_EXCEEDED: 'providerLimit',
  EMBEDDING_REQUEST_REJECTED: 'providerLimit',
  EMBEDDING_RATE_LIMITED: 'providerLimit',
};

export function recoveryFor(error: unknown): Recovery {
  if (!(error instanceof ApiError)) {
    return 'reload';
  }
  if (error.code) {
    return recoveryByCode[error.code];
  }
  return error.isNetworkError || error.status >= 500 ? 'retry' : 'reload';
}
