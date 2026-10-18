import { Check } from 'lucide-react';
import { messages } from '../../messages';

export type SourceType = 'LOCAL_FOLDER' | 'ZIP';
export type Step = 'source' | 'upload' | 'review' | 'index';

function stepsFor(sourceType: SourceType): Step[] {
  return sourceType === 'ZIP'
    ? ['source', 'upload', 'review', 'index']
    : ['source', 'review', 'index'];
}

export function StepIndicator({ sourceType, current }: { sourceType: SourceType; current: Step }) {
  const text = messages.newProject.steps;
  const steps = stepsFor(sourceType);
  const currentIndex = steps.indexOf(current);
  return (
    <ol className="steps" aria-label={text.label}>
      {steps.map((step, index) => {
        const state =
          index < currentIndex ? 'done' : index === currentIndex ? 'current' : 'pending';
        return (
          <li
            key={step}
            className={`steps__item steps__item--${state}`}
            aria-current={state === 'current' ? 'step' : undefined}
          >
            <span className="steps__marker" aria-hidden="true">
              {state === 'done' ? <Check className="steps__check" /> : index + 1}
            </span>
            {state === 'current' && (
              <span className="visually-hidden">{text.current(index + 1, steps.length)} </span>
            )}
            {step === 'source' && sourceType === 'LOCAL_FOLDER' ? text.sourceLocal : text[step]}
          </li>
        );
      })}
    </ol>
  );
}
