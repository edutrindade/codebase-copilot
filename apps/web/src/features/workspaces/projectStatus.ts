import { messages } from '../../messages';
import type { Project } from './api';

export type ProjectStatus =
  'INDEXING' | 'AWAITING_UPLOAD' | 'NOT_INDEXED' | 'OUTDATED' | 'PARTIAL' | 'READY';

export function projectStatus(project: Project): ProjectStatus {
  if (project.indexing) {
    return 'INDEXING';
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

export function projectSource(project: Project): string {
  const { source } = project;
  return source.type === 'LOCAL_FOLDER'
    ? messages.projects.sourceLocal(source.rootKey ?? '', source.relativePath ?? '')
    : messages.projects.sourceZip;
}
