import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { RefreshCw } from 'lucide-react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { messages } from '../../messages';
import type { Project } from '../workspaces/api';
import { getInventory, getSettings, importKeys, type Inventory, type Settings } from './api';
import { IndexPanel } from './IndexPanel';
import { InventorySummary } from './InventorySummary';
import { SETTINGS_SECTION_ID, SettingsForm } from './SettingsForm';
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
      {saved && <p role="status">{messages.settings.saved}</p>}
      {settings.isPending && <ReviewLayout projectId={project.id} main={<Loading />} />}
      {settings.isError && (
        <ReviewLayout
          projectId={project.id}
          main={<ProblemAlert error={settings.error} onRetry={() => void settings.refetch()} />}
        />
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

interface ReviewLayoutProps {
  projectId: string;
  main: ReactNode;
  side?: ReactNode;
}

// The pre-analysis and the exclusions on the left; what indexing does stays in view on the right.
function ReviewLayout({ projectId, main, side }: ReviewLayoutProps) {
  return (
    <div className="review">
      <div className="review__main stack">
        <InventoryPanel projectId={projectId} />
        {main}
      </div>
      {side && <div className="review__side">{side}</div>}
    </div>
  );
}

function InventoryPanel({ projectId }: { projectId: string }) {
  const inventory = useQuery({
    queryKey: importKeys.inventory(projectId),
    queryFn: ({ signal }) => getInventory(projectId, signal),
  });

  return (
    <Card as="section" className="stack" aria-labelledby="pre-analysis-title">
      <div className="page-header">
        <h2 id="pre-analysis-title">{messages.preAnalysis.title}</h2>
        <Button
          size="sm"
          icon={RefreshCw}
          disabled={inventory.isFetching}
          onClick={() => void inventory.refetch()}
        >
          {messages.preAnalysis.refresh}
        </Button>
      </div>
      <p>{messages.preAnalysis.detail}</p>
      {inventory.isPending && <Loading />}
      {inventory.isError && (
        <ProblemAlert error={inventory.error} onRetry={() => void inventory.refetch()} />
      )}
      {inventory.isSuccess && <InventorySummary inventory={inventory.data} />}
    </Card>
  );
}

// The index job would fail with these same codes, so the button is blocked up front.
const BLOCKING_WARNINGS = new Set([
  'NO_INCLUDED_FILES',
  'PROJECT_SIZE_LIMIT_EXCEEDED',
  'SCAN_LIMIT_REACHED',
]);

function blockedReason(inventory: Inventory | undefined, settingsDirty: boolean): string | null {
  if (settingsDirty) {
    return messages.indexing.dirty;
  }
  return inventory?.warnings.some((warning) => BLOCKING_WARNINGS.has(warning.code))
    ? messages.preAnalysis.blocking
    : null;
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
    <ReviewLayout
      projectId={projectId}
      main={
        <SettingsForm
          projectId={projectId}
          settings={settings}
          draft={draft}
          onDraftChange={setDraft}
          onDiscard={() => setDraft(toDraft(settings))}
          onSaved={onSaved}
        />
      }
      side={
        <IndexPanel
          projectId={projectId}
          title={messages.indexing.whatHappens}
          startLabel={messages.indexing.start}
          reviewHref={`#${SETTINGS_SECTION_ID}`}
          ready={inventory.isSuccess && !inventory.isFetching}
          blockedReason={blockedReason(inventory.data, isDirty(draft, settings))}
        />
      }
    />
  );
}
