import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { listProjects, workspaceKeys } from '../../features/workspaces/api';
import { messages } from '../../messages';
import { useToast } from '../ui/Toast';

const ACTIVE_JOB_POLL_MS = 1000;

// Shares the sidebar's query, so the end of an indexing is seen from any screen, even on phones.
export function IndexingToasts({ workspaceId }: { workspaceId: string }) {
  const notify = useToast();
  const projects = useQuery({
    queryKey: workspaceKeys.projects(workspaceId, 0),
    queryFn: ({ signal }) => listProjects(workspaceId, 0, signal),
    refetchInterval: (query) =>
      query.state.data?.items.some((project) => project.indexing) ? ACTIVE_JOB_POLL_MS : false,
  });
  const indexing = useRef<Set<string> | null>(null);

  useEffect(() => {
    const items = projects.data?.items;
    if (!items) {
      return;
    }
    const previous = indexing.current;
    indexing.current = new Set(items.filter((item) => item.indexing).map((item) => item.id));
    for (const project of items) {
      if (!previous?.has(project.id) || project.indexing) {
        continue;
      }
      // A cancellation was asked for on screen, so it gets no confirmation.
      if (project.lastJob?.status === 'COMPLETED') {
        notify({ tone: 'success', title: messages.toast.indexed, description: project.name });
      } else if (project.lastJob?.status === 'FAILED') {
        notify({ tone: 'danger', title: messages.toast.indexFailed, description: project.name });
      }
    }
  }, [projects.data, notify]);

  return null;
}
