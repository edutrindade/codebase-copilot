import { describe, expect, test } from 'vitest';
import { jobRecovery } from './jobRecovery';

describe('jobRecovery', () => {
  test.each([
    ['SOURCE_DIRECTORY_NOT_FOUND', 'checkSource'],
    ['SOURCE_NOT_IMPORTED', 'uploadZip'],
    ['NO_INCLUDED_FILES', 'reviewExclusions'],
    ['EMBEDDING_PROVIDER_UNAUTHORIZED', 'configureProvider'],
    ['EMBEDDING_QUOTA_EXCEEDED', 'providerLimit'],
    ['INDEX_IO_ERROR', 'retry'],
    ['INDEX_ATTEMPTS_EXHAUSTED', 'retry'],
    ['SOMETHING_NEW', 'retry'],
  ])('%s → %s', (code, recovery) => {
    expect(jobRecovery(code)).toBe(recovery);
  });
});
