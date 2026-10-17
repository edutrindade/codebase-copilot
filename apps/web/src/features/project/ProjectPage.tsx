import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link, useParams } from 'react-router';
import { MessageSquareText, MessagesSquare } from 'lucide-react';
import { ApiError } from '../../api/errors';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { getProject, getProviders, importKeys } from '../import/api';
import { IndexPanel } from '../import/IndexPanel';
import { workspaceKeys, type Project } from '../workspaces/api';
import { PROJECT_STATUS_TONE, projectSource, projectStatus } from '../workspaces/projectStatus';
import { ActiveSnapshot } from './ActiveSnapshot';
import { cancelIndexJob, getLatestIndexJob, projectKeys, type IndexJob } from './api';
import { IndexingProgress } from './IndexingProgress';
import { jobRecovery } from './jobRecovery';
import { SnapshotList } from './SnapshotList';
import { UnindexedFiles } from './UnindexedFiles';
import { UsagePanel } from './UsagePanel';

const ACTIVE_JOB_POLL_MS = 1000;

export function ProjectPage() {
  const { projectId = '' } = useParams();
  const project = useQuery({
    queryKey: importKeys.project(projectId),
    queryFn: ({ signal }) => getProject(projectId, signal),
    refetchInterval: (query) => (query.state.data?.indexing ? ACTIVE_JOB_POLL_MS : false),
  });
  useRefreshWhenJobChanges(projectId, project.data?.indexing?.id ?? null);

  if (project.isPending) {
    return (
      <section>
        <h1>{messages.pages.project}</h1>
        <Loading />
      </section>
    );
  }
  if (project.isError) {
    const notFound =
      project.error instanceof ApiError && project.error.code === 'PROJECT_NOT_FOUND';
    return (
      <section className="stack">
        <Link to="/" className="back-link">
          {messages.projects.allWorkspaces}
        </Link>
        <h1>{messages.pages.project}</h1>
        <ProblemAlert
          error={project.error}
          onRetry={notFound ? undefined : () => void project.refetch()}
        />
      </section>
    );
  }
  return <ProjectSummary project={project.data} />;
}

// Snapshots, jobs and counts only change when a job starts or ends.
function useRefreshWhenJobChanges(projectId: string, indexingId: string | null) {
  const queryClient = useQueryClient();
  const previous = useRef(indexingId);
  useEffect(() => {
    if (previous.current === indexingId) {
      return;
    }
    previous.current = indexingId;
    void queryClient.invalidateQueries({ queryKey: importKeys.project(projectId) });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }, [indexingId, projectId, queryClient]);
}

