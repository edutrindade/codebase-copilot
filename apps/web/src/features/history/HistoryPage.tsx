import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { MessageSquareText, MessagesSquare } from 'lucide-react';
import { ApiError } from '../../api/errors';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { PageHeader } from '../../components/ui/PageHeader';
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
    <ButtonLink to={`/projects/${projectId}/ask`} variant="primary" icon={MessageSquareText}>
      {text.ask}
    </ButtonLink>
  );
  return (
    <section className="stack">
      <PageHeader
        back={
          <Link to={`/projects/${projectId}`} className="back-link">
            {messages.ask.backToProject}
          </Link>
        }
        title={messages.pages.history}
        description={project.data.name}
        actions={canAsk && conversations.data.totalItems > 0 && askLink}
      />
      {conversations.data.totalItems === 0 ? (
        <EmptyState icon={MessagesSquare} title={text.emptyTitle} action={canAsk && askLink}>
          <p>{text.emptyDetail}</p>
        </EmptyState>
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
