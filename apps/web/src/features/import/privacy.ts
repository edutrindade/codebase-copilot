import type { Providers } from './api';

// Transparency, not a security control (P4): a cleared or blocked storage only shows the notice again.
const PREFIX = 'privacy-ack:';

// Indexing sends chunks to the embeddings provider; asking sends the question and evidence to generation.
export type PrivacyScope = 'index' | 'ask';

const PROVIDER_NAMES: Record<Providers['embeddings']['provider'], string> = {
  openai: 'OpenAI',
  fake: 'fake',
  none: 'none',
};

export function providerName(provider: Providers['embeddings']['provider']): string {
  return PROVIDER_NAMES[provider];
}

function key(projectId: string, scope: PrivacyScope): string {
  return scope === 'index' ? PREFIX + projectId : `${PREFIX}${scope}:${projectId}`;
}

export function hasAcknowledgedPrivacy(projectId: string, scope: PrivacyScope = 'index'): boolean {
  try {
    return window.localStorage.getItem(key(projectId, scope)) === 'true';
  } catch {
    return false;
  }
}

export function acknowledgePrivacy(projectId: string, scope: PrivacyScope = 'index'): void {
  try {
    window.localStorage.setItem(key(projectId, scope), 'true');
  } catch {
    // Storage unavailable: the notice is shown again next time.
  }
}
