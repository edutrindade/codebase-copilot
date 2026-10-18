import type { Tone } from '../../components/ui/StatusBadge';
import { messages } from '../../messages';
import type { Project } from './api';

export type ProjectStatus =
  'INDEXING' | 'FAILED' | 'AWAITING_UPLOAD' | 'NOT_INDEXED' | 'OUTDATED' | 'PARTIAL' | 'READY';

export function projectStatus(project: Project): ProjectStatus {
  if (project.indexing) {
    return 'INDEXING';
  }
  if (project.lastJob?.status === 'FAILED') {
    return 'FAILED';
  }
  if (!project.activeSnapshot) {
    return project.source.type === 'ZIP' && !project.source.currentImport
      ? 'AWAITING_UPLOAD'
      : 'NOT_INDEXED';
  }
  if (project.outdatedReasons.length > 0) {
    return 'OUTDATED';
  }
  return project.activeSnapshot.status === 'PARTIAL' ? 'PARTIAL' : 'READY';
}

export const PROJECT_STATUS_TONE: Record<ProjectStatus, Tone> = {
  INDEXING: 'progress',
  FAILED: 'danger',
  AWAITING_UPLOAD: 'neutral',
  NOT_INDEXED: 'neutral',
  OUTDATED: 'warning',
  PARTIAL: 'warning',
  READY: 'success',
};

export function projectSource(project: Project): string {
  const { source } = project;
  return source.type === 'LOCAL_FOLDER'
    ? messages.projects.sourceLocal(source.rootKey ?? '', source.relativePath ?? '')
    : messages.projects.sourceZip;
}
