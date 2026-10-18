import { useMatches } from 'react-router';

export type RouteSection =
  | 'workspaces'
  | 'workspace'
  | 'newProject'
  | 'overview'
  | 'ask'
  | 'conversation'
  | 'history'
  | 'settings'
  | 'system'
  | 'catalog'
  | 'notFound';

export interface RouteHandle {
  section: RouteSection;
}

export interface RouteContext {
  section: RouteSection | null;
  workspaceId: string | null;
  projectId: string | null;
  conversationId: string | null;
}

function isRouteHandle(handle: unknown): handle is RouteHandle {
  return typeof handle === 'object' && handle !== null && 'section' in handle;
}

// The shell sits above the screens, so it reads the deepest match instead of its own params.
export function useRouteContext(): RouteContext {
  const matches = useMatches();
  const params = matches.at(-1)?.params ?? {};
  const handle = matches.map((match) => match.handle).findLast(isRouteHandle);
  return {
    section: handle?.section ?? null,
    workspaceId: params.workspaceId ?? null,
    projectId: params.projectId ?? null,
    conversationId: params.conversationId ?? null,
  };
}
