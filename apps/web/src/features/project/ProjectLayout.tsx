import { useQuery, useQueryClient } from '@tanstack/react-query';
import { SquarePen } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef } from 'react';
import { Link, Outlet, useLocation, useOutletContext, useParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { forgetProject, rememberProject } from '../../app/recentProjects';
import { useRouteContext, type RouteSection } from '../../app/routeContext';
import { usePageTitle } from '../../app/title';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ButtonLink } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatDateTime } from '../../format';
import { messages } from '../../messages';
import { getProject, importKeys } from '../import/api';
import type { Indexing } from './api';
import { workspaceKeys, type Project } from '../workspaces/api';
import { PROJECT_STATUS_TONE, projectSource, projectStatus } from '../workspaces/projectStatus';
import './ProjectLayout.css';

const ACTIVE_JOB_POLL_MS = 1000;

export function ProjectLayout() {
  const { projectId = '' } = useParams();
  const project = useQuery({
    queryKey: importKeys.project(projectId),
    queryFn: ({ signal }) => getProject(projectId, signal),
    refetchInterval: (query) => (query.state.data?.indexing ? ACTIVE_JOB_POLL_MS : false),
  });
  const notFound = project.error instanceof ApiError && project.error.code === 'PROJECT_NOT_FOUND';
  useRefreshWhenJobChanges(projectId, project.data?.indexing?.id ?? null);
  useIndexingTitle(project.data?.indexing ?? null);
  usePageTitle([messages.pages.project], !project.isSuccess);
  useEffect(() => {
    if (project.isSuccess) {
      rememberProject(projectId);
    } else if (notFound) {
      forgetProject(projectId);
    }
  }, [project.isSuccess, notFound, projectId]);

  if (project.isPending) {
    return (
      <section>
        <h1>{messages.pages.project}</h1>
        <Loading />
      </section>
    );
  }
  if (project.isError) {
    return (
      <section className="stack">
        <h1>{messages.pages.project}</h1>
        <ProblemAlert
          error={project.error}
          onRetry={notFound ? undefined : () => void project.refetch()}
        />
      </section>
    );
  }
  return (
    <div className="project-area">
      <ProjectHeader project={project.data} />
      <Outlet context={project.data} />
    </div>
  );
}

// The screens under the project area render only once the project has loaded.
export function useProject(): Project {
  return useOutletContext<Project>();
}

// Snapshots, jobs and counts only change when a job starts or ends.
function useRefreshWhenJobChanges(projectId: string, indexingId: string | null) {
  const queryClient = useQueryClient();
  const previous = useRef(indexingId);
  useEffect(() => {
    if (previous.current === indexingId) {
      return;
    }
    previous.current = indexingId;
    void queryClient.invalidateQueries({ queryKey: importKeys.project(projectId) });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }, [indexingId, projectId, queryClient]);
}

const TABS = ['overview', 'ask', 'history', 'settings'] as const;
type Tab = (typeof TABS)[number];

const TAB_PATHS: Record<Tab, string> = {
  overview: '',
  ask: '/ask',
  history: '/history',
  settings: '/settings',
};

function currentTab(section: RouteSection | null): Tab | null {
  return section === 'conversation' ? 'ask' : (TABS.find((tab) => tab === section) ?? null);
}

function ProjectHeader({ project }: { project: Project }) {
  const text = messages.projectArea;
  const { section } = useRouteContext();
  const status = projectStatus(project);
  const { activeSnapshot } = project;
  const active = currentTab(section);
  const inAsk = active === 'ask';
  return (
    <header className="project-header">
      <div className="project-header__row">
        <div className="project-header__identity">
          <p className="project-header__name">{project.name}</p>
          <div className="project-header__meta">
            <StatusBadge tone={PROJECT_STATUS_TONE[status]}>
              {messages.projects.status[status]}
            </StatusBadge>
            <span>{projectSource(project)}</span>
            <span>
              {activeSnapshot?.completedAt
                ? text.snapshot(formatDateTime(activeSnapshot.completedAt))
                : text.noSnapshot}
            </span>
          </div>
        </div>
        {activeSnapshot && !inAsk && (
          <ButtonLink to={`/projects/${project.id}/ask`} variant="primary" icon={SquarePen}>
            {text.newQuestion}
          </ButtonLink>
        )}
      </div>
      <nav aria-label={text.tabsLabel} className="project-tabs">
        <ul>
          {TABS.map((tab) => (
            <li key={tab}>
              <Link
                to={`/projects/${project.id}${TAB_PATHS[tab]}`}
                className="project-tabs__link"
                // A conversation lives under "Perguntar" without being that page.
                aria-current={
                  active === tab ? (section === 'conversation' ? 'true' : 'page') : undefined
                }
                viewTransition
              >
                {text.tabs[tab]}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </header>
  );
}

function indexingPercent({ progress }: Indexing): number | null {
  return progress && progress.total > 0
    ? Math.floor((progress.completed / progress.total) * 100)
    : null;
}

// Prefixes whatever title the screen set, and is applied after it (parents' effects run last).
function useIndexingTitle(indexing: Indexing | null) {
  const location = useLocation();
  const applied = useRef<string | null>(null);
  const prefix = indexing ? messages.project.progress.titlePrefix(indexingPercent(indexing)) : null;
  useLayoutEffect(() => {
    const current = document.title;
    const base =
      applied.current && current.startsWith(`${applied.current} `)
        ? current.slice(applied.current.length + 1)
        : current;
    document.title = prefix ? `${prefix} ${base}` : base;
    applied.current = prefix;
  }, [prefix, location.key]);
  useEffect(
    () => () => {
      if (applied.current && document.title.startsWith(`${applied.current} `)) {
        document.title = document.title.slice(applied.current.length + 1);
      }
    },
    [],
  );
}
