import { keepPreviousData, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { forgetProject, readRecentProjects } from '../../app/recentProjects';
import { usePageTitle } from '../../app/title';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { getProject } from '../import/api';
import {
  deleteWorkspace,
  listWorkspaces,
  workspaceKeys,
  type Project,
  type Workspace,
} from './api';
import { PROJECT_STATUS_TONE, projectSource, projectStatus } from './projectStatus';
import { CreateWorkspaceForm } from './CreateWorkspaceForm';

export function WorkspacesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFromSearch(searchParams);
  const query = useQuery({
    queryKey: workspaceKeys.list(page),
    queryFn: ({ signal }) => listWorkspaces(page, signal),
    placeholderData: keepPreviousData,
  });
  const firstUse = query.isSuccess && query.data.totalItems === 0;
  usePageTitle([firstUse ? messages.workspaces.firstUseTitle : messages.workspaces.title]);

  if (query.isPending) {
    return (
      <section>
        <h1>{messages.workspaces.title}</h1>
        <Loading />
      </section>
    );
  }

  if (query.isError) {
    return (
      <section>
        <h1>{messages.workspaces.title}</h1>
        <ProblemAlert error={query.error} onRetry={() => void query.refetch()} />
      </section>
    );
  }

  if (query.data.totalItems === 0) {
    return (
      <section className="stack">
        <h1>{messages.workspaces.firstUseTitle}</h1>
        <p>{messages.workspaces.firstUseDetail}</p>
        <CreateWorkspaceForm />
      </section>
    );
  }

  return (
    <section className="stack">
      <h1>{messages.workspaces.title}</h1>
      <ul className="card-list">
        {query.data.items.map((workspace) => (
          <WorkspaceItem key={workspace.id} workspace={workspace} />
        ))}
      </ul>
      <Pagination
        page={page}
        totalPages={query.data.totalPages}
        onChange={(next) => setSearchParams({ page: String(next + 1) })}
      />
      <RecentProjects />
      <h2>{messages.workspaces.newTitle}</h2>
      <CreateWorkspaceForm />
    </section>
  );
}

function WorkspaceItem({ workspace }: { workspace: Workspace }) {
  const queryClient = useQueryClient();

  async function remove() {
    await deleteWorkspace(workspace.id);
    await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }

  return (
    <li className="card">
      <div className="card__main">
        <Link to={`/workspaces/${workspace.id}`} className="card__title">
          {workspace.name}
        </Link>
        <p className="muted">
          {messages.workspaces.projectCount(workspace.projectCount)} ·{' '}
          {messages.workspaces.createdAt(formatDateTime(workspace.createdAt))}
        </p>
      </div>
      <ConfirmDialog
        trigger={messages.confirm.deleteTrigger}
        triggerLabel={messages.workspaces.delete(workspace.name)}
        title={messages.workspaces.deleteTitle(workspace.name)}
        description={
          <>
            <p>{messages.workspaces.deleteDetail}</p>
            {workspace.projectCount > 0 && (
              <p>{messages.workspaces.deleteNotEmpty(workspace.projectCount)}</p>
            )}
          </>
        }
        confirmLabel={messages.workspaces.deleteConfirm}
        onConfirm={remove}
      />
    </li>
  );
}

// Checked against the API: a project removed elsewhere leaves the list without an error.
async function getRecentProject(projectId: string, signal: AbortSignal): Promise<Project | null> {
  try {
    return await getProject(projectId, signal);
  } catch (error) {
    if (error instanceof ApiError && error.code === 'PROJECT_NOT_FOUND') {
      forgetProject(projectId);
      return null;
    }
    throw error;
  }
}

function RecentProjects() {
  const text = messages.workspaces;
  const [ids] = useState(readRecentProjects);
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: ['recent-projects', id],
      queryFn: ({ signal }: { signal: AbortSignal }) => getRecentProject(id, signal),
    })),
  });
  const projects = results
    .map((result) => result.data)
    .filter((project): project is Project => Boolean(project));
  if (projects.length === 0) {
    return null;
  }
  return (
    <section className="stack" aria-labelledby="recent-projects-title">
      <div>
        <h2 id="recent-projects-title">{text.continueTitle}</h2>
        <p className="muted">{text.continueDetail}</p>
      </div>
      <ul className="card-list">
        {projects.map((project) => {
          const status = projectStatus(project);
          return (
            <li key={project.id} className="card">
              <div className="card__main">
                <Link to={`/projects/${project.id}`} className="card__title">
                  {project.name}
                </Link>
                <p className="muted">{projectSource(project)}</p>
              </div>
              <StatusBadge tone={PROJECT_STATUS_TONE[status]}>
                {messages.projects.status[status]}
              </StatusBadge>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
