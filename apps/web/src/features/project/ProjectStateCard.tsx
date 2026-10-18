import { Link } from 'react-router';
import {
  CircleAlert,
  CircleSlash,
  FileArchive,
  Info,
  RefreshCw,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { Card } from '../../components/ui/Card';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { IndexPanel } from '../import/IndexPanel';
import type { Project } from '../workspaces/api';
import type { IndexJob } from './api';
import { IndexingProgress } from './IndexingProgress';
import { jobRecovery } from './jobRecovery';

interface ProjectStateCardProps {
  project: Project;
  latestJob: IndexJob | undefined;
  embeddingsEnabled: boolean;
  review: string;
  onCancel: (jobId: string) => Promise<unknown>;
}

// Only the most important state is shown; a ready, current project needs no card.
export function ProjectStateCard({
  project,
  latestJob,
  embeddingsEnabled,
  review,
  onCancel,
}: ProjectStateCardProps) {
  const text = messages.project;
  const { activeSnapshot, indexing } = project;
  if (indexing) {
    return (
      <IndexingProgress
        indexing={indexing}
        embeddingsEnabled={embeddingsEnabled}
        onCancel={() => onCancel(indexing.id)}
      />
    );
  }
  const indexAction = (
    <IndexPanel
      embedded
      projectId={project.id}
      title={messages.indexing.whatHappens}
      startLabel={activeSnapshot ? text.reindexTitle : text.indexTitle}
      reviewHref={review}
      ready
      blockedReason={null}
    />
  );

  if (latestJob?.status === 'FAILED') {
    const recovery = latestJob.error ? jobRecovery(latestJob.error.code) : 'retry';
    return (
      <StateCard tone="danger" icon={CircleAlert} title={text.failure.title} alert>
        {latestJob.error && <p>{latestJob.error.message}</p>}
        {latestJob.completedAt && (
          <p className="muted">{text.failure.at(formatDateTime(latestJob.completedAt))}</p>
        )}
        {activeSnapshot && <p>{text.failure.previousInUse}</p>}
        <p>{messages.recovery[recovery]}</p>
        {recovery === 'reviewExclusions' && (
          <p>
            <Link to={review}>{text.reviewPreAnalysis}</Link>
          </p>
        )}
        {indexAction}
      </StateCard>
    );
  }
  if (activeSnapshot && project.outdatedReasons.length > 0) {
    return (
      <StateCard tone="warning" icon={RefreshCw} title={text.outdated.title}>
        <ul>
          {project.outdatedReasons.map((reason) => (
            <li key={reason}>{text.outdated.reasons[reason]}</li>
          ))}
        </ul>
        <p>{text.outdated.action}</p>
        {indexAction}
      </StateCard>
    );
  }
  if (!activeSnapshot && project.source.type === 'ZIP' && !project.source.currentImport) {
    return (
      <StateCard tone="info" icon={FileArchive} title={text.awaitingUploadTitle}>
        <p>{text.awaitingUpload}</p>
        <p>
          <Link to={review}>{text.sendZip}</Link>
        </p>
      </StateCard>
    );
  }
  const cancelled = latestJob?.status === 'CANCELLED' ? latestJob : null;
  const cancelledNote = cancelled && (
    <>
      {cancelled.completedAt && (
        <p className="muted">{text.cancelled.at(formatDateTime(cancelled.completedAt))}</p>
      )}
      {activeSnapshot && <p>{text.cancelled.previousInUse}</p>}
    </>
  );
  if (!activeSnapshot) {
    return (
      <StateCard tone="info" icon={Info} title={text.notIndexedTitle}>
        <p>{text.notIndexed}</p>
        {cancelled && <p>{text.cancelled.title}</p>}
        {cancelledNote}
        <p>
          <Link to={review}>{text.reviewPreAnalysis}</Link>
        </p>
        {indexAction}
      </StateCard>
    );
  }
  if (cancelled) {
    return (
      <StateCard tone="neutral" icon={CircleSlash} title={text.cancelled.title}>
        {cancelledNote}
        {indexAction}
      </StateCard>
    );
  }
  return null;
}

interface StateCardProps {
  tone: 'danger' | 'warning' | 'info' | 'neutral';
  icon: LucideIcon;
  title: string;
  // Failures are announced when they appear; the other states are read in order.
  alert?: boolean;
  children: ReactNode;
}

function StateCard({ tone, icon: Icon, title, alert, children }: StateCardProps) {
  return (
    <Card
      as="section"
      className={`state-card state-card--${tone} stack`}
      role={alert ? 'alert' : undefined}
      aria-labelledby="state-card-title"
    >
      <div className="state-card__header">
        <Icon aria-hidden="true" className="state-card__icon" />
        <h2 id="state-card-title">{title}</h2>
      </div>
      {children}
    </Card>
  );
}
