import { formatCost, formatDuration, formatNumber } from '../../format';
import { messages } from '../../messages';
import type { Answer, Evidence } from './api';
import { answerRecovery } from './answerRecovery';
import { answerSegments } from './answerText';
import { highlightedRange } from './evidence';

interface AnswerTurnProps {
  answer: Answer;
  selected: boolean;
  olderSnapshot: boolean;
  onCite: (answer: Answer, evidence: Evidence) => void;
  onShowEvidence: (answer: Answer) => void;
}

export function AnswerTurn({
  answer,
  selected,
  olderSnapshot,
  onCite,
  onShowEvidence,
}: AnswerTurnProps) {
  const text = messages.ask;
  return (
    <li className={selected ? 'turn turn--selected' : 'turn'}>
      <Question question={answer.question} />
      <div className="turn__answer stack">
        <p className="turn__meta">
          <span className={`status status--answer-${answer.status.toLowerCase()}`}>
            {text.status[answer.status]}
          </span>
          {olderSnapshot && <span className="muted"> {text.olderSnapshot}</span>}
        </p>
        <AnswerBody answer={answer} onCite={onCite} />
        {answer.warnings.length > 0 && (
          <ul className="notice-list">
            {answer.warnings.map((warning) => (
              <li key={warning.code} className="notice notice--warning">
                <p>{warning.message}</p>
                {text.warningHints[warning.code] && <p>{text.warningHints[warning.code]}</p>}
              </li>
            ))}
          </ul>
        )}
        {answer.evidence.length > 0 && (
          <div>
            <button
              type="button"
              className="link-button"
              aria-pressed={selected}
              onClick={() => onShowEvidence(answer)}
            >
              {text.showEvidence}
            </button>
          </div>
        )}
        {answer.status !== 'PENDING' && <AnswerMetrics answer={answer} />}
      </div>
    </li>
  );
}

export function Question({ question }: { question: string }) {
  return (
    <div className="turn__question">
      <p className="visually-hidden">{messages.ask.question}</p>
      <p className="turn__text">{question}</p>
    </div>
  );
}

function AnswerBody({
  answer,
  onCite,
}: {
  answer: Answer;
  onCite: (answer: Answer, evidence: Evidence) => void;
}) {
  const text = messages.ask;
  if (answer.status === 'PENDING') {
    return <p className="muted">{text.pending}</p>;
  }
  if (answer.status === 'FAILED') {
    return (
      <div className="problem-alert">
        <p className="problem-alert__title">{text.failed}</p>
        {answer.error && <p>{answer.error.message}</p>}
        <p>{messages.recovery[answer.error ? answerRecovery(answer.error.code) : 'askAgain']}</p>
      </div>
    );
  }
  if (answer.answer === null) {
    return null;
  }
  const byLabel = new Map(answer.evidence.map((evidence) => [evidence.citationLabel, evidence]));
  return (
    <p className="turn__text">
      <span className="visually-hidden">{text.answer}: </span>
      {answerSegments(answer.answer, new Set(byLabel.keys())).map((segment, index) => {
        if (segment.type === 'text') {
          return segment.text;
        }
        const evidence = byLabel.get(segment.label);
        if (!evidence) {
          return null;
        }
        const range = highlightedRange(evidence);
        return (
          <button
            key={`${segment.label}-${index}`}
            type="button"
            className="citation"
            aria-label={text.citation(
              segment.label,
              evidence.path,
              text.lines(range.start, range.end),
            )}
            onClick={() => onCite(answer, evidence)}
          >
            {segment.label}
          </button>
        );
      })}
    </p>
  );
}

function AnswerMetrics({ answer }: { answer: Answer }) {
  const text = messages.ask.metrics;
  const { metrics, model, retrieval, evidence } = answer;
  const duration = (ms: number | null) => (ms === null ? text.unavailable : formatDuration(ms));
  const cited = evidence.filter((item) => item.validationStatus === 'CITED').length;
  const tokens =
    metrics.inputTokens !== null && metrics.outputTokens !== null
      ? text.tokensValue(formatNumber(metrics.inputTokens), formatNumber(metrics.outputTokens))
      : text.unavailable;
  const cost =
    metrics.costStatus === 'ESTIMATED' &&
    metrics.estimatedCost !== null &&
    metrics.costCurrency !== null
      ? text.costEstimated(formatCost(metrics.estimatedCost, metrics.costCurrency))
      : text.costUnknown;

  return (
    <details className="metrics">
      <summary>{text.title}</summary>
      <dl className="metrics__list">
        <div>
          <dt>{text.duration}</dt>
          <dd>{duration(metrics.latencyMs)}</dd>
        </div>
        <div>
          <dt>{text.retrieval}</dt>
          <dd>{duration(metrics.retrievalMs)}</dd>
        </div>
        <div>
          <dt>{text.generation}</dt>
          <dd>{duration(metrics.generationMs)}</dd>
        </div>
        <div>
          <dt>{text.evidence}</dt>
          <dd>{text.evidenceCount(evidence.length, cited)}</dd>
        </div>
        <div>
          <dt>{text.tokens}</dt>
          <dd>{tokens}</dd>
        </div>
        <div>
          <dt>{text.cost}</dt>
          <dd>{cost}</dd>
        </div>
        <div>
          <dt>{text.model}</dt>
          <dd>{model ? `${model.provider}, ${model.name}` : text.noModel}</dd>
        </div>
        <div>
          <dt>{text.mode}</dt>
          <dd>{messages.ask.form.modes[retrieval.mode]}</dd>
        </div>
      </dl>
    </details>
  );
}
