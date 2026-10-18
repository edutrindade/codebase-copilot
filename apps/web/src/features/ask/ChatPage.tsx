import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MessageSquare, SquarePen } from 'lucide-react';
import { useEffect, useId, useRef, useState, type CSSProperties } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { useShortcut } from '../../app/shortcuts';
import { readStorage, writeStorage } from '../../app/storage';
import { usePageTitle } from '../../app/title';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { Skeleton } from '../../components/ui/Skeleton';
import { Splitter } from '../../components/ui/Splitter';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../components/ui/Tabs';
import { useMediaQuery } from '../../components/useMediaQuery';
import { messages } from '../../messages';
import { getProviders, importKeys, type Providers } from '../import/api';
import { useProject } from '../project/ProjectLayout';
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
import { draftFromState } from './draft';
import { EvidencePanel } from './EvidencePanel';
import { highlightedRange, listOrder } from './evidence';
import './ChatPage.css';

const WIDE_LAYOUT = '(min-width: 64rem)';
const SPLIT_KEY = 'ask-split';
const SPLIT = { min: 30, max: 70, default: 50 };
// An answer is PENDING only while another request generates it, or until the server marks it interrupted.
const PENDING_POLL_MS = 2000;
const VECTOR_BLOCKERS = new Set<Project['outdatedReasons'][number]>([
  'EMBEDDING_MODEL_CHANGED',
  'EMBEDDINGS_MISSING',
]);

type Pane = 'conversation' | 'evidence' | 'code';

export function ChatPage() {
  const { conversationId } = useParams();
  const project = useProject();
  const projectId = project.id;
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  const conversation = useQuery({
    queryKey: askKeys.conversation(conversationId ?? ''),
    queryFn: ({ signal }) => getConversation(conversationId ?? '', signal),
    enabled: conversationId !== undefined,
    refetchInterval: (query) =>
      query.state.data?.answers.some((answer) => answer.status === 'PENDING')
        ? PENDING_POLL_MS
        : false,
  });
  const title = conversationId
    ? (conversation.data?.conversation.title ?? messages.pages.conversation)
    : messages.pages.ask;
  usePageTitle([title, project.name]);

  const failed = [providers, conversation].find((query) => query.isError);
  if (failed) {
    const notFound =
      failed.error instanceof ApiError && failed.error.code === 'CONVERSATION_NOT_FOUND';
    return (
      <section className="stack">
        <h1>{title}</h1>
        <ProblemAlert
          error={failed.error}
          onRetry={notFound ? undefined : () => void failed.refetch()}
        />
      </section>
    );
  }
  if (!providers.isSuccess || (conversationId && conversation.isPending)) {
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
      project={project}
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
  const [announcement, setAnnouncement] = useState('');
  const location = useLocation();
  const [draft, setDraft] = useState(() => draftFromState(location.state));
  const [split, setSplit] = useState(readSplit);
  const columns = useRef<HTMLDivElement>(null);
  const ids = { question: useId(), conversation: useId() };
  useEffect(() => writeStorage(SPLIT_KEY, String(split)), [split]);
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
    // The side column sticks only after the page scrolls past the header; until then the viewer
    // runs below the fold, so scroll just enough for the whole column to fit.
    const side = columns.current?.querySelector('.chat__side');
    const overflow = side ? side.getBoundingClientRect().bottom - window.innerHeight + 16 : 0;
    if (wide && overflow > 0) {
      window.scrollBy({ top: overflow });
    }
  }

  // Same order as the evidence list; stops at both ends.
  function stepEvidence(step: 1 | -1) {
    const ordered = selectedAnswer ? listOrder(selectedAnswer.evidence) : [];
    if (!selectedAnswer || ordered.length === 0) {
      return;
    }
    const index = ordered.findIndex((evidence) => evidence.id === selectedEvidence?.id);
    const next =
      index === -1
        ? step === 1
          ? 0
          : ordered.length - 1
        : Math.min(ordered.length - 1, Math.max(0, index + step));
    const evidence = ordered[next];
    if (index === next || !evidence) {
      return;
    }
    openEvidence(selectedAnswer, evidence);
    const range = highlightedRange(evidence);
    setAnnouncement(
      text.evidence.opened(
        evidence.citationLabel,
        evidence.path,
        messages.ask.lines(range.start, range.end),
      ),
    );
  }
  useShortcut('previousEvidence', () => stepEvidence(-1));
  useShortcut('nextEvidence', () => stepEvidence(1));

  const activeSnapshot = project.activeSnapshot;
  const generationAvailable = providers.generation.provider !== 'none';
  const vectorAvailable =
    providers.embeddings.provider !== 'none' &&
    !project.outdatedReasons.some((reason) => VECTOR_BLOCKERS.has(reason));
  const review = `/workspaces/${project.workspaceId}/new?project=${project.id}`;

  const empty = answers.length === 0 && !mutation.isPending;
  const canAsk = generationAvailable && activeSnapshot !== null;
  const conversationPane = (
    <section
      id={ids.conversation}
      className="chat__conversation"
      aria-labelledby="conversation-title"
    >
      <h2 id="conversation-title" className={wide ? 'visually-hidden' : undefined}>
        {text.conversationTitle}
      </h2>
      {empty && (
        <EmptyConversation
          suggestions={canAsk}
          onSuggestion={(suggestion) => {
            setDraft(suggestion);
            document.getElementById(ids.question)?.focus();
          }}
        />
      )}
      {!empty && (
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
        <Callout tone="warning">
          <p>{text.noGeneration}</p>
        </Callout>
      ) : activeSnapshot ? (
        <AskForm
          questionId={ids.question}
          projectId={project.id}
          conversationId={conversationId}
          providers={providers}
          vectorAvailable={vectorAvailable}
          reviewHref={review}
          draft={draft}
          onDraftChange={setDraft}
          pending={mutation.isPending}
          onAsk={(question) => mutation.mutateAsync(question)}
        />
      ) : (
        <Callout tone="info">
          <p>{text.noSnapshot}</p>
          <p>
            <Link to={`/projects/${project.id}`}>{text.openProject}</Link>
          </p>
        </Callout>
      )}
    </section>
  );
  const evidenceCount = selectedAnswer?.evidence.length ?? 0;
  const evidencePane = (
    <Card as="section" className="stack chat__evidence" aria-labelledby="evidence-title">
      <div className="chat__pane-header">
        <h2 id="evidence-title">{text.evidence.title}</h2>
        {evidenceCount > 0 && <span className="muted">{text.evidence.count(evidenceCount)}</span>}
      </div>
      <EvidencePanel
        answer={selectedAnswer}
        selectedEvidenceId={selectedEvidence?.id ?? null}
        onOpen={(evidence) => selectedAnswer && openEvidence(selectedAnswer, evidence)}
      />
    </Card>
  );
  const codePane = (
    <Card as="section" className="stack chat__code" aria-labelledby="viewer-title">
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
    </Card>
  );

  return (
    <section className="stack chat">
      <ChatHeader title={title} project={project} inConversation={conversation !== null} />
      <div className="visually-hidden" aria-live="polite" aria-atomic="true">
        {announcement}
      </div>
      {wide ? (
        <div
          ref={columns}
          className="chat__columns"
          style={{ '--chat-split': `${split}%` } as CSSProperties}
        >
          {conversationPane}
          <Splitter
            value={split}
            min={SPLIT.min}
            max={SPLIT.max}
            onChange={setSplit}
            containerRef={columns}
            controls={ids.conversation}
            label={text.split.label}
            valueText={text.split.value}
            decreaseLabel={text.split.narrower}
            increaseLabel={text.split.wider}
          />
          <div className="chat__side">
            {evidencePane}
            {codePane}
          </div>
        </div>
      ) : (
        <Tabs value={pane} onValueChange={(value) => setPane(value as Pane)}>
          <TabsList aria-label={text.tabs.label}>
            <TabsTrigger value="conversation">{text.tabs.conversation}</TabsTrigger>
            <TabsTrigger value="evidence">{text.tabs.evidence}</TabsTrigger>
            <TabsTrigger value="code">{text.tabs.code}</TabsTrigger>
          </TabsList>
          <TabsContent value="conversation">{conversationPane}</TabsContent>
          <TabsContent value="evidence">{evidencePane}</TabsContent>
          <TabsContent value="code">{codePane}</TabsContent>
        </Tabs>
      )}
    </section>
  );
}

