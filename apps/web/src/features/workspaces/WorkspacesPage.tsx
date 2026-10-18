import { keepPreviousData, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Boxes, Clock, FolderGit2, ShieldCheck, Trash } from 'lucide-react';
import { useId, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../../api/errors';
import { forgetProject, readRecentProjects } from '../../app/recentProjects';
import { usePageTitle } from '../../app/title';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Pagination, pageFromSearch } from '../../components/Pagination';
import { ProblemAlert } from '../../components/ProblemAlert';
import { BrandMark } from '../../components/ui/BrandMark';
import { Callout } from '../../components/ui/Callout';
import { Card } from '../../components/ui/Card';
import { MenuItem, MenuSeparator } from '../../components/ui/Menu';
import { PageHeader } from '../../components/ui/PageHeader';
import { RelativeTime } from '../../components/ui/RelativeTime';
import { Tile, TileGrid, TileGridSkeleton, TileMenu } from '../../components/ui/TileGrid';
import { messages } from '../../messages';
import { getProject } from '../import/api';
import {
  deleteWorkspace,
  listWorkspaces,
  workspaceKeys,
  type Project,
  type Workspace,
} from './api';
import { CreateWorkspaceForm } from './CreateWorkspaceForm';
import { AskIllustration, ImportIllustration, WorkspaceIllustration } from './illustrations';
import { ProjectTile } from './ProjectTile';
import './WorkspacesPage.css';

const STEP_ILLUSTRATIONS = [WorkspaceIllustration, ImportIllustration, AskIllustration];

export function WorkspacesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFromSearch(searchParams);
  const query = useQuery({
    queryKey: workspaceKeys.list(page),
    queryFn: ({ signal }) => listWorkspaces(page, signal),
    placeholderData: keepPreviousData,
  });
  const firstUse = query.isSuccess && query.data.totalItems === 0;
  usePageTitle([firstUse ? messages.workspaces.firstUseTitle : messages.workspaces.title]);
  const listId = useId();
  const newId = useId();

  if (query.isPending) {
    return (
      <section className="stack">
        <h1>{messages.workspaces.title}</h1>
        <TileGridSkeleton />
      </section>
    );
  }

  if (query.isError) {
    return (
      <section className="stack">
        <h1>{messages.workspaces.title}</h1>
        <ProblemAlert error={query.error} onRetry={() => void query.refetch()} />
      </section>
    );
  }

  if (query.data.totalItems === 0) {
    return <FirstUse />;
  }

  return (
    <section className="home">
      <PageHeader title={messages.workspaces.title} />
      <RecentProjects />
      <section className="stack" aria-labelledby={listId}>
        <h2 id={listId}>{messages.workspaces.listTitle}</h2>
        <TileGrid>
          {query.data.items.map((workspace) => (
            <WorkspaceTile key={workspace.id} workspace={workspace} />
          ))}
        </TileGrid>
        <Pagination
          page={page}
          totalPages={query.data.totalPages}
          onChange={(next) => setSearchParams({ page: String(next + 1) })}
        />
      </section>
      <Card as="section" className="stack home__new" aria-labelledby={newId}>
        <h2 id={newId}>{messages.workspaces.newTitle}</h2>
        <CreateWorkspaceForm />
      </Card>
    </section>
  );
}

function FirstUse() {
  const text = messages.workspaces;
  const formId = useId();
  return (
    <section className="welcome">
      <header className="welcome__hero">
        <span className="welcome__mark">
          <BrandMark />
        </span>
        <h1>{text.firstUseTitle}</h1>
        <p className="welcome__lead">{text.firstUseDetail}</p>
      </header>
      <ol className="welcome__steps" aria-label={text.stepsLabel}>
        {text.steps.map((step, index) => {
          const Illustration = STEP_ILLUSTRATIONS[index] ?? WorkspaceIllustration;
          return (
            <li key={step.title} className="welcome__step">
              <Illustration />
              <div className="welcome__step-text">
                <span className="welcome__number" aria-hidden="true">
                  {index + 1}
                </span>
                <h2 className="welcome__step-title">{step.title}</h2>
                <p>{step.detail}</p>
              </div>
            </li>
          );
        })}
      </ol>
      <Callout
        tone="info"
        icon={ShieldCheck}
        title={text.privacyTitle}
        className="welcome__privacy"
      >
        <p>{text.privacyDetail}</p>
      </Callout>
      <Card as="section" className="stack welcome__form" aria-labelledby={formId}>
        <h2 id={formId}>{text.newTitle}</h2>
        <CreateWorkspaceForm />
      </Card>
    </section>
  );
}

function WorkspaceTile({ workspace }: { workspace: Workspace }) {
  const text = messages.workspaces;
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [deleting, setDeleting] = useState(false);
  const to = `/workspaces/${workspace.id}`;

  async function remove() {
    await deleteWorkspace(workspace.id);
    await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }

  return (
    <>
      <Tile
        to={to}
        title={workspace.name}
        icon={Boxes}
        menu={
          <TileMenu label={text.actions(workspace.name)}>
            <MenuItem
              icon={ArrowRight}
              onSelect={() => void navigate(to, { viewTransition: true })}
            >
              {text.open}
            </MenuItem>
            <MenuSeparator />
            <MenuItem icon={Trash} tone="danger" onSelect={() => setDeleting(true)}>
              {text.deleteItem}
            </MenuItem>
          </TileMenu>
        }
      >
        <p className="tile__meta">
          <FolderGit2 aria-hidden="true" />
          <span>{text.projectCount(workspace.projectCount)}</span>
        </p>
        <p className="tile__meta">
          <Clock aria-hidden="true" />
          <span>
            {text.created} <RelativeTime iso={workspace.createdAt} />
          </span>
        </p>
      </Tile>
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title={text.deleteTitle(workspace.name)}
        description={
          <>
            <p>{text.deleteDetail}</p>
            {workspace.projectCount > 0 && <p>{text.deleteNotEmpty(workspace.projectCount)}</p>}
          </>
        }
        confirmLabel={text.deleteConfirm}
        onConfirm={remove}
      />
    </>
  );
}

// Checked against the API: a project removed elsewhere leaves the list without an error.
async function getRecentProject(projectId: string, signal: AbortSignal): Promise<Project | null> {
  try {
    return await getProject(projectId, signal);
  } catch (error) {
    if (error instanceof ApiError && error.code === 'PROJECT_NOT_FOUND') {
      forgetProject(projectId);
      return null;
    }
    throw error;
  }
}

function RecentProjects() {
  const text = messages.workspaces;
  const headingId = useId();
  const [ids] = useState(readRecentProjects);
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: ['recent-projects', id],
      queryFn: ({ signal }: { signal: AbortSignal }) => getRecentProject(id, signal),
    })),
  });
  const projects = results
    .map((result) => result.data)
    .filter((project): project is Project => Boolean(project));
  if (projects.length === 0) {
    return null;
  }
  return (
    <section className="stack" aria-labelledby={headingId}>
      <div>
        <h2 id={headingId}>{text.continueTitle}</h2>
        <p className="muted">{text.continueDetail}</p>
      </div>
      <TileGrid>
        {projects.map((project) => (
          <ProjectTile key={project.id} project={project} withActions={false} />
        ))}
      </TileGrid>
    </section>
  );
}
