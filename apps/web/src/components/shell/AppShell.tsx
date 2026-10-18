import { useQuery } from '@tanstack/react-query';
import { lazy, Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router';
import { useRouteContext, type RouteContext } from '../../app/routeContext';
import { readStorage, writeStorage } from '../../app/storage';
import { getProject, importKeys } from '../../features/import/api';
import { messages } from '../../messages';
import { cx } from '../ui/cx';
import { useMediaQuery } from '../useMediaQuery';
import { RouteAnnouncer } from './RouteAnnouncer';
import { Sidebar } from './Sidebar';
import { LAST_WORKSPACE_KEY, SIDEBAR_COLLAPSED_KEY } from './storageKeys';
import { Topbar } from './Topbar';
import './AppShell.css';

const DESKTOP = '(min-width: 48rem)';
// Only phones use the drawer, so its dialog stays out of the first load.
const NavDrawer = lazy(async () => ({ default: (await import('./NavDrawer')).NavDrawer }));

export function AppShell() {
  const desktop = useMediaQuery(DESKTOP, true);
  const location = useLocation();
  const context = useRouteContext();
  const routeWorkspaceId = useRouteWorkspace(context);
  const [collapsed, setCollapsed] = useState(() => readStorage(SIDEBAR_COLLAPSED_KEY) === 'true');
  // Tied to the location, so any navigation closes the drawer.
  const [drawerKey, setDrawerKey] = useState<string | null>(null);

  // Outside a workspace or project, the sidebar keeps the last workspace opened.
  const sidebarWorkspaceId =
    routeWorkspaceId ?? (context.projectId ? null : readStorage(LAST_WORKSPACE_KEY));

  function toggleCollapsed() {
    const next = !collapsed;
    setCollapsed(next);
    writeStorage(SIDEBAR_COLLAPSED_KEY, next ? 'true' : null);
  }

  return (
    <>
      <a className="skip-link" href="#content">
        {messages.app.skipToContent}
      </a>
      <RouteAnnouncer />
      <div className={cx('shell', desktop ? collapsed && 'shell--collapsed' : 'shell--no-sidebar')}>
        {desktop && (
          <div className="shell__sidebar">
            <Sidebar
              context={context}
              workspaceId={sidebarWorkspaceId}
              collapsed={collapsed}
              onToggleCollapsed={toggleCollapsed}
            />
          </div>
        )}
        <div className="shell__body">
          <Topbar
            context={context}
            workspaceId={routeWorkspaceId}
            onOpenNav={desktop ? undefined : () => setDrawerKey(location.key)}
          />
          <main id="content" className="app-main" tabIndex={-1}>
            <Outlet />
          </main>
        </div>
      </div>
      {!desktop && drawerKey === location.key && (
        <Suspense fallback={null}>
          <NavDrawer
            open={drawerKey === location.key}
            onOpenChange={(open) => setDrawerKey(open ? location.key : null)}
          >
            <Sidebar context={context} workspaceId={sidebarWorkspaceId} collapsed={false} />
          </NavDrawer>
        </Suspense>
      )}
    </>
  );
}

function useRouteWorkspace(context: RouteContext): string | null {
  const project = useQuery({
    queryKey: importKeys.project(context.projectId ?? ''),
    queryFn: ({ signal }) => getProject(context.projectId ?? '', signal),
    enabled: context.projectId !== null,
  });
  const workspaceId =
    context.workspaceId ?? (context.projectId ? (project.data?.workspaceId ?? null) : null);
  useEffect(() => {
    if (workspaceId) {
      writeStorage(LAST_WORKSPACE_KEY, workspaceId);
    }
  }, [workspaceId]);
  return workspaceId;
}
