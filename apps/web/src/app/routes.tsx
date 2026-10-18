import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { Loading } from '../components/Loading';
import { AppShell } from '../components/shell/AppShell';
import { NotFoundPage } from '../features/not-found/NotFoundPage';
import type { RouteHandle, RouteSection } from './routeContext';
import { RouteError } from './RouteError';

// One chunk per screen, so the first load stays under 500 kB (P8 of Etapa 8).
function screen(load: () => Promise<Record<string, unknown>>, name: string) {
  return lazy(async () => ({ default: (await load())[name] as ComponentType }));
}

const WorkspacesPage = screen(
  () => import('../features/workspaces/WorkspacesPage'),
  'WorkspacesPage',
);
const WorkspacePage = screen(() => import('../features/workspaces/WorkspacePage'), 'WorkspacePage');
const NewProjectPage = screen(() => import('../features/import/NewProjectPage'), 'NewProjectPage');
const ProjectPage = screen(() => import('../features/project/ProjectPage'), 'ProjectPage');
const ChatPage = screen(() => import('../features/ask/ChatPage'), 'ChatPage');
const HistoryPage = screen(() => import('../features/history/HistoryPage'), 'HistoryPage');
const ProjectLayout = screen(() => import('../features/project/ProjectLayout'), 'ProjectLayout');
const ProjectSettingsPage = screen(
  () => import('../features/project/ProjectSettingsPage'),
  'ProjectSettingsPage',
);
const SystemPage = screen(() => import('../features/system/SystemPage'), 'SystemPage');

// Development only: import.meta.env.DEV is false in the build, which drops the catalog chunk.
function catalogRoutes(): RouteObject[] {
  if (!import.meta.env.DEV) {
    return [];
  }
  const CatalogPage = screen(() => import('../features/catalog/CatalogPage'), 'CatalogPage');
  return [{ path: '_ui', element: page(<CatalogPage />), handle: section('catalog') }];
}

function section(name: RouteSection): RouteHandle {
  return { section: name };
}

function page(element: ReactNode) {
  return <Suspense fallback={<Loading />}>{element}</Suspense>;
}

export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      {
        errorElement: <RouteError />,
        children: [
          { index: true, element: page(<WorkspacesPage />), handle: section('workspaces') },
          {
            path: 'workspaces/:workspaceId',
            element: page(<WorkspacePage />),
            handle: section('workspace'),
          },
          {
            path: 'workspaces/:workspaceId/new',
            element: page(<NewProjectPage />),
            handle: section('newProject'),
          },
          {
            path: 'projects/:projectId',
            element: page(<ProjectLayout />),
            children: [
              { index: true, element: page(<ProjectPage />), handle: section('overview') },
              { path: 'ask', element: page(<ChatPage />), handle: section('ask') },
              {
                path: 'conversations/:conversationId',
                element: page(<ChatPage />),
                handle: section('conversation'),
              },
              { path: 'history', element: page(<HistoryPage />), handle: section('history') },
              {
                path: 'settings',
                element: page(<ProjectSettingsPage />),
                handle: section('settings'),
              },
            ],
          },
          { path: 'system', element: page(<SystemPage />), handle: section('system') },
          ...catalogRoutes(),
          { path: '*', element: <NotFoundPage />, handle: section('notFound') },
        ],
      },
    ],
  },
];
