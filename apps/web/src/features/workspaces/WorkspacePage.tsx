import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useParams, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { usePageTitle } from '../../app/title';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
import { RelativeTime } from '../../components/ui/RelativeTime';
import { Skeleton } from '../../components/ui/Skeleton';
import { TileGrid, TileGridSkeleton } from '../../components/ui/TileGrid';
import { messages } from '../../messages';
import { getWorkspace, listProjects, workspaceKeys } from './api';
import { EmptyProjectsIllustration } from './illustrations';
import { ProjectTile } from './ProjectTile';

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

  usePageTitle([workspace.data?.name ?? messages.pages.workspace]);

  if (workspace.isPending) {
    return (
      <section className="stack">
        <h1>{messages.pages.workspace}</h1>
        <Skeleton width="12rem" />
        <TileGridSkeleton />
      </section>
    );
  }

  if (workspace.isError) {
    const notFound =
      workspace.error instanceof ApiError && workspace.error.code === 'WORKSPACE_NOT_FOUND';
    return (
      <section className="stack">
        <h1>{messages.pages.workspace}</h1>
        <ProblemAlert
          error={workspace.error}
          onRetry={notFound ? undefined : () => void workspace.refetch()}
        />
      </section>
    );
  }

  const newProject = `/workspaces/${workspaceId}/new`;
  const text = messages.projects;

  return (
    <section className="stack">
      <PageHeader
        title={workspace.data.name}
        description={
          <>
            <span>{messages.workspaces.projectCount(workspace.data.projectCount)}</span>
            <span aria-hidden="true">·</span>
            <span>
              {messages.workspaces.created} <RelativeTime iso={workspace.data.createdAt} />
            </span>
          </>
        }
        actions={
          <ButtonLink to={newProject} variant="primary" icon={Plus}>
            {text.newProject}
          </ButtonLink>
        }
      />
      {projects.isPending && <TileGridSkeleton />}
      {projects.isError && (
        <ProblemAlert error={projects.error} onRetry={() => void projects.refetch()} />
      )}
      {projects.isSuccess && projects.data.totalItems === 0 && (
        <EmptyState
          illustration={<EmptyProjectsIllustration />}
          title={text.emptyTitle}
          action={
            <ButtonLink to={newProject} variant="primary" icon={Plus}>
              {text.emptyAction}
            </ButtonLink>
          }
        >
          <p>{text.emptyDetail}</p>
        </EmptyState>
      )}
      {projects.isSuccess && projects.data.totalItems > 0 && (
        <>
          <TileGrid>
            {projects.data.items.map((project) => (
              <ProjectTile key={project.id} project={project} />
            ))}
          </TileGrid>
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
