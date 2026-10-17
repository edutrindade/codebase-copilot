import { messages } from '../../messages';

export type SourceType = 'LOCAL_FOLDER' | 'ZIP';
export type Step = 'source' | 'upload' | 'review';

function stepsFor(sourceType: SourceType): Step[] {
  return sourceType === 'ZIP' ? ['source', 'upload', 'review'] : ['source', 'review'];
}

export function StepIndicator({ sourceType, current }: { sourceType: SourceType; current: Step }) {
  const steps = stepsFor(sourceType);
  return (
    <ol className="steps" aria-label={messages.newProject.steps.label}>
      {steps.map((step) => (
        <li
          key={step}
          className={step === current ? 'steps__item steps__item--current' : 'steps__item'}
          aria-current={step === current ? 'step' : undefined}
        >
          {messages.newProject.steps[step]}
        </li>
      ))}
    </ol>
  );
}
