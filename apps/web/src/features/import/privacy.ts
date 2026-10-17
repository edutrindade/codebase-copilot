// Transparency, not a security control (P4): a cleared or blocked storage only shows the notice again.
const PREFIX = 'privacy-ack:';

export function hasAcknowledgedPrivacy(projectId: string): boolean {
  try {
    return window.localStorage.getItem(PREFIX + projectId) === 'true';
  } catch {
    return false;
  }
}

export function acknowledgePrivacy(projectId: string): void {
  try {
    window.localStorage.setItem(PREFIX + projectId, 'true');
  } catch {
    // Storage unavailable: the notice is shown again next time.
  }
}
