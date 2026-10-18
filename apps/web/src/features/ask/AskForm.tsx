import { SendHorizontal } from 'lucide-react';
import {
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from 'react';
import { Link } from 'react-router';
import { isApplePlatform, useShortcut } from '../../app/shortcuts';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { Kbd } from '../../components/ui/Kbd';
import { messages } from '../../messages';
import type { Providers } from '../import/api';
import { acknowledgePrivacy, hasAcknowledgedPrivacy, providerName } from '../import/privacy';
import type { NewQuestion, RetrievalMode } from './api';

const MAX_QUESTION_LENGTH = 2000;
const DEFAULT_MAX_EVIDENCE = 8;
const MODES: readonly RetrievalMode[] = ['HYBRID', 'LEXICAL', 'VECTOR'];

interface AskFormProps {
  questionId: string;
  projectId: string;
  conversationId: string | null;
  providers: Providers;
  vectorAvailable: boolean;
  reviewHref: string;
  draft: string;
  onDraftChange: (draft: string) => void;
  pending: boolean;
  onAsk: (question: NewQuestion) => Promise<unknown>;
}

export function AskForm({
  questionId,
  projectId,
  conversationId,
  providers,
  vectorAvailable,
  reviewHref,
  draft,
  onDraftChange,
  pending,
  onAsk,
}: AskFormProps) {
  const text = messages.ask.form;
  const ids = {
    hint: useId(),
    error: useId(),
    mode: useId(),
    modeHint: useId(),
    maxEvidence: useId(),
  };
  const textarea = useRef<HTMLTextAreaElement>(null);
  const [emptyError, setEmptyError] = useState(false);
  const [chosenMode, setChosenMode] = useState<RetrievalMode | null>(null);
  const [maxEvidence, setMaxEvidence] = useState(String(DEFAULT_MAX_EVIDENCE));
  // Remembered per project after the first question; from then on only a reminder (P4).
  const [remembered, setRemembered] = useState(() => hasAcknowledgedPrivacy(projectId, 'ask'));
  const [acknowledged, setAcknowledged] = useState(false);
  const remote = providers.generation.remote;
  const mode: RetrievalMode = vectorAvailable ? (chosenMode ?? 'HYBRID') : 'LEXICAL';
  const blocked = pending || (remote && !remembered && !acknowledged);
  useShortcut('focusQuestion', () => textarea.current?.focus());

  // Grows with the text up to the CSS max-height, then scrolls.
  useLayoutEffect(() => {
    const element = textarea.current;
    if (element) {
      element.style.height = 'auto';
      element.style.height = `${element.scrollHeight}px`;
    }
  }, [draft]);

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
    onDraftChange('');
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
    <form className="composer" onSubmit={onSubmit}>
      <PrivacyNotice
        providers={providers}
        mode={mode}
        reviewHref={reviewHref}
        remembered={remembered}
        acknowledged={acknowledged}
        onAcknowledge={setAcknowledged}
      />
      <div className="composer__box">
        <label htmlFor={questionId} className="visually-hidden">
          {text.label}
        </label>
        <textarea
          ref={textarea}
          id={questionId}
          className="composer__input"
          rows={2}
          maxLength={MAX_QUESTION_LENGTH}
          placeholder={text.placeholder}
          value={draft}
          aria-invalid={emptyError || undefined}
          aria-describedby={emptyError ? `${ids.error} ${ids.hint}` : ids.hint}
          onChange={(event) => {
            onDraftChange(event.target.value);
            setEmptyError(false);
          }}
          onKeyDown={onKeyDown}
        />
        <div className="composer__toolbar">
          <div className="composer__option">
            <label htmlFor={ids.mode}>{text.modeLegend}</label>
            <select
              id={ids.mode}
              value={mode}
              aria-describedby={vectorAvailable ? undefined : ids.modeHint}
              onChange={(event) => setChosenMode(event.target.value as RetrievalMode)}
            >
              {MODES.map((option) => (
                <option
                  key={option}
                  value={option}
                  disabled={option !== 'LEXICAL' && !vectorAvailable}
                >
                  {text.modeOptions[option]}
                </option>
              ))}
            </select>
          </div>
          <div className="composer__option">
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
          <span className="composer__send">
            <span className="composer__keys" aria-hidden="true">
              <Kbd>{isApplePlatform() ? '⌘' : 'Ctrl'}</Kbd>
              <Kbd>↵</Kbd>
            </span>
            <Button
              type="submit"
              variant="primary"
              icon={SendHorizontal}
              disabled={blocked}
              pending={pending}
            >
              {pending ? text.submitting : text.submit}
            </Button>
          </span>
        </div>
      </div>
      {emptyError && (
        <p id={ids.error} className="field-error">
          {text.empty}
        </p>
      )}
      <p id={ids.hint} className="field__hint muted">
        {text.hint(draft.length, MAX_QUESTION_LENGTH)}
      </p>
      {!vectorAvailable && (
        <p id={ids.modeHint} className="field__hint muted">
          {text.vectorUnavailable}
        </p>
      )}
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
    <Callout tone="warning">
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
    </Callout>
  );
}
