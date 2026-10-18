import { Info } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { CopyButton } from '../../components/ui/CopyButton';
import { Popover } from '../../components/ui/Popover';
import { StatusBadge, type Tone } from '../../components/ui/StatusBadge';
import { formatCost, formatDuration, formatNumber } from '../../format';
import { messages } from '../../messages';
import type { Answer, Evidence } from './api';
import { AnswerContent } from './AnswerContent';
import { answerRecovery } from './answerRecovery';

const STATUS_TONE: Record<Answer['status'], Tone> = {
  PENDING: 'progress',
  ANSWERED: 'success',
  PARTIAL: 'warning',
  ABSTAINED: 'warning',
  FAILED: 'danger',
};

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
  const done = answer.status !== 'PENDING';
  return (
    <li className={selected ? 'turn turn--selected' : 'turn'}>
      <Question question={answer.question} />
      <article className="answer-card" aria-label={text.answer}>
        <p className="answer-card__meta">
          <StatusBadge tone={STATUS_TONE[answer.status]}>{text.status[answer.status]}</StatusBadge>
          {olderSnapshot && <span className="muted">{text.olderSnapshot}</span>}
        </p>
        <AnswerBody answer={answer} onCite={onCite} />
        {answer.warnings.length > 0 && (
          <ul className="notice-list">
            {answer.warnings.map((warning) => (
              <Callout as="li" key={warning.code} tone="warning">
                <p>{warning.message}</p>
                {text.warningHints[warning.code] && <p>{text.warningHints[warning.code]}</p>}
              </Callout>
            ))}
          </ul>
        )}
        {(done || answer.evidence.length > 0) && (
          <div className="answer-card__footer">
            {done && <p className="answer-card__summary">{metricsSummary(answer)}</p>}
            <div className="answer-card__actions">
              {answer.evidence.length > 0 && (
                <Button
                  size="sm"
                  variant="ghost"
                  aria-pressed={selected}
                  onClick={() => onShowEvidence(answer)}
                >
                  {text.showEvidence}
                </Button>
              )}
              {done && <AnswerMetrics answer={answer} />}
              {answer.answer && <CopyButton text={answer.answer} label={text.copyAnswer} />}
            </div>
          </div>
        )}
      </article>
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
      <Callout tone="danger" title={text.failed}>
        {answer.error && <p>{answer.error.message}</p>}
        <p>{messages.recovery[answer.error ? answerRecovery(answer.error.code) : 'askAgain']}</p>
      </Callout>
    );
  }
  if (answer.answer === null) {
    return null;
  }
  return (
    <AnswerContent
      text={answer.answer}
      evidence={answer.evidence}
      onCite={(evidence) => onCite(answer, evidence)}
    />
  );
}

function costText(metrics: Answer['metrics']): string | null {
  return metrics.costStatus === 'ESTIMATED' &&
    metrics.estimatedCost !== null &&
    metrics.costCurrency !== null
    ? formatCost(metrics.estimatedCost, metrics.costCurrency)
    : null;
}

// "1,2 s · 6 evidências · US$ 0,0004 estimado": only what was measured, never a made-up value.
function metricsSummary(answer: Answer): string {
  const text = messages.ask.metrics;
  const cost = costText(answer.metrics);
  return [
    answer.metrics.latencyMs !== null && formatDuration(answer.metrics.latencyMs),
    text.evidenceShort(answer.evidence.length),
    cost !== null && text.costShort(cost),
  ]
    .filter((part): part is string => typeof part === 'string')
    .join(' · ');
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
  const estimated = costText(metrics);
  const cost = estimated !== null ? text.costEstimated(estimated) : text.costUnknown;

  return (
    <Popover
      label={text.title}
      trigger={
        <Button size="sm" variant="ghost" icon={Info}>
          {text.title}
        </Button>
      }
    >
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
    </Popover>
  );
}
