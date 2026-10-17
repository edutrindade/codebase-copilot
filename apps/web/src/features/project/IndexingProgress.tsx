import { messages } from '../../messages';
import type { Indexing } from './api';

type Stage = NonNullable<Indexing['stage']>;

const STAGES: Stage[] = ['SCANNING', 'PROCESSING_FILES', 'LEXICAL', 'EMBEDDING', 'FINALIZING'];

interface IndexingProgressProps {
  indexing: Indexing;
  // With provider=none the EMBEDDING stage is skipped (06 §7).
  embeddingsEnabled: boolean;
}

export function IndexingProgress({ indexing, embeddingsEnabled }: IndexingProgressProps) {
  const text = messages.project.progress;
  const stages = STAGES.filter((stage) => embeddingsEnabled || stage !== 'EMBEDDING');
  const current = indexing.stage === null ? -1 : stages.indexOf(indexing.stage);

  return (
    <section className="stack panel" aria-labelledby="indexing-title">
      <h2 id="indexing-title">{text.title}</h2>
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
    </section>
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
      <progress value={progress.completed} max={progress.total} aria-label={counter} />
      <span>{counter}</span>
    </div>
  );
}
