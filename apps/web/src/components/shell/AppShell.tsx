import { useQuery } from '@tanstack/react-query';
import { lazy, Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';
import { useRouteContext, type RouteContext } from '../../app/routeContext';
import { useShortcut, useShortcutListener } from '../../app/shortcuts';
import { readStorage, writeStorage } from '../../app/storage';
import { getProject, importKeys } from '../../features/import/api';
import { messages } from '../../messages';
import { cx } from '../ui/cx';
import { ToastProvider } from '../ui/Toast';
import { useMediaQuery } from '../useMediaQuery';
import { IndexingToasts } from './IndexingToasts';
import { RouteAnnouncer } from './RouteAnnouncer';
import { Sidebar } from './Sidebar';
import { LAST_WORKSPACE_KEY, SIDEBAR_COLLAPSED_KEY } from './storageKeys';
import { Topbar } from './Topbar';
import './AppShell.css';

const DESKTOP = '(min-width: 48rem)';
// Only phones use the drawer, so its dialog stays out of the first load.
const NavDrawer = lazy(async () => ({ default: (await import('./NavDrawer')).NavDrawer }));
// Loaded on first use, so cmdk stays out of the shell chunk.
const CommandPalette = lazy(async () => ({
  default: (await import('./CommandPalette')).CommandPalette,
}));
const ShortcutsDialog = lazy(async () => ({
  default: (await import('../shortcuts/ShortcutsDialog')).ShortcutsDialog,
}));

export function AppShell() {
  const desktop = useMediaQuery(DESKTOP, true);
  const location = useLocation();
  const context = useRouteContext();
  const routeWorkspaceId = useRouteWorkspace(context);
  const [collapsed, setCollapsed] = useState(() => readStorage(SIDEBAR_COLLAPSED_KEY) === 'true');
  // Tied to the location, so any navigation closes the drawer.
  const [drawerKey, setDrawerKey] = useState<string | null>(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [paletteLoaded, setPaletteLoaded] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  // Outside a workspace or project, the sidebar keeps the last workspace opened.
  const sidebarWorkspaceId =
    routeWorkspaceId ?? (context.projectId ? null : readStorage(LAST_WORKSPACE_KEY));

  function togglePalette() {
    setPaletteLoaded(true);
    setPaletteOpen((open) => !open);
  }

  useShortcutListener();
  useShortcut('openPalette', togglePalette);
  useShortcut('showShortcuts', () => setShortcutsOpen(true));
  useProjectShortcuts(context.projectId);

  function toggleCollapsed() {
    const next = !collapsed;
    setCollapsed(next);
    writeStorage(SIDEBAR_COLLAPSED_KEY, next ? 'true' : null);
  }

  return (
    <ToastProvider>
      {sidebarWorkspaceId && (
        <IndexingToasts key={sidebarWorkspaceId} workspaceId={sidebarWorkspaceId} />
      )}
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
            onOpenPalette={togglePalette}
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
      {paletteLoaded && (
        <Suspense fallback={null}>
          <CommandPalette
            open={paletteOpen}
            onOpenChange={setPaletteOpen}
            context={context}
            workspaceId={sidebarWorkspaceId}
            onShowShortcuts={() => setShortcutsOpen(true)}
          />
        </Suspense>
      )}
      {shortcutsOpen && (
        <Suspense fallback={null}>
          <ShortcutsDialog open onOpenChange={setShortcutsOpen} />
        </Suspense>
      )}
    </ToastProvider>
  );
}

function useProjectShortcuts(projectId: string | null) {
  const navigate = useNavigate();
  const enabled = projectId !== null;
  const base = `/projects/${projectId ?? ''}`;
  useShortcut('goOverview', () => void navigate(base, { viewTransition: true }), enabled);
  useShortcut('goAsk', () => void navigate(`${base}/ask`, { viewTransition: true }), enabled);
  useShortcut(
    'goHistory',
    () => void navigate(`${base}/history`, { viewTransition: true }),
    enabled,
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
