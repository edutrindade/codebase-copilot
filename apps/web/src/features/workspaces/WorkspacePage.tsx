import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { messages } from '../../messages';
import { deleteProject, getWorkspace, listProjects, workspaceKeys, type Project } from './api';
import { projectStatus } from './projectStatus';

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
      {back}
      <div className="page-header">
        <h1>{workspace.data.name}</h1>
        <Link to={newProject} className="button button--primary">
          {messages.projects.newProject}
        </Link>
      </div>
      {projects.isPending && <Loading />}
      {projects.isError && (
        <ProblemAlert error={projects.error} onRetry={() => void projects.refetch()} />
      )}
      {projects.isSuccess && projects.data.totalItems === 0 && (
        <div className="empty-state">
          <h2>{messages.projects.emptyTitle}</h2>
          <p>{messages.projects.emptyDetail}</p>
          <Link to={newProject}>{messages.projects.newProject}</Link>
        </div>
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

function sourceText(project: Project): string {
  const { source } = project;
  return source.type === 'LOCAL_FOLDER'
    ? messages.projects.sourceLocal(source.rootKey ?? '', source.relativePath ?? '')
    : messages.projects.sourceZip;
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
        <p className="muted">{sourceText(project)}</p>
        <p>
          <span className={`status status--${status.toLowerCase()}`}>
            {messages.projects.status[status]}
          </span>
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
