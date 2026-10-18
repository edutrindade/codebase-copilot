import { useQuery } from '@tanstack/react-query';
import { Command, defaultFilter } from 'cmdk';
import {
  Boxes,
  FolderGit2,
  History,
  Keyboard,
  LayoutDashboard,
  LayoutGrid,
  MessageSquare,
  Monitor,
  Moon,
  Plus,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  SquarePen,
  Sun,
  type LucideIcon,
} from 'lucide-react';
import { Dialog as RadixDialog } from 'radix-ui';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import type { RouteContext } from '../../app/routeContext';
import { setThemePreference, useThemePreference, type ThemePreference } from '../../app/theme';
import { askKeys, listConversations } from '../../features/ask/api';
import { getProject, importKeys } from '../../features/import/api';
import { ReindexDialog } from '../../features/project/ReindexDialog';
import {
  getWorkspace,
  listProjects,
  listWorkspaces,
  workspaceKeys,
  type Project,
} from '../../features/workspaces/api';
import { projectStatus } from '../../features/workspaces/projectStatus';
import { messages } from '../../messages';
import { Kbd } from '../ui/Kbd';
import { useReturnFocus } from '../ui/returnFocus';
import './CommandPalette.css';

const THEMES: { value: ThemePreference; icon: LucideIcon }[] = [
  { value: 'light', icon: Sun },
  { value: 'dark', icon: Moon },
  { value: 'system', icon: Monitor },
];

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  context: RouteContext;
  workspaceId: string | null;
  onShowShortcuts: () => void;
}

export function CommandPalette({
  open,
  onOpenChange,
  context,
  workspaceId,
  onShowShortcuts,
}: CommandPaletteProps) {
  const [reindexing, setReindexing] = useState<Project | null>(null);
  // A navigation moves focus to the new screen's heading; returning it would undo that.
  const navigated = useRef(false);
  const returnFocus = useReturnFocus(() => navigated.current);
  return (
    <>
      <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
        <RadixDialog.Portal>
          <RadixDialog.Overlay className="dialog-overlay" />
          <RadixDialog.Content
            className="palette"
            aria-describedby={undefined}
            onOpenAutoFocus={() => {
              navigated.current = false;
              returnFocus.onOpenAutoFocus();
            }}
            onCloseAutoFocus={returnFocus.onCloseAutoFocus}
          >
            <RadixDialog.Title className="visually-hidden">
              {messages.palette.title}
            </RadixDialog.Title>
            <PaletteCommands
              context={context}
              workspaceId={workspaceId}
              onDone={(how) => {
                navigated.current = how === 'navigated';
                onOpenChange(false);
              }}
              onReindex={setReindexing}
              onShowShortcuts={onShowShortcuts}
            />
          </RadixDialog.Content>
        </RadixDialog.Portal>
      </RadixDialog.Root>
      {reindexing && <ReindexDialog project={reindexing} onClose={() => setReindexing(null)} />}
    </>
  );
}

// Matches on the visible text only: the value is an id, and hex digits would match almost anything.
function filter(_value: string, search: string, keywords?: string[]) {
  return defaultFilter(keywords?.join(' ') ?? '', search);
}

interface PaletteCommandsProps {
  context: RouteContext;
  workspaceId: string | null;
  onDone: (how: 'navigated' | 'stayed') => void;
  onReindex: (project: Project) => void;
  onShowShortcuts: () => void;
}

