import { useId, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import { messages } from '../../messages';
import type { Providers } from '../import/api';
import { acknowledgePrivacy, hasAcknowledgedPrivacy, providerName } from '../import/privacy';
import type { NewQuestion, RetrievalMode } from './api';

const MAX_QUESTION_LENGTH = 2000;
const DEFAULT_MAX_EVIDENCE = 8;
const MODES: readonly RetrievalMode[] = ['HYBRID', 'LEXICAL', 'VECTOR'];

interface AskFormProps {
  projectId: string;
  conversationId: string | null;
  providers: Providers;
  vectorAvailable: boolean;
  reviewHref: string;
  showSuggestions: boolean;
  pending: boolean;
  onAsk: (question: NewQuestion) => Promise<unknown>;
}

export function AskForm({
  projectId,
  conversationId,
  providers,
  vectorAvailable,
  reviewHref,
  showSuggestions,
  pending,
  onAsk,
}: AskFormProps) {
  const text = messages.ask.form;
  const ids = { question: useId(), hint: useId(), error: useId(), maxEvidence: useId() };
  const [draft, setDraft] = useState('');
  const [emptyError, setEmptyError] = useState(false);
  const [chosenMode, setChosenMode] = useState<RetrievalMode | null>(null);
  const [maxEvidence, setMaxEvidence] = useState(String(DEFAULT_MAX_EVIDENCE));
  // Remembered per project after the first question; from then on only a reminder (P4).
  const [remembered, setRemembered] = useState(() => hasAcknowledgedPrivacy(projectId, 'ask'));
  const [acknowledged, setAcknowledged] = useState(false);
  const remote = providers.generation.remote;
  const mode: RetrievalMode = vectorAvailable ? (chosenMode ?? 'HYBRID') : 'LEXICAL';
  const blocked = pending || (remote && !remembered && !acknowledged);

  async function submit() {
    const question = draft.trim();
    if (question === '') {
      setEmptyError(true);
      return;
    }
    if (blocked) {
      return;
    }
    try {
      await onAsk({
        question,
        conversationId,
        retrievalMode: mode,
        maxEvidence: Number(maxEvidence) || DEFAULT_MAX_EVIDENCE,
      });
    } catch {
      // Shown by the page from the mutation state; the draft stays for another try.
      return;
    }
    setDraft('');
    if (remote && !remembered) {
      acknowledgePrivacy(projectId, 'ask');
      setRemembered(true);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submit();
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      void submit();
    }
  }

  return (
    <form className="stack ask-form" onSubmit={onSubmit}>
      {showSuggestions && (
        <section className="stack" aria-labelledby="suggestions-title">
          <h3 id="suggestions-title">{messages.ask.suggestionsTitle}</h3>
          <ul className="actions suggestions">
            {messages.ask.suggestions.map((suggestion) => (
              <li key={suggestion}>
                <button
                  type="button"
                  className="button"
                  onClick={() => {
                    setDraft(suggestion);
                    setEmptyError(false);
                    document.getElementById(ids.question)?.focus();
                  }}
                >
                  {suggestion}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
      <div className="field field--wide">
        <label htmlFor={ids.question}>{text.label}</label>
        <textarea
          id={ids.question}
          rows={3}
          maxLength={MAX_QUESTION_LENGTH}
          value={draft}
          aria-invalid={emptyError || undefined}
          aria-describedby={emptyError ? `${ids.error} ${ids.hint}` : ids.hint}
          onChange={(event) => {
            setDraft(event.target.value);
            setEmptyError(false);
          }}
          onKeyDown={onKeyDown}
        />
        {emptyError && (
          <p id={ids.error} className="field-error">
            {text.empty}
          </p>
        )}
        <p id={ids.hint} className="field__hint muted">
          {text.hint(draft.length, MAX_QUESTION_LENGTH)}
        </p>
      </div>
      <PrivacyNotice
        providers={providers}
        mode={mode}
        reviewHref={reviewHref}
        remembered={remembered}
        acknowledged={acknowledged}
        onAcknowledge={setAcknowledged}
      />
      <details className="advanced">
        <summary>{text.advanced}</summary>
        <div className="stack">
          <fieldset className="choice">
            <legend>{text.modeLegend}</legend>
            {MODES.map((option) => (
              <label key={option} className="choice__option">
                <input
                  type="radio"
                  name="retrievalMode"
                  value={option}
                  checked={mode === option}
                  disabled={option !== 'LEXICAL' && !vectorAvailable}
                  onChange={() => setChosenMode(option)}
                />
                {text.modes[option]}
              </label>
            ))}
            {!vectorAvailable && <p className="field__hint muted">{text.vectorUnavailable}</p>}
          </fieldset>
          <div className="field">
            <label htmlFor={ids.maxEvidence}>{text.maxEvidenceLabel}</label>
            <input
              id={ids.maxEvidence}
              type="number"
              min={1}
              max={20}
              step={1}
              required
              value={maxEvidence}
              onChange={(event) => setMaxEvidence(event.target.value)}
            />
          </div>
        </div>
      </details>
      <div>
        <button type="submit" className="button button--primary" disabled={blocked}>
          {pending ? text.submitting : text.submit}
        </button>
      </div>
    </form>
  );
}

interface PrivacyNoticeProps {
  providers: Providers;
  mode: RetrievalMode;
  reviewHref: string;
  remembered: boolean;
  acknowledged: boolean;
  onAcknowledge: (value: boolean) => void;
}

function PrivacyNotice({
  providers,
  mode,
  reviewHref,
  remembered,
  acknowledged,
  onAcknowledge,
}: PrivacyNoticeProps) {
  const text = messages.ask.privacy;
  const { generation, embeddings } = providers;
  const embeddingsNotice =
    embeddings.remote && mode !== 'LEXICAL'
      ? text.remoteEmbeddings(providerName(embeddings.provider))
      : null;
  if (!generation.remote) {
    return embeddingsNotice && <p className="muted">{embeddingsNotice}</p>;
  }
  const name = providerName(generation.provider);
  if (remembered) {
    return (
      <p className="muted">
        {text.reminder(name)}
        {embeddingsNotice && ` ${embeddingsNotice}`}
      </p>
    );
  }
  return (
    <div className="notice notice--warning stack">
      <p>{text.remote(name, generation.model)}</p>
      {embeddingsNotice && <p>{embeddingsNotice}</p>}
      <p>
        <Link to={reviewHref}>{messages.privacy.review}</Link>
      </p>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(event) => onAcknowledge(event.target.checked)}
        />
        {text.acknowledge}
      </label>
    </div>
  );
}
