import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { messages } from '../../messages';
import type { Project } from '../workspaces/api';
import { getInventory, getSettings, importKeys, type Settings } from './api';
import { IndexPanel } from './IndexPanel';
import { InventorySummary } from './InventorySummary';
import { SettingsForm } from './SettingsForm';
import { isDirty, toDraft } from './settingsDraft';
import { StepIndicator } from './StepIndicator';

export function ReviewStep({ project }: { project: Project }) {
  const settings = useQuery({
    queryKey: importKeys.settings(project.id),
    queryFn: ({ signal }) => getSettings(project.id, signal),
  });
  const [saved, setSaved] = useState(false);

  return (
    <div className="stack">
      <StepIndicator sourceType={project.source.type} current="review" />
      <InventoryPanel projectId={project.id} />
      {saved && <p role="status">{messages.settings.saved}</p>}
      {settings.isPending && <Loading />}
      {settings.isError && (
        <ProblemAlert error={settings.error} onRetry={() => void settings.refetch()} />
      )}
      {settings.isSuccess && (
        // A saved version remounts the draft from the server's normalized settings.
        <SettingsAndIndex
          key={settings.data.version}
          projectId={project.id}
          settings={settings.data}
          onSaved={() => setSaved(true)}
        />
      )}
    </div>
  );
}

function InventoryPanel({ projectId }: { projectId: string }) {
  const inventory = useQuery({
    queryKey: importKeys.inventory(projectId),
    queryFn: ({ signal }) => getInventory(projectId, signal),
  });

  return (
    <section className="stack panel" aria-labelledby="pre-analysis-title">
      <div className="page-header">
        <h2 id="pre-analysis-title">{messages.preAnalysis.title}</h2>
        <button
          type="button"
          className="button"
          disabled={inventory.isFetching}
          onClick={() => void inventory.refetch()}
        >
          {messages.preAnalysis.refresh}
        </button>
      </div>
      <p>{messages.preAnalysis.detail}</p>
      {inventory.isPending && <Loading />}
      {inventory.isError && (
        <ProblemAlert error={inventory.error} onRetry={() => void inventory.refetch()} />
      )}
      {inventory.isSuccess && <InventorySummary inventory={inventory.data} />}
    </section>
  );
}

interface SettingsAndIndexProps {
  projectId: string;
  settings: Settings;
  onSaved: () => void;
}

function SettingsAndIndex({ projectId, settings, onSaved }: SettingsAndIndexProps) {
  const [draft, setDraft] = useState(() => toDraft(settings));
  const inventory = useQuery({
    queryKey: importKeys.inventory(projectId),
    queryFn: ({ signal }) => getInventory(projectId, signal),
  });

  return (
    <>
      <SettingsForm
        projectId={projectId}
        settings={settings}
        draft={draft}
        onDraftChange={setDraft}
        onDiscard={() => setDraft(toDraft(settings))}
        onSaved={onSaved}
      />
      <IndexPanel
        projectId={projectId}
        inventory={inventory.isFetching ? undefined : inventory.data}
        settingsDirty={isDirty(draft, settings)}
      />
    </>
  );
}