function ProjectSummary({ project }: { project: Project }) {
  const text = messages.project;
  const queryClient = useQueryClient();
  const status = projectStatus(project);
  const { activeSnapshot, indexing } = project;
  const review = `/workspaces/${project.workspaceId}/new?project=${project.id}`;
  const awaitingUpload = status === 'AWAITING_UPLOAD';
  const latestJob = useQuery({
    queryKey: projectKeys.latestJob(project.id),
    queryFn: ({ signal }) => getLatestIndexJob(project.id, signal),
  });
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  const failedJob = !indexing && latestJob.data?.status === 'FAILED' ? latestJob.data : null;
  const cancelledJob = !indexing && latestJob.data?.status === 'CANCELLED' ? latestJob.data : null;

  async function cancel(jobId: string) {
    await cancelIndexJob(jobId);
    await queryClient.invalidateQueries({ queryKey: importKeys.project(project.id) });
  }

  return (
    <section className="stack">
      <PageHeader
        back={
          <Link to={`/workspaces/${project.workspaceId}`} className="back-link">
            {text.backToWorkspace}
          </Link>
        }
        title={project.name}
        description={
          <>
            <span>{projectSource(project)}</span>
            <StatusBadge tone={PROJECT_STATUS_TONE[status]}>
              {messages.projects.status[status]}
            </StatusBadge>
          </>
        }
        actions={
          <>
            <ButtonLink to={`/projects/${project.id}/history`} icon={MessagesSquare}>
              {text.history}
            </ButtonLink>
            {activeSnapshot && (
              <ButtonLink
                to={`/projects/${project.id}/ask`}
                variant="primary"
                icon={MessageSquareText}
              >
                {text.ask}
              </ButtonLink>
            )}
          </>
        }
      />
      {awaitingUpload && (
        <Callout tone="info">
          <p>{text.awaitingUpload}</p>
          <p>
            <Link to={review}>{text.sendZip}</Link>
          </p>
        </Callout>
      )}
      {indexing && (
        <IndexingProgress
          indexing={indexing}
          embeddingsEnabled={providers.data?.embeddings.provider !== 'none'}
          onCancel={() => cancel(indexing.id)}
        />
      )}
      {latestJob.isError && (
        <ProblemAlert error={latestJob.error} onRetry={() => void latestJob.refetch()} />
      )}
      {failedJob && (
        <JobFailure job={failedJob} hasActiveSnapshot={activeSnapshot !== null} review={review} />
      )}
      {cancelledJob && (
        <Callout tone="neutral" title={text.cancelled.title}>
          {cancelledJob.completedAt && (
            <p className="muted">{text.cancelled.at(formatDateTime(cancelledJob.completedAt))}</p>
          )}
          {activeSnapshot && <p>{text.cancelled.previousInUse}</p>}
        </Callout>
      )}
      {!activeSnapshot && !indexing && !awaitingUpload && (
        <Callout tone="info">
          <p>{text.notIndexed}</p>
          <p>
            <Link to={review}>{text.reviewPreAnalysis}</Link>
          </p>
        </Callout>
      )}
      {activeSnapshot && project.outdatedReasons.length > 0 && (
        <Callout as="section" tone="warning" aria-labelledby="outdated-title">
          <h2 id="outdated-title">{text.outdated.title}</h2>
          <ul>
            {project.outdatedReasons.map((reason) => (
              <li key={reason}>{text.outdated.reasons[reason]}</li>
            ))}
          </ul>
          <p>{text.outdated.action}</p>
        </Callout>
      )}
      {!indexing && !awaitingUpload && (
        <IndexPanel
          projectId={project.id}
          title={activeSnapshot ? text.reindexTitle : text.indexTitle}
          startLabel={activeSnapshot ? text.reindexTitle : text.indexTitle}
          reviewHref={review}
          ready
          blockedReason={null}
        />
      )}
      {activeSnapshot && project.source.type === 'LOCAL_FOLDER' && (
        <p className="muted">{text.diskChanges}</p>
      )}
      {activeSnapshot && <ActiveSnapshot snapshot={activeSnapshot} />}
      {activeSnapshot &&
        activeSnapshot.counts.failedFiles + activeSnapshot.counts.unsupportedFiles > 0 && (
          <UnindexedFiles
            snapshotId={activeSnapshot.id}
            failedFiles={activeSnapshot.counts.failedFiles}
            unsupportedFiles={activeSnapshot.counts.unsupportedFiles}
          />
        )}
      <SnapshotList projectId={project.id} />
      <UsagePanel projectId={project.id} />
    </section>
  );
}

interface JobFailureProps {
  job: IndexJob;
  hasActiveSnapshot: boolean;
  review: string;
}

function JobFailure({ job, hasActiveSnapshot, review }: JobFailureProps) {
  const text = messages.project.failure;
  const recovery = job.error ? jobRecovery(job.error.code) : 'retry';
  return (
    <Callout role="alert" tone="danger" title={text.title}>
      {job.error && <p>{job.error.message}</p>}
      {job.completedAt && <p className="muted">{text.at(formatDateTime(job.completedAt))}</p>}
      {hasActiveSnapshot && <p>{text.previousInUse}</p>}
      <p>{messages.recovery[recovery]}</p>
      {recovery === 'reviewExclusions' && (
        <p>
          <Link to={review}>{messages.project.reviewPreAnalysis}</Link>
        </p>
      )}
    </Callout>
  );
}
