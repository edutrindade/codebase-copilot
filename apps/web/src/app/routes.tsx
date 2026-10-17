import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { Layout } from '../components/Layout';
import { Loading } from '../components/Loading';
import { NotFoundPage } from '../features/not-found/NotFoundPage';
import { RouteError } from './RouteError';

// One chunk per screen, so the first load stays under 500 kB (P8 of Etapa 8).
function screen(load: () => Promise<Record<string, ComponentType>>, name: string) {
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

function page(element: ReactNode) {
  return <Suspense fallback={<Loading />}>{element}</Suspense>;
}

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        errorElement: <RouteError />,
        children: [
          { index: true, element: page(<WorkspacesPage />) },
          { path: 'workspaces/:workspaceId', element: page(<WorkspacePage />) },
          { path: 'workspaces/:workspaceId/new', element: page(<NewProjectPage />) },
          { path: 'projects/:projectId', element: page(<ProjectPage />) },
          { path: 'projects/:projectId/ask', element: page(<ChatPage />) },
          {
            path: 'projects/:projectId/conversations/:conversationId',
            element: page(<ChatPage />),
          },
          { path: 'projects/:projectId/history', element: page(<HistoryPage />) },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
