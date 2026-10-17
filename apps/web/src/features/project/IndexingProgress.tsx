import { CircleStop } from 'lucide-react';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Callout } from '../../components/ui/Callout';
import { Card } from '../../components/ui/Card';
import { Progress } from '../../components/ui/Progress';
import { messages } from '../../messages';
import type { Indexing } from './api';

type Stage = NonNullable<Indexing['stage']>;

const STAGES: Stage[] = ['SCANNING', 'PROCESSING_FILES', 'LEXICAL', 'EMBEDDING', 'FINALIZING'];

interface IndexingProgressProps {
  indexing: Indexing;
  // With provider=none the EMBEDDING stage is skipped (06 §7).
  embeddingsEnabled: boolean;
  onCancel: () => Promise<unknown>;
}

export function IndexingProgress({ indexing, embeddingsEnabled, onCancel }: IndexingProgressProps) {
  const text = messages.project.progress;
  const stages = STAGES.filter((stage) => embeddingsEnabled || stage !== 'EMBEDDING');
  const current = indexing.stage === null ? -1 : stages.indexOf(indexing.stage);

  return (
    <Card as="section" className="stack" aria-labelledby="indexing-title">
      <div className="page-header">
        <h2 id="indexing-title">{text.title}</h2>
        {!indexing.cancelRequested && (
          <ConfirmDialog
            trigger={text.cancel}
            triggerLabel={text.cancelLabel}
            triggerIcon={CircleStop}
            title={text.cancelTitle}
            description={<p>{text.cancelDetail}</p>}
            confirmLabel={text.cancelConfirm}
            dismissLabel={text.cancelDismiss}
            workingLabel={text.cancelWorking}
            onConfirm={onCancel}
          />
        )}
      </div>
      {indexing.cancelRequested && (
        <Callout tone="neutral">
          <p>{text.cancelRequested}</p>
        </Callout>
      )}
      {indexing.stage === null && <p>{text.queued}</p>}
      <ol className="stage-list" aria-label={text.stagesLabel}>
        {stages.map((stage, index) => {
          const state = index < current ? 'done' : index === current ? 'current' : 'pending';
          return (
            <li
              key={stage}
              className={`stage-list__item stage-list__item--${state}`}
              aria-current={state === 'current' ? 'step' : undefined}
            >
              <span>{text.stages[stage]}</span>
              <span className="muted"> — {text.state[state]}</span>
              {state === 'current' && <StageProgress indexing={indexing} />}
            </li>
          );
        })}
      </ol>
    </Card>
  );
}

function StageProgress({ indexing }: { indexing: Indexing }) {
  const { progress, stage } = indexing;
  if (!progress || progress.total === 0 || stage === 'FINALIZING') {
    return null;
  }
  const counter =
    stage === 'LEXICAL' || stage === 'EMBEDDING'
      ? messages.project.progress.chunks(progress.completed, progress.total)
      : messages.project.progress.files(progress.completed, progress.total);
  return (
    <div className="stage-list__progress">
      <Progress value={progress.completed} max={progress.total} label={counter} />
      <span className="tabular">{counter}</span>
    </div>
  );
}
