// A suggested question travels in the history state, so it fills the field without being sent.
interface AskDraftState {
  askDraft: string;
}

export function askDraftState(question: string): AskDraftState {
  return { askDraft: question };
}

export function draftFromState(state: unknown): string {
  return typeof state === 'object' &&
    state !== null &&
    'askDraft' in state &&
    typeof state.askDraft === 'string'
    ? state.askDraft
    : '';
}
