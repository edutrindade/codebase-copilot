import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { deleteWorkspace, listWorkspaces, workspaceKeys, type Workspace } from './api';
import { CreateWorkspaceForm } from './CreateWorkspaceForm';

export function WorkspacesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFromSearch(searchParams);
  const query = useQuery({
    queryKey: workspaceKeys.list(page),
    queryFn: ({ signal }) => listWorkspaces(page, signal),
    placeholderData: keepPreviousData,
  });

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
