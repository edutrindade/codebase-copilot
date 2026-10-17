import type { RouteObject } from 'react-router';
import { Layout } from '../components/Layout';
import { NewProjectPage } from '../features/import/NewProjectPage';
import { NotFoundPage } from '../features/not-found/NotFoundPage';
import { ProjectPage } from '../features/project/ProjectPage';
import { PendingPage } from '../features/pending/PendingPage';
import { WorkspacePage } from '../features/workspaces/WorkspacePage';
import { WorkspacesPage } from '../features/workspaces/WorkspacesPage';
import { messages } from '../messages';
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
          {
            path: 'projects/:projectId/ask',
            element: <PendingPage title={messages.pages.ask} />,
          },
          {
            path: 'projects/:projectId/conversations/:conversationId',
            element: <PendingPage title={messages.pages.conversation} />,
          },
          {
            path: 'projects/:projectId/history',
            element: <PendingPage title={messages.pages.history} />,
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
