import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { MessageSquareText, MessagesSquare } from 'lucide-react';
import { usePageTitle } from '../../app/title';
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
import { useProject } from '../project/ProjectLayout';

export function HistoryPage() {
  const text = messages.history;
  const project = useProject();
  const projectId = project.id;
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFromSearch(searchParams);
  usePageTitle([messages.pages.history, project.name]);
  const conversations = useQuery({
    queryKey: askKeys.conversationsPage(projectId, page),
    queryFn: ({ signal }) => listConversations(projectId, page, signal),
    placeholderData: keepPreviousData,
  });

  if (conversations.isError) {
    return (
      <section className="stack">
        <h1>{messages.pages.history}</h1>
        <ProblemAlert error={conversations.error} onRetry={() => void conversations.refetch()} />
      </section>
    );
  }
  if (conversations.isPending) {
    return (
      <section>
        <h1>{messages.pages.history}</h1>
        <Loading />
      </section>
    );
  }

  const canAsk = project.activeSnapshot !== null;
  const askLink = (
    <ButtonLink to={`/projects/${projectId}/ask`} variant="primary" icon={MessageSquareText}>
      {text.ask}
    </ButtonLink>
  );
  return (
    <section className="stack">
      <PageHeader
        title={messages.pages.history}
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
