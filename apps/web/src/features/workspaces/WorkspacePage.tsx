import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { FolderOpen, Plus } from 'lucide-react';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { messages } from '../../messages';
import { deleteProject, getWorkspace, listProjects, workspaceKeys, type Project } from './api';
import { PROJECT_STATUS_TONE, projectSource, projectStatus } from './projectStatus';

const ACTIVE_JOB_POLL_MS = 1000;

export function WorkspacePage() {
  const { workspaceId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFromSearch(searchParams);
  const workspace = useQuery({
    queryKey: workspaceKeys.detail(workspaceId),
    queryFn: ({ signal }) => getWorkspace(workspaceId, signal),
  });
  const projects = useQuery({
    queryKey: workspaceKeys.projects(workspaceId, page),
    queryFn: ({ signal }) => listProjects(workspaceId, page, signal),
    placeholderData: keepPreviousData,
    enabled: workspace.isSuccess,
    refetchInterval: (query) =>
      query.state.data?.items.some((project) => project.indexing) ? ACTIVE_JOB_POLL_MS : false,
  });

  const back = (
    <Link to="/" className="back-link">
      {messages.projects.allWorkspaces}
    </Link>
  );

  if (workspace.isPending) {
    return (
      <section>
        {back}
        <h1>{messages.pages.workspace}</h1>
        <Loading />
      </section>
    );
  }

  if (workspace.isError) {
    const notFound =
      workspace.error instanceof ApiError && workspace.error.code === 'WORKSPACE_NOT_FOUND';
    return (
      <section>
        {back}
        <h1>{messages.pages.workspace}</h1>
        <ProblemAlert
          error={workspace.error}
          onRetry={notFound ? undefined : () => void workspace.refetch()}
        />
      </section>
    );
  }

  const newProject = `/workspaces/${workspaceId}/new`;

  return (
    <section className="stack">
      <PageHeader
        back={back}
        title={workspace.data.name}
        actions={
          <ButtonLink to={newProject} variant="primary" icon={Plus}>
            {messages.projects.newProject}
          </ButtonLink>
        }
      />
      {projects.isPending && <Loading />}
      {projects.isError && (
        <ProblemAlert error={projects.error} onRetry={() => void projects.refetch()} />
      )}
      {projects.isSuccess && projects.data.totalItems === 0 && (
        <EmptyState
          icon={FolderOpen}
          title={messages.projects.emptyTitle}
          action={
            <ButtonLink to={newProject} variant="primary" icon={Plus}>
              {messages.projects.newProject}
            </ButtonLink>
          }
        >
          <p>{messages.projects.emptyDetail}</p>
        </EmptyState>
      )}
      {projects.isSuccess && projects.data.totalItems > 0 && (
        <>
          <ul className="card-list">
            {projects.data.items.map((project) => (
              <ProjectItem key={project.id} project={project} />
            ))}
          </ul>
          <Pagination
            page={page}
            totalPages={projects.data.totalPages}
            onChange={(next) => setSearchParams({ page: String(next + 1) })}
          />
        </>
      )}
    </section>
  );
}

function ProjectItem({ project }: { project: Project }) {
  const queryClient = useQueryClient();
  const status = projectStatus(project);
  const progress = project.indexing?.progress;

  async function remove() {
    await deleteProject(project.id);
    await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }

  return (
    <li className="card">
      <div className="card__main">
        <Link to={`/projects/${project.id}`} className="card__title">
          {project.name}
        </Link>
        <p className="muted">{projectSource(project)}</p>
        <p>
          <StatusBadge tone={PROJECT_STATUS_TONE[status]}>
            {messages.projects.status[status]}
          </StatusBadge>
          {progress && progress.total > 0 && (
            <span className="muted">
              {' '}
              · {messages.projects.progress(progress.completed, progress.total)}
            </span>
          )}
        </p>
      </div>
      <ConfirmDialog
        trigger={messages.confirm.deleteTrigger}
        triggerLabel={messages.projects.delete(project.name)}
        title={messages.projects.deleteTitle(project.name)}
        description={
          <>
            <p>{messages.projects.deleteDetail}</p>
            {project.indexing && <p>{messages.projects.deleteBusy}</p>}
          </>
        }
        confirmLabel={messages.projects.deleteConfirm}
        onConfirm={remove}
      />
    </li>
  );
}
