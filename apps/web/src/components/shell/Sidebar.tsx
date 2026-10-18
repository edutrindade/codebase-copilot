import { useQuery } from '@tanstack/react-query';
import {
  Boxes,
  Check,
  ChevronsUpDown,
  LayoutGrid,
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useId, type ReactElement, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../../api/errors';
import type { RouteContext } from '../../app/routeContext';
import { readStorage, writeStorage } from '../../app/storage';
import { askKeys, listConversations } from '../../features/ask/api';
import {
  getWorkspace,
  listProjects,
  listWorkspaces,
  workspaceKeys,
  type Project,
} from '../../features/workspaces/api';
import { PROJECT_STATUS_TONE, projectStatus } from '../../features/workspaces/projectStatus';
import { messages } from '../../messages';
import { BrandMark } from '../ui/BrandMark';
import { Button } from '../ui/Button';
import { cx } from '../ui/cx';
import { Menu, MenuItem, MenuLabel, MenuSeparator } from '../ui/Menu';
import { StatusIcon } from '../ui/StatusBadge';
import { Tooltip } from '../ui/Tooltip';
import { LAST_WORKSPACE_KEY } from './storageKeys';

const ACTIVE_JOB_POLL_MS = 1000;
const RECENT_CONVERSATIONS = 5;

interface SidebarProps {
  context: RouteContext;
  workspaceId: string | null;
  collapsed: boolean;
  onToggleCollapsed?: () => void;
}

export function Sidebar({ context, workspaceId, collapsed, onToggleCollapsed }: SidebarProps) {
  const text = messages.shell;
  const workspace = useQuery({
    queryKey: workspaceKeys.detail(workspaceId ?? ''),
    queryFn: ({ signal }) => getWorkspace(workspaceId ?? '', signal),
    enabled: workspaceId !== null,
  });
  const missing =
    workspace.error instanceof ApiError && workspace.error.code === 'WORKSPACE_NOT_FOUND';
  useEffect(() => {
    if (missing && workspaceId && readStorage(LAST_WORKSPACE_KEY) === workspaceId) {
      writeStorage(LAST_WORKSPACE_KEY, null);
    }
  }, [missing, workspaceId]);
  const activeWorkspaceId = missing ? null : workspaceId;

  return (
    <nav aria-label={text.mainNav} className={cx('sidebar', collapsed && 'sidebar--collapsed')}>
      <WithTooltip show={collapsed} content={messages.app.name}>
        <Link to="/" className="sidebar__brand" viewTransition>
          <BrandMark />
          <span className="sidebar__label">{messages.app.name}</span>
        </Link>
      </WithTooltip>
      <WorkspaceSwitcher
        workspaceId={activeWorkspaceId}
        name={workspace.data?.name ?? null}
        collapsed={collapsed}
      />
      {activeWorkspaceId && (
        <ProjectList workspaceId={activeWorkspaceId} context={context} collapsed={collapsed} />
      )}
      {context.projectId && !collapsed && (
        <RecentConversations
          projectId={context.projectId}
          conversationId={context.conversationId}
        />
      )}
      <div className="sidebar__footer">
        <SidebarLink
          to="/system"
          icon={ShieldCheck}
          current={context.section === 'system'}
          collapsed={collapsed}
        >
          {text.system}
        </SidebarLink>
        {onToggleCollapsed && (
          <WithTooltip show={collapsed} content={collapsed ? text.expand : text.collapse}>
            <button type="button" className="sidebar__link" onClick={onToggleCollapsed}>
              {collapsed ? (
                <PanelLeftOpen aria-hidden="true" className="sidebar__icon" />
              ) : (
                <PanelLeftClose aria-hidden="true" className="sidebar__icon" />
              )}
              <span className="sidebar__label">{collapsed ? text.expand : text.collapse}</span>
            </button>
          </WithTooltip>
        )}
      </div>
    </nav>
  );
}

interface WorkspaceSwitcherProps {
  workspaceId: string | null;
  name: string | null;
  collapsed: boolean;
}

function WorkspaceSwitcher({ workspaceId, name, collapsed }: WorkspaceSwitcherProps) {
  const text = messages.shell;
  return (
    <Menu
      align="start"
      trigger={
        <button type="button" className="sidebar__switcher">
          <Boxes aria-hidden="true" className="sidebar__icon" />
          <span className="sidebar__label sidebar__switcher-text">
            {name && (
              <span className="sidebar__eyebrow">
                {text.workspaceFallback}
                <span className="visually-hidden">: </span>
              </span>
            )}
            <span className="sidebar__switcher-name">{name ?? text.chooseWorkspace}</span>
          </span>
          {!collapsed && <ChevronsUpDown aria-hidden="true" className="sidebar__chevron" />}
        </button>
      }
    >
      <WorkspaceMenuItems currentId={workspaceId} />
    </Menu>
  );
}

// Rendered only while the menu is open, so the list is fetched on demand.
function WorkspaceMenuItems({ currentId }: { currentId: string | null }) {
  const navigate = useNavigate();
  const workspaces = useQuery({
    queryKey: workspaceKeys.list(0),
    queryFn: ({ signal }) => listWorkspaces(0, signal),
  });
  return (
    <>
      <MenuLabel>{messages.shell.workspacesMenu}</MenuLabel>
      {workspaces.isPending && (
        <MenuItem disabled onSelect={() => {}}>
          {messages.loading}
        </MenuItem>
      )}
      {workspaces.isError && (
        <MenuItem disabled onSelect={() => {}}>
          {messages.shell.loadFailed}
        </MenuItem>
      )}
      {workspaces.data?.items.map((workspace) => (
        <MenuItem
          key={workspace.id}
          icon={workspace.id === currentId ? Check : undefined}
          onSelect={() => void navigate(`/workspaces/${workspace.id}`)}
        >
          {workspace.name}
        </MenuItem>
      ))}
      <MenuSeparator />
      <MenuItem icon={LayoutGrid} onSelect={() => void navigate('/')}>
        {messages.shell.allWorkspaces}
      </MenuItem>
    </>
  );
}

interface ProjectListProps {
  workspaceId: string;
  context: RouteContext;
  collapsed: boolean;
}

function ProjectList({ workspaceId, context, collapsed }: ProjectListProps) {
  const text = messages.shell;
  const headingId = useId();
  const projects = useQuery({
    queryKey: workspaceKeys.projects(workspaceId, 0),
    queryFn: ({ signal }) => listProjects(workspaceId, 0, signal),
    refetchInterval: (query) =>
      query.state.data?.items.some((project) => project.indexing) ? ACTIVE_JOB_POLL_MS : false,
  });
  return (
    <div className="sidebar__section">
      <p id={headingId} className="sidebar__heading sidebar__label">
        {text.projects}
      </p>
      {projects.isError && !collapsed && (
        <div className="sidebar__error">
          <p>{text.loadFailed}</p>
          <Button size="sm" variant="ghost" onClick={() => void projects.refetch()}>
            {text.retryProjects}
          </Button>
        </div>
      )}
      {projects.isSuccess && projects.data.items.length > 0 && (
        <ul className="sidebar__list" aria-labelledby={headingId}>
          {projects.data.items.map((project) => (
            <li key={project.id}>
              <ProjectLink project={project} context={context} collapsed={collapsed} />
            </li>
          ))}
        </ul>
      )}
      {projects.isSuccess && projects.data.totalPages > 1 && (
        <SidebarLink to={`/workspaces/${workspaceId}`} icon={LayoutGrid} collapsed={collapsed}>
          {text.moreProjects}
        </SidebarLink>
      )}
      <SidebarLink
        to={`/workspaces/${workspaceId}/new`}
        icon={Plus}
        current={context.section === 'newProject'}
        collapsed={collapsed}
      >
        {text.newProject}
      </SidebarLink>
    </div>
  );
}

interface ProjectLinkProps {
  project: Project;
  context: RouteContext;
  collapsed: boolean;
}

function ProjectLink({ project, context, collapsed }: ProjectLinkProps) {
  const status = projectStatus(project);
  const tone = PROJECT_STATUS_TONE[status];
  const progress = project.indexing?.progress;
  const current = context.projectId === project.id;
  return (
    <WithTooltip show={collapsed} content={project.name}>
      <Link
        to={`/projects/${project.id}`}
        className="sidebar__link sidebar__project"
        aria-current={current ? (context.section === 'overview' ? 'page' : 'true') : undefined}
        viewTransition
      >
        <StatusIcon tone={tone} className={`sidebar__status sidebar__status--${tone}`} />
        <span className="sidebar__label sidebar__stacked">
          <span className="sidebar__truncate">{project.name}</span>
          <span className="visually-hidden">, </span>
          <span className="sidebar__meta">
            {messages.projects.status[status]}
            {progress &&
              progress.total > 0 &&
              ` · ${messages.projects.progress(progress.completed, progress.total)}`}
          </span>
        </span>
      </Link>
    </WithTooltip>
  );
}

function RecentConversations({
  projectId,
  conversationId,
}: {
  projectId: string;
  conversationId: string | null;
}) {
  const headingId = useId();
  const conversations = useQuery({
    queryKey: askKeys.conversationsPage(projectId, 0),
    queryFn: ({ signal }) => listConversations(projectId, 0, signal),
  });
  const items = conversations.data?.items.slice(0, RECENT_CONVERSATIONS) ?? [];
  if (items.length === 0) {
    return null;
  }
  return (
    <div className="sidebar__section">
      <p id={headingId} className="sidebar__heading">
        {messages.shell.conversations}
      </p>
      <ul className="sidebar__list" aria-labelledby={headingId}>
        {items.map((conversation) => (
          <li key={conversation.id}>
            <Link
              to={`/projects/${projectId}/conversations/${conversation.id}`}
              className="sidebar__link"
              aria-current={conversation.id === conversationId ? 'page' : undefined}
              viewTransition
            >
              <MessageSquare aria-hidden="true" className="sidebar__icon" />
              <span className="sidebar__label sidebar__truncate">{conversation.title}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

interface SidebarLinkProps {
  to: string;
  icon: LucideIcon;
  current?: boolean;
  collapsed: boolean;
  children: string;
}

function SidebarLink({ to, icon: Icon, current = false, collapsed, children }: SidebarLinkProps) {
  return (
    <WithTooltip show={collapsed} content={children}>
      <Link
        to={to}
        className="sidebar__link"
        aria-current={current ? 'page' : undefined}
        viewTransition
      >
        <Icon aria-hidden="true" className="sidebar__icon" />
        <span className="sidebar__label">{children}</span>
      </Link>
    </WithTooltip>
  );
}

// Collapsed, the labels are only read by screen readers; the tooltip shows them on hover and focus.
function WithTooltip({
  show,
  content,
  children,
}: {
  show: boolean;
  content: ReactNode;
  children: ReactElement;
}) {
  return show ? (
    <Tooltip content={content} side="right">
      {children}
    </Tooltip>
  ) : (
    children
  );
}
