import type { RouteObject } from 'react-router';
import { Layout } from '../components/Layout';
import { ChatPage } from '../features/ask/ChatPage';
import { HistoryPage } from '../features/history/HistoryPage';
import { NewProjectPage } from '../features/import/NewProjectPage';
import { NotFoundPage } from '../features/not-found/NotFoundPage';
import { ProjectPage } from '../features/project/ProjectPage';
import { WorkspacePage } from '../features/workspaces/WorkspacePage';
import { WorkspacesPage } from '../features/workspaces/WorkspacesPage';
import { RouteError } from './RouteError';

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        errorElement: <RouteError />,
        children: [
          { index: true, element: <WorkspacesPage /> },
          { path: 'workspaces/:workspaceId', element: <WorkspacePage /> },
          { path: 'workspaces/:workspaceId/new', element: <NewProjectPage /> },
          { path: 'projects/:projectId', element: <ProjectPage /> },
          { path: 'projects/:projectId/ask', element: <ChatPage /> },
          { path: 'projects/:projectId/conversations/:conversationId', element: <ChatPage /> },
          { path: 'projects/:projectId/history', element: <HistoryPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
