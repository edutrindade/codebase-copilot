import { useQuery } from '@tanstack/react-query';
import { Cloud, HardDrive, PanelLeft, Search } from 'lucide-react';
import { Link } from 'react-router';
import type { RouteContext, RouteSection } from '../../app/routeContext';
import { isApplePlatform } from '../../app/shortcuts';
import { askKeys, getConversation } from '../../features/ask/api';
import { getProject, getProviders, importKeys } from '../../features/import/api';
import { getWorkspace, workspaceKeys } from '../../features/workspaces/api';
import { messages } from '../../messages';
import { ThemeMenu } from '../ThemeMenu';
import { Breadcrumbs, type Crumb } from '../ui/Breadcrumbs';
import { cx } from '../ui/cx';
import { IconButton } from '../ui/IconButton';
import { Kbd } from '../ui/Kbd';

interface TopbarProps {
  context: RouteContext;
  workspaceId: string | null;
  onOpenNav?: () => void;
  onOpenPalette: () => void;
}

export function Topbar({ context, workspaceId, onOpenNav, onOpenPalette }: TopbarProps) {
  const crumbs = useCrumbs(context, workspaceId);
  return (
    <header className="topbar">
      {onOpenNav && (
        <IconButton
          label={messages.shell.openNav}
          icon={PanelLeft}
          tooltip={false}
          onClick={onOpenNav}
        />
      )}
      <Breadcrumbs items={crumbs} />
      <div className="topbar__actions">
        <PaletteButton onClick={onOpenPalette} />
        <ProviderIndicator />
        <ThemeMenu />
      </div>
    </header>
  );
}

// The key hint sits outside the button: its name must contain all of its visible text (WCAG 2.5.3).
function PaletteButton({ onClick }: { onClick: () => void }) {
  const text = messages.shell;
  return (
    <div className="palette-button">
      <button
        type="button"
        className="palette-button__trigger"
        aria-label={text.paletteLabel}
        aria-keyshortcuts="Meta+K Control+K"
        onClick={onClick}
      >
        <Search aria-hidden="true" className="palette-button__icon" />
        <span className="palette-button__text">{text.palette}</span>
      </button>
      <span className="palette-button__keys" aria-hidden="true">
        <Kbd>{isApplePlatform() ? '⌘' : 'Ctrl'}</Kbd>
        <Kbd>K</Kbd>
      </span>
    </div>
  );
}

function ProviderIndicator() {
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  if (!providers.isSuccess) {
    return null;
  }
  const text = messages.shell.providers;
  const remote = providers.data.embeddings.remote || providers.data.generation.remote;
  const mode = remote ? text.remote : text.local;
  const Icon = remote ? Cloud : HardDrive;
  return (
    <Link
      to="/system"
      className={cx('provider-indicator', remote && 'provider-indicator--remote')}
      aria-label={text.label(mode)}
    >
      <Icon aria-hidden="true" className="provider-indicator__icon" />
      {mode}
    </Link>
  );
}

function sectionLabel(section: RouteSection | null, conversationTitle?: string): string | null {
  switch (section) {
    case 'overview':
      return messages.pages.overview;
    case 'ask':
      return messages.pages.ask;
    case 'conversation':
      return conversationTitle ?? messages.pages.conversation;
    case 'files':
      return messages.pages.files;
    case 'history':
      return messages.pages.history;
    case 'settings':
      return messages.pages.settings;
    default:
      return null;
  }
}

function useCrumbs(context: RouteContext, workspaceId: string | null): Crumb[] {
  const { section, projectId, conversationId } = context;
  const workspace = useQuery({
    queryKey: workspaceKeys.detail(workspaceId ?? ''),
    queryFn: ({ signal }) => getWorkspace(workspaceId ?? '', signal),
    enabled: workspaceId !== null,
  });
  const project = useQuery({
    queryKey: importKeys.project(projectId ?? ''),
    queryFn: ({ signal }) => getProject(projectId ?? '', signal),
    enabled: projectId !== null,
  });
  const conversation = useQuery({
    queryKey: askKeys.conversation(conversationId ?? ''),
    queryFn: ({ signal }) => getConversation(conversationId ?? '', signal),
    enabled: conversationId !== null,
  });

  const crumbs: Crumb[] = [{ label: messages.pages.workspaces, to: '/' }];
  if (section === 'system') {
    crumbs.push({ label: messages.pages.system });
  }
  if (workspaceId) {
    crumbs.push({
      label: workspace.data?.name ?? messages.shell.workspaceFallback,
      to: `/workspaces/${workspaceId}`,
    });
  }
  if (section === 'newProject') {
    crumbs.push({ label: messages.pages.newProject });
  }
  if (projectId) {
    crumbs.push({
      label: project.data?.name ?? messages.shell.projectFallback,
      to: `/projects/${projectId}`,
    });
    const label = sectionLabel(section, conversation.data?.conversation.title);
    if (label) {
      crumbs.push({ label });
    }
  }
  if (section === 'notFound') {
    crumbs.push({ label: messages.notFound.title });
  }
  return crumbs;
}
