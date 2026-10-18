import { useQuery, useQueryClient } from '@tanstack/react-query';
import { MessageSquare } from 'lucide-react';
import { Link } from 'react-router';
import { usePageTitle } from '../../app/title';
import { ProblemAlert } from '../../components/ProblemAlert';
import { messages } from '../../messages';
import { getProviders, importKeys } from '../import/api';
import { askDraftState } from '../ask/draft';
import type { Project } from '../workspaces/api';
import { ActiveSnapshot } from './ActiveSnapshot';
import { cancelIndexJob, getLatestIndexJob, projectKeys } from './api';
import { useProject } from './ProjectLayout';
import { ProjectStateCard } from './ProjectStateCard';
import { SnapshotList } from './SnapshotList';
import { UnindexedFiles } from './UnindexedFiles';
import { UsagePanel } from './UsagePanel';

export function ProjectPage() {
  const project = useProject();
  usePageTitle([messages.pages.overview, project.name]);
  return <ProjectSummary project={project} />;
}

function ProjectSummary({ project }: { project: Project }) {
  const text = messages.project;
  const queryClient = useQueryClient();
  const { activeSnapshot } = project;
  const review = `/workspaces/${project.workspaceId}/new?project=${project.id}`;
  const latestJob = useQuery({
    queryKey: projectKeys.latestJob(project.id),
    queryFn: ({ signal }) => getLatestIndexJob(project.id, signal),
  });
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  const stateCard = (
    <ProjectStateCard
      project={project}
      latestJob={latestJob.data ?? undefined}
      embeddingsEnabled={providers.data?.embeddings.provider !== 'none'}
      review={review}
      onCancel={cancel}
    />
  );
  // Re-indexing a ready project lives on the snapshot card, so the state card is never duplicated.
  const ready =
    activeSnapshot !== null &&
    !project.indexing &&
    project.outdatedReasons.length === 0 &&
    latestJob.isSuccess &&
    latestJob.data?.status !== 'FAILED' &&
    latestJob.data?.status !== 'CANCELLED';

  async function cancel(jobId: string) {
    await cancelIndexJob(jobId);
    await queryClient.invalidateQueries({ queryKey: importKeys.project(project.id) });
  }

  return (
    <section className="overview">
      {/* The active tab already shows the name of this section. */}
      <h1 className="visually-hidden">{messages.pages.overview}</h1>
      {latestJob.isError && (
        <ProblemAlert error={latestJob.error} onRetry={() => void latestJob.refetch()} />
      )}
      {stateCard}
      <div className="overview__grid">
        <div className="overview__main stack">
          {activeSnapshot && <StartHere projectId={project.id} />}
          {activeSnapshot &&
            activeSnapshot.counts.failedFiles + activeSnapshot.counts.unsupportedFiles > 0 && (
              <UnindexedFiles
                snapshotId={activeSnapshot.id}
                failedFiles={activeSnapshot.counts.failedFiles}
                unsupportedFiles={activeSnapshot.counts.unsupportedFiles}
              />
            )}
          <UsagePanel projectId={project.id} />
        </div>
        <div className="overview__side stack">
          {activeSnapshot && (
            <ActiveSnapshot snapshot={activeSnapshot} project={ready ? project : undefined} />
          )}
          {activeSnapshot && project.source.type === 'LOCAL_FOLDER' && (
            <p className="muted">{text.diskChanges}</p>
          )}
          <SnapshotList projectId={project.id} />
        </div>
      </div>
    </section>
  );
}

function StartHere({ projectId }: { projectId: string }) {
  const text = messages.project.startHere;
  return (
    <section className="stack" aria-labelledby="start-here-title">
      <h2 id="start-here-title">{text.title}</h2>
      <p className="muted">{text.detail}</p>
      <ul className="suggestion-grid">
        {messages.ask.suggestions.map((suggestion) => (
          <li key={suggestion}>
            <Link
              className="suggestion-card"
              to={`/projects/${projectId}/ask`}
              state={askDraftState(suggestion)}
              viewTransition
            >
              <MessageSquare aria-hidden="true" className="suggestion-card__icon" />
              {suggestion}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
