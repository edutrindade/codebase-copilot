import { useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Clock, FileArchive, Folder, RefreshCw, SquarePen, Trash } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { MenuItem, MenuSeparator } from '../../components/ui/Menu';
import { Progress } from '../../components/ui/Progress';
import { RelativeTime } from '../../components/ui/RelativeTime';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Tile, TileMenu } from '../../components/ui/TileGrid';
import { messages } from '../../messages';
import { ReindexDialog } from '../project/ReindexDialog';
import { deleteProject, workspaceKeys, type Project } from './api';
import { PROJECT_STATUS_TONE, projectSource, projectStatus } from './projectStatus';

interface ProjectTileProps {
  project: Project;
  withActions?: boolean;
}

export function ProjectTile({ project, withActions = true }: ProjectTileProps) {
  const text = messages.projects;
  const [dialog, setDialog] = useState<'reindex' | 'delete' | null>(null);
  const status = projectStatus(project);
  const progress = project.indexing?.progress;
  const counted = progress != null && progress.total > 0;
  const snapshotAt = project.activeSnapshot?.completedAt;
  const to = `/projects/${project.id}`;

  return (
    <>
      <Tile
        to={to}
        title={project.name}
        icon={project.source.type === 'ZIP' ? FileArchive : Folder}
        menu={
          withActions && (
            <ProjectMenu project={project} to={to} onOpenDialog={(next) => setDialog(next)} />
          )
        }
      >
        <p className="tile__meta">
          <span className="tile__truncate">{projectSource(project)}</span>
        </p>
        <div className="tile__status">
          <StatusBadge tone={PROJECT_STATUS_TONE[status]}>{text.status[status]}</StatusBadge>
          {counted && <span>{text.progress(progress.completed, progress.total)}</span>}
        </div>
        {project.indexing && (
          <Progress
            value={counted ? progress.completed : null}
            max={counted ? progress.total : 100}
            label={text.progressLabel(project.name)}
          />
        )}
        <p className="tile__meta">
          <Clock aria-hidden="true" />
          {snapshotAt ? (
            <span>
              {text.snapshot} <RelativeTime iso={snapshotAt} />
            </span>
          ) : (
            <span>{text.noSnapshot}</span>
          )}
        </p>
      </Tile>
      {dialog === 'reindex' && <ReindexDialog project={project} onClose={() => setDialog(null)} />}
      {withActions && (
        <DeleteProjectDialog
          project={project}
          open={dialog === 'delete'}
          onOpenChange={(open) => setDialog(open ? 'delete' : null)}
        />
      )}
    </>
  );
}

interface ProjectMenuProps {
  project: Project;
  to: string;
  onOpenDialog: (dialog: 'reindex' | 'delete') => void;
}

function ProjectMenu({ project, to, onOpenDialog }: ProjectMenuProps) {
  const text = messages.projects;
  const navigate = useNavigate();
  const ready = project.activeSnapshot !== null;
  return (
    <TileMenu label={text.actions(project.name)}>
      <MenuItem icon={ArrowRight} onSelect={() => void navigate(to, { viewTransition: true })}>
        {text.open}
      </MenuItem>
      {ready && (
        <MenuItem
          icon={SquarePen}
          onSelect={() => void navigate(`${to}/ask`, { viewTransition: true })}
        >
          {text.ask}
        </MenuItem>
      )}
      {ready && !project.indexing && (
        <MenuItem icon={RefreshCw} onSelect={() => onOpenDialog('reindex')}>
          {text.reindex}
        </MenuItem>
      )}
      <MenuSeparator />
      <MenuItem icon={Trash} tone="danger" onSelect={() => onOpenDialog('delete')}>
        {text.deleteItem}
      </MenuItem>
    </TileMenu>
  );
}

interface DeleteProjectDialogProps {
  project: Project;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function DeleteProjectDialog({ project, open, onOpenChange }: DeleteProjectDialogProps) {
  const text = messages.projects;
  const queryClient = useQueryClient();

  async function remove() {
    await deleteProject(project.id);
    await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title={text.deleteTitle(project.name)}
      description={
        <>
          <p>{text.deleteDetail}</p>
          {project.indexing && <p>{text.deleteBusy}</p>}
        </>
      }
      confirmLabel={text.deleteConfirm}
      onConfirm={remove}
    />
  );
}