function PaletteCommands({
  context,
  workspaceId,
  onDone,
  onReindex,
  onShowShortcuts,
}: PaletteCommandsProps) {
  const text = messages.palette;
  const navigate = useNavigate();
  const theme = useThemePreference();
  const { projectId } = context;
  const project = useQuery({
    queryKey: importKeys.project(projectId ?? ''),
    queryFn: ({ signal }) => getProject(projectId ?? '', signal),
    enabled: projectId !== null,
  });
  const conversations = useQuery({
    queryKey: askKeys.conversationsPage(projectId ?? '', 0),
    queryFn: ({ signal }) => listConversations(projectId ?? '', 0, signal),
    enabled: projectId !== null,
  });
  const workspace = useQuery({
    queryKey: workspaceKeys.detail(workspaceId ?? ''),
    queryFn: ({ signal }) => getWorkspace(workspaceId ?? '', signal),
    enabled: workspaceId !== null,
  });
  const projects = useQuery({
    queryKey: workspaceKeys.projects(workspaceId ?? '', 0),
    queryFn: ({ signal }) => listProjects(workspaceId ?? '', 0, signal),
    enabled: workspaceId !== null,
  });
  const workspaces = useQuery({
    queryKey: workspaceKeys.list(0),
    queryFn: ({ signal }) => listWorkspaces(0, signal),
  });
  const queries = [project, conversations, workspace, projects, workspaces];
  const loading = queries.some((query) => query.isLoading);
  const failed = queries.some((query) => query.isError);

  function go(to: string) {
    onDone('navigated');
    void navigate(to, { viewTransition: true });
  }

  const current = project.data;
  const base = current ? `/projects/${current.id}` : '';
  const sections = messages.projectArea.tabs;

  return (
    <Command
      label={text.label}
      filter={filter}
      vimBindings={false}
      loop
      className="palette__command"
    >
      <div className="palette__search">
        <Search aria-hidden="true" className="palette__search-icon" />
        <Command.Input className="palette__input" placeholder={text.placeholder} />
      </div>
      {/* Outside the list: a listbox may only hold options and groups. */}
      <p className="palette__status" role="status">
        {loading ? text.loading : failed ? text.loadFailed : ''}
      </p>
      <Command.List className="palette__list">
        <Command.Empty className="palette__empty">{text.empty}</Command.Empty>
        {current && (
          <Command.Group heading={text.groups.project(current.name)}>
            <Item id="section:overview" icon={LayoutDashboard} onSelect={() => go(base)}>
              {sections.overview}
            </Item>
            {current.activeSnapshot && (
              <Item id="section:ask" icon={SquarePen} onSelect={() => go(`${base}/ask`)}>
                {sections.ask}
              </Item>
            )}
            <Item id="section:history" icon={History} onSelect={() => go(`${base}/history`)}>
              {sections.history}
            </Item>
            <Item id="section:settings" icon={Settings} onSelect={() => go(`${base}/settings`)}>
              {sections.settings}
            </Item>
            {current.activeSnapshot && !current.indexing && (
              <Item
                id="action:reindex"
                icon={RefreshCw}
                onSelect={() => {
                  onDone('stayed');
                  onReindex(current);
                }}
              >
                {text.reindex}
              </Item>
            )}
          </Command.Group>
        )}
        {current && conversations.data && conversations.data.items.length > 0 && (
          <Command.Group heading={text.groups.conversations}>
            {conversations.data.items.map((conversation) => (
              <Item
                key={conversation.id}
                id={`conversation:${conversation.id}`}
                icon={MessageSquare}
                onSelect={() => go(`${base}/conversations/${conversation.id}`)}
              >
                {conversation.title}
              </Item>
            ))}
          </Command.Group>
        )}
        {workspace.data && projects.data && projects.data.items.length > 0 && (
          <Command.Group heading={text.groups.projects(workspace.data.name)}>
            {projects.data.items.map((item) => (
              <Item
                key={item.id}
                id={`project:${item.id}`}
                icon={FolderGit2}
                hint={messages.projects.status[projectStatus(item)]}
                onSelect={() => go(`/projects/${item.id}`)}
              >
                {item.name}
              </Item>
            ))}
          </Command.Group>
        )}
        {workspaces.data && workspaces.data.items.length > 0 && (
          <Command.Group heading={text.groups.workspaces}>
            {workspaces.data.items.map((item) => (
              <Item
                key={item.id}
                id={`workspace:${item.id}`}
                icon={Boxes}
                hint={item.id === workspaceId ? text.current : undefined}
                onSelect={() => go(`/workspaces/${item.id}`)}
              >
                {item.name}
              </Item>
            ))}
          </Command.Group>
        )}
        <Command.Group heading={text.groups.actions}>
          {workspaceId && (
            <Item
              id="action:new-project"
              icon={Plus}
              onSelect={() => go(`/workspaces/${workspaceId}/new`)}
            >
              {text.newProject}
            </Item>
          )}
          <Item id="action:workspaces" icon={LayoutGrid} onSelect={() => go('/')}>
            {text.allWorkspaces}
          </Item>
          <Item id="action:system" icon={ShieldCheck} onSelect={() => go('/system')}>
            {text.system}
          </Item>
          <Item
            id="action:shortcuts"
            icon={Keyboard}
            onSelect={() => {
              onDone('stayed');
              onShowShortcuts();
            }}
          >
            {text.shortcuts}
          </Item>
          {THEMES.filter((option) => option.value !== theme).map((option) => (
            <Item
              key={option.value}
              id={`theme:${option.value}`}
              icon={option.icon}
              onSelect={() => {
                setThemePreference(option.value);
                onDone('stayed');
              }}
            >
              {text.themes[option.value]}
            </Item>
          ))}
        </Command.Group>
      </Command.List>
      <div className="palette__footer" aria-hidden="true">
        <span>
          <Kbd>↑</Kbd>
          <Kbd>↓</Kbd> {text.hints.navigate}
        </span>
        <span>
          <Kbd>↵</Kbd> {text.hints.open}
        </span>
        <span>
          <Kbd>Esc</Kbd> {text.hints.close}
        </span>
      </div>
    </Command>
  );
}

interface ItemProps {
  id: string;
  icon: LucideIcon;
  hint?: string;
  onSelect: () => void;
  children: string;
}

function Item({ id, icon: Icon, hint, onSelect, children }: ItemProps) {
  return (
    <Command.Item
      value={id}
      keywords={hint ? [children, hint] : [children]}
      onSelect={onSelect}
      className="palette__item"
    >
      <Icon aria-hidden="true" className="palette__icon" />
      <span className="palette__label">{children}</span>
      {hint && <span className="palette__hint">{hint}</span>}
    </Command.Item>
  );
}
