import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import {
  askKeys,
  deleteConversation,
  listConversations,
  type ConversationSummary,
} from '../ask/api';
import { getProject, importKeys } from '../import/api';

export function HistoryPage() {
  const text = messages.history;
  const { projectId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFromSearch(searchParams);
  const project = useQuery({
    queryKey: importKeys.project(projectId),
    queryFn: ({ signal }) => getProject(projectId, signal),
  });
  const conversations = useQuery({
    queryKey: askKeys.conversationsPage(projectId, page),
    queryFn: ({ signal }) => listConversations(projectId, page, signal),
    placeholderData: keepPreviousData,
  });

  const failed = [project, conversations].find((query) => query.isError);
  if (failed) {
    const notFound = failed.error instanceof ApiError && failed.error.code === 'PROJECT_NOT_FOUND';
    return (
      <section className="stack">
        <Link to="/" className="back-link">
          {messages.projects.allWorkspaces}
        </Link>
        <h1>{messages.pages.history}</h1>
        <ProblemAlert
          error={failed.error}
          onRetry={notFound ? undefined : () => void failed.refetch()}
        />
      </section>
    );
  }
  if (!project.isSuccess || !conversations.isSuccess) {
    return (
      <section>
        <h1>{messages.pages.history}</h1>
        <Loading />
      </section>
    );
  }

  const canAsk = project.data.activeSnapshot !== null;
  const askLink = (
    <Link to={`/projects/${projectId}/ask`} className="button button--primary">
      {text.ask}
    </Link>
  );
  return (
    <section className="stack">
      <Link to={`/projects/${projectId}`} className="back-link">
        {messages.ask.backToProject}
      </Link>
      <div className="page-header">
        <h1>{messages.pages.history}</h1>
        {canAsk && conversations.data.totalItems > 0 && askLink}
      </div>
      <p>
        <span className="muted">{project.data.name}</span>
      </p>
      {conversations.data.totalItems === 0 ? (
        <div className="empty-state stack">
          <h2>{text.emptyTitle}</h2>
          <p>{text.emptyDetail}</p>
          {canAsk && <div>{askLink}</div>}
        </div>
      ) : (
        <>
          <p className="muted">{text.detail}</p>
          <ul className="card-list">
            {conversations.data.items.map((conversation) => (
              <ConversationItem key={conversation.id} conversation={conversation} />
            ))}
          </ul>
          <Pagination
            page={page}
            totalPages={conversations.data.totalPages}
            onChange={(next) => setSearchParams({ page: String(next + 1) })}
          />
        </>
      )}
    </section>
  );
}

function ConversationItem({ conversation }: { conversation: ConversationSummary }) {
  const text = messages.history;
  const queryClient = useQueryClient();

  async function remove() {
    await deleteConversation(conversation.id);
    queryClient.removeQueries({ queryKey: askKeys.conversation(conversation.id) });
    await queryClient.invalidateQueries({
      queryKey: ['projects', conversation.projectId],
    });
  }

  return (
    <li className="card">
      <div className="card__main">
        <Link
          to={`/projects/${conversation.projectId}/conversations/${conversation.id}`}
          className="card__title"
        >
          {conversation.title}
        </Link>
        <p className="muted">
          {text.questionCount(conversation.questionCount)} ·{' '}
          {text.updatedAt(formatDateTime(conversation.updatedAt))} ·{' '}
          {text.createdAt(formatDateTime(conversation.createdAt))}
        </p>
      </div>
      <ConfirmDialog
        trigger={messages.confirm.deleteTrigger}
        triggerLabel={text.delete(conversation.title)}
        title={text.deleteTitle}
        description={
          <>
            <p>
              <strong>{conversation.title}</strong>
            </p>
            <p>{text.deleteDetail}</p>
          </>
        }
        confirmLabel={text.deleteConfirm}
        onConfirm={remove}
      />
    </li>
  );
}