interface ChatHeaderProps {
  title: string;
  project: Project;
  inConversation: boolean;
}

function ChatHeader({ title, project, inConversation }: ChatHeaderProps) {
  const text = messages.ask;
  const { activeSnapshot } = project;
  return (
    <div className="stack chat__header">
      <PageHeader
        title={title}
        actions={
          inConversation &&
          activeSnapshot && (
            <ButtonLink to={`/projects/${project.id}/ask`} icon={SquarePen}>
              {text.newConversation}
            </ButtonLink>
          )
        }
      />
      {project.indexing && (
        <Callout tone="info">
          <p>{text.indexing}</p>
        </Callout>
      )}
      {activeSnapshot && project.outdatedReasons.length > 0 && (
        <Callout tone="warning">
          <p>
            {text.outdated} <Link to={`/projects/${project.id}`}>{text.outdatedLink}</Link>
          </p>
        </Callout>
      )}
    </div>
  );
}

function readSplit(): number {
  const stored = Number(readStorage(SPLIT_KEY));
  return Number.isFinite(stored) && stored >= SPLIT.min && stored <= SPLIT.max
    ? stored
    : SPLIT.default;
}

function EmptyConversation({
  suggestions,
  onSuggestion,
}: {
  suggestions: boolean;
  onSuggestion: (suggestion: string) => void;
}) {
  const text = messages.ask;
  return (
    <div className="stack chat__empty">
      <div>
        <h3>{text.emptyTitle}</h3>
        <p className="muted">{text.empty}</p>
      </div>
      {suggestions && (
        <section className="stack" aria-labelledby="suggestions-title">
          <h4 id="suggestions-title">{text.suggestionsTitle}</h4>
          <ul className="suggestion-grid">
            {text.suggestions.map((suggestion) => (
              <li key={suggestion}>
                <button
                  type="button"
                  className="suggestion-card"
                  onClick={() => onSuggestion(suggestion)}
                >
                  <MessageSquare aria-hidden="true" className="suggestion-card__icon" />
                  {suggestion}
                </button>
              </li>
            ))}
          </ul>
        </section>
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
      <div className="answer-card answer-card--waiting" aria-busy="true">
        <p>
          <span role="status">{text.status}</span>{' '}
          <span className="muted tabular">{text.elapsed(seconds)}</span>
        </p>
        <div className="answer-card__skeleton">
          <Skeleton width="92%" />
          <Skeleton width="85%" />
          <Skeleton width="60%" />
        </div>
        <p className="muted">{text.limit}</p>
      </div>
    </li>
  );
}
