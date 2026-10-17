import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { useEffect, useState, type ReactNode } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { useMediaQuery } from '../../components/useMediaQuery';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { getProject, getProviders, importKeys, type Providers } from '../import/api';
import type { Project } from '../workspaces/api';
import {
  askKeys,
  askQuestion,
  getConversation,
  type Answer,
  type Conversation,
  type Evidence,
  type NewQuestion,
} from './api';
import { AnswerTurn, Question } from './AnswerTurn';
import { AskForm } from './AskForm';
import { CodeViewer } from './CodeViewer';
import { EvidencePanel } from './EvidencePanel';

const WIDE_LAYOUT = '(min-width: 64rem)';
const VECTOR_BLOCKERS = new Set<Project['outdatedReasons'][number]>([
  'EMBEDDING_MODEL_CHANGED',
  'EMBEDDINGS_MISSING',
]);

type Pane = 'conversation' | 'evidence' | 'code';

export function ChatPage() {
  const { projectId = '', conversationId } = useParams();
  const project = useQuery({
    queryKey: importKeys.project(projectId),
    queryFn: ({ signal }) => getProject(projectId, signal),
  });
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  const conversation = useQuery({
    queryKey: askKeys.conversation(conversationId ?? ''),
    queryFn: ({ signal }) => getConversation(conversationId ?? '', signal),
    enabled: conversationId !== undefined,
  });
  const title = conversationId
    ? (conversation.data?.conversation.title ?? messages.pages.conversation)
    : messages.pages.ask;

  const failed = [project, providers, conversation].find((query) => query.isError);
  if (failed) {
    const notFound =
      failed.error instanceof ApiError &&
      (failed.error.code === 'PROJECT_NOT_FOUND' || failed.error.code === 'CONVERSATION_NOT_FOUND');
    return (
      <section className="stack">
        <Link to={project.isSuccess ? `/projects/${projectId}` : '/'} className="back-link">
          {project.isSuccess ? messages.ask.backToProject : messages.projects.allWorkspaces}
        </Link>
        <h1>{title}</h1>
        <ProblemAlert
          error={failed.error}
          onRetry={notFound ? undefined : () => void failed.refetch()}
        />
      </section>
    );
  }
  if (!project.isSuccess || !providers.isSuccess || (conversationId && conversation.isPending)) {
    return (
      <section>
        <h1>{title}</h1>
        <Loading />
      </section>
    );
  }
  const loaded = conversation.data ?? null;
  if (loaded && loaded.conversation.projectId !== projectId) {
    return (
      <Navigate
        to={`/projects/${loaded.conversation.projectId}/conversations/${loaded.conversation.id}`}
        replace
      />
    );
  }
  return (
    <Chat
      key={conversationId ?? 'new'}
      title={title}
      project={project.data}
      providers={providers.data}
      conversation={loaded}
    />
  );
}

interface ChatProps {
  title: string;
  project: Project;
  providers: Providers;
  conversation: Conversation | null;
}

