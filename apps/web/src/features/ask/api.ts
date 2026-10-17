import { api } from '../../api/client';
import { unwrap } from '../../api/errors';
import type { components } from '../../api/schema';

type Schemas = components['schemas'];
export type Answer = Schemas['AnswerResponse'];
export type Evidence = Schemas['EvidenceResponse'];
export type EvidenceDetail = Schemas['EvidenceDetailResponse'];
export type FileContent = Schemas['FileContentResponse'];
export type Conversation = Schemas['ConversationResponse'];
export type ConversationSummary = Schemas['ConversationSummaryResponse'];
export type RetrievalMode = NonNullable<Schemas['OptionsRequest']['retrievalMode']>;

export interface NewQuestion {
  question: string;
  conversationId: string | null;
  retrievalMode: RetrievalMode;
  maxEvidence: number;
}

export const askKeys = {
  conversations: (projectId: string) => ['projects', projectId, 'conversations'] as const,
  conversationsPage: (projectId: string, page: number) =>
    ['projects', projectId, 'conversations', page] as const,
  conversation: (conversationId: string) => ['conversations', conversationId] as const,
  evidence: (projectId: string, evidenceId: string) =>
    ['projects', projectId, 'evidence', evidenceId] as const,
  content: (snapshotId: string, path: string) =>
    ['snapshots', snapshotId, 'content', path] as const,
};

export function askQuestion(projectId: string, question: NewQuestion) {
  return unwrap(
    api.POST('/api/v1/projects/{projectId}/questions', {
      params: { path: { projectId } },
      body: {
        question: question.question,
        conversationId: question.conversationId,
        options: { retrievalMode: question.retrievalMode, maxEvidence: question.maxEvidence },
      },
    }),
  );
}

export function listConversations(projectId: string, page: number, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}/conversations', {
      params: { path: { projectId }, query: { page } },
      signal,
    }),
  );
}

export function getConversation(conversationId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/conversations/{conversationId}', {
      params: { path: { conversationId } },
      signal,
    }),
  );
}

export function getEvidence(projectId: string, evidenceId: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/projects/{projectId}/evidence/{evidenceId}', {
      params: { path: { projectId, evidenceId } },
      signal,
    }),
  );
}

export function getFileContent(snapshotId: string, path: string, signal: AbortSignal) {
  return unwrap(
    api.GET('/api/v1/snapshots/{snapshotId}/files/content', {
      params: { path: { snapshotId }, query: { path } },
      signal,
    }),
  );
}
