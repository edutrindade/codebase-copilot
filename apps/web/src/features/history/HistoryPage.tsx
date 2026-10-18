import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  Ellipsis,
  MessageSquare,
  MessageSquareText,
  MessagesSquare,
  Trash,
} from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { usePageTitle } from '../../app/title';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { IconButton } from '../../components/ui/IconButton';
import { Menu, MenuItem, MenuSeparator } from '../../components/ui/Menu';
import { PageHeader } from '../../components/ui/PageHeader';
import { RelativeTime } from '../../components/ui/RelativeTime';
import { useToast } from '../../components/ui/Toast';
import { messages } from '../../messages';
import {
  askKeys,
  deleteConversation,
  listConversations,
  type ConversationSummary,
} from '../ask/api';
import { useProject } from '../project/ProjectLayout';
import { groupByPeriod } from './periods';
import './HistoryPage.css';

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
          {groupByPeriod(conversations.data.items).map((group) => (
            <section
              key={group.period}
              className="history-group"
              aria-labelledby={`history-${group.period}`}
            >
              <h2 id={`history-${group.period}`} className="history-group__title">
                {text.periods[group.period]}
              </h2>
              <ul className="history-list">
                {group.items.map((conversation) => (
                  <ConversationItem key={conversation.id} conversation={conversation} />
                ))}
              </ul>
            </section>
          ))}
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
  const navigate = useNavigate();
  const notify = useToast();
  const [confirming, setConfirming] = useState(false);
  const to = `/projects/${conversation.projectId}/conversations/${conversation.id}`;

  async function remove() {
    await deleteConversation(conversation.id);
    queryClient.removeQueries({ queryKey: askKeys.conversation(conversation.id) });
    await queryClient.invalidateQueries({
      queryKey: ['projects', conversation.projectId],
    });
    notify({
      tone: 'success',
      title: messages.toast.conversationDeleted,
      description: conversation.title,
    });
  }

  return (
    <li className="history-item">
      <MessageSquare aria-hidden="true" className="history-item__icon" />
      <div className="history-item__main">
        <Link to={to} className="history-item__title" viewTransition>
          {conversation.title}
        </Link>
        <p className="history-item__meta">
          {text.questionCount(conversation.questionCount)} ·{' '}
          <RelativeTime iso={conversation.updatedAt} />
        </p>
      </div>
      <Menu
        trigger={
          <IconButton
            label={text.actions(conversation.title)}
            icon={Ellipsis}
            size="sm"
            tooltip={false}
          />
        }
      >
        <MenuItem icon={ArrowRight} onSelect={() => void navigate(to, { viewTransition: true })}>
          {text.open}
        </MenuItem>
        <MenuSeparator />
        <MenuItem icon={Trash} tone="danger" onSelect={() => setConfirming(true)}>
          {text.deleteItem}
        </MenuItem>
      </Menu>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
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