function Chat({ title, project, providers, conversation }: ChatProps) {
  const text = messages.ask;
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const wide = useMediaQuery(WIDE_LAYOUT, true);
  const answers = conversation?.answers ?? [];
  const conversationId = conversation?.conversation.id ?? null;
  const [selectedAnswerId, setSelectedAnswerId] = useState<string | null>(null);
  const [selectedEvidence, setSelectedEvidence] = useState<Evidence | null>(null);
  const [pane, setPane] = useState<Pane>('conversation');
  const selectedAnswer =
    answers.find((answer) => answer.answerId === selectedAnswerId) ?? answers.at(-1) ?? null;

  const mutation = useMutation({
    mutationFn: (question: NewQuestion) => askQuestion(project.id, question),
    onSuccess: async (answer) => {
      setSelectedAnswerId(answer.answerId);
      setSelectedEvidence(null);
      void queryClient.invalidateQueries({ queryKey: askKeys.conversations(project.id) });
      const key = askKeys.conversation(answer.conversationId);
      if (conversationId === answer.conversationId) {
        queryClient.setQueryData<Conversation>(key, (old) =>
          old ? { ...old, answers: [...old.answers, answer] } : old,
        );
        void queryClient.invalidateQueries({ queryKey: key });
        return;
      }
      await queryClient.prefetchQuery({
        queryKey: key,
        queryFn: ({ signal }) => getConversation(answer.conversationId, signal),
      });
      await navigate(`/projects/${project.id}/conversations/${answer.conversationId}`, {
        replace: true,
      });
    },
  });

  function openEvidence(answer: Answer, evidence: Evidence) {
    setSelectedAnswerId(answer.answerId);
    setSelectedEvidence(evidence);
    setPane('code');
  }

  const activeSnapshot = project.activeSnapshot;
  const generationAvailable = providers.generation.provider !== 'none';
  const vectorAvailable =
    providers.embeddings.provider !== 'none' &&
    !project.outdatedReasons.some((reason) => VECTOR_BLOCKERS.has(reason));
  const review = `/workspaces/${project.workspaceId}/new?project=${project.id}`;

  const conversationPane = (
    <section className="stack chat__conversation" aria-labelledby="conversation-title">
      <h2 id="conversation-title" className={wide ? 'visually-hidden' : undefined}>
        {text.conversationTitle}
      </h2>
      {answers.length === 0 && !mutation.isPending && <p className="muted">{text.empty}</p>}
      {(answers.length > 0 || mutation.isPending) && (
        <ol className="turns">
          {answers.map((answer) => (
            <AnswerTurn
              key={answer.answerId}
              answer={answer}
              selected={answer.answerId === selectedAnswer?.answerId}
              olderSnapshot={activeSnapshot !== null && answer.snapshotId !== activeSnapshot.id}
              onCite={openEvidence}
              onShowEvidence={(chosen) => {
                setSelectedAnswerId(chosen.answerId);
                setPane('evidence');
              }}
            />
          ))}
          {mutation.isPending && (
            <WaitingTurn question={mutation.variables.question} since={mutation.submittedAt} />
          )}
        </ol>
      )}
      {mutation.isError && <ProblemAlert error={mutation.error} />}
      {!generationAvailable ? (
        <p className="notice notice--warning">{text.noGeneration}</p>
      ) : activeSnapshot ? (
        <AskForm
          projectId={project.id}
          conversationId={conversationId}
          providers={providers}
          vectorAvailable={vectorAvailable}
          reviewHref={review}
          showSuggestions={answers.length === 0 && !mutation.isPending}
          pending={mutation.isPending}
          onAsk={(question) => mutation.mutateAsync(question)}
        />
      ) : (
        <div className="notice stack">
          <p>{text.noSnapshot}</p>
          <p>
            <Link to={`/projects/${project.id}`}>{text.openProject}</Link>
          </p>
        </div>
      )}
    </section>
  );
  const evidencePane = (
    <section className="stack panel" aria-labelledby="evidence-title">
      <h2 id="evidence-title">{text.evidence.title}</h2>
      <EvidencePanel
        answer={selectedAnswer}
        selectedEvidenceId={selectedEvidence?.id ?? null}
        onOpen={(evidence) => selectedAnswer && openEvidence(selectedAnswer, evidence)}
      />
    </section>
  );
  const codePane = (
    <section className="stack panel" aria-labelledby="viewer-title">
      <h2 id="viewer-title">{text.viewer.title}</h2>
      {selectedEvidence ? (
        <CodeViewer
          key={selectedEvidence.id}
          projectId={project.id}
          evidence={selectedEvidence}
          activeSnapshotId={activeSnapshot?.id ?? null}
        />
      ) : (
        <p className="muted">{text.viewer.empty}</p>
      )}
    </section>
  );

  return (
    <section className="stack chat">
      <ChatHeader title={title} project={project} />
      {wide ? (
        <div className="chat__columns">
          {conversationPane}
          <div className="stack chat__side">
            {evidencePane}
            {codePane}
          </div>
        </div>
      ) : (
        <Tabs.Root value={pane} onValueChange={(value) => setPane(value as Pane)}>
          <Tabs.List className="tabs" aria-label={text.tabs.label}>
            <Tabs.Trigger className="tabs__trigger" value="conversation">
              {text.tabs.conversation}
            </Tabs.Trigger>
            <Tabs.Trigger className="tabs__trigger" value="evidence">
              {text.tabs.evidence}
            </Tabs.Trigger>
            <Tabs.Trigger className="tabs__trigger" value="code">
              {text.tabs.code}
            </Tabs.Trigger>
          </Tabs.List>
          <TabPane value="conversation">{conversationPane}</TabPane>
          <TabPane value="evidence">{evidencePane}</TabPane>
          <TabPane value="code">{codePane}</TabPane>
        </Tabs.Root>
      )}
    </section>
  );
}

function TabPane({ value, children }: { value: Pane; children: ReactNode }) {
  return (
    <Tabs.Content value={value} className="tabs__content">
      {children}
    </Tabs.Content>
  );
}

function ChatHeader({ title, project }: { title: string; project: Project }) {
  const text = messages.ask;
  const { activeSnapshot } = project;
  return (
    <div className="stack chat__header">
      <Link to={`/projects/${project.id}`} className="back-link">
        {text.backToProject}
      </Link>
      <h1>{title}</h1>
      <p>
        <span className="muted">{project.name}</span>
        {activeSnapshot?.completedAt && (
          <span className="muted">
            {' '}
            · {text.snapshot(formatDateTime(activeSnapshot.completedAt))}
          </span>
        )}
      </p>
      {project.indexing && <p className="notice">{text.indexing}</p>}
      {activeSnapshot && project.outdatedReasons.length > 0 && (
        <p className="notice notice--warning">
          {text.outdated} <Link to={`/projects/${project.id}`}>{text.outdatedLink}</Link>
        </p>
      )}
    </div>
  );
}

function WaitingTurn({ question, since }: { question: string; since: number }) {
  const text = messages.ask.waiting;
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const seconds = Math.max(0, Math.floor((now - since) / 1000));
  return (
    <li className="turn">
      <Question question={question} />
      <div className="turn__answer stack">
        <p>
          <span role="status">{text.status}</span>{' '}
          <span className="muted">{text.elapsed(seconds)}</span>
        </p>
        <p className="muted">{text.limit}</p>
      </div>
    </li>
  );
}
