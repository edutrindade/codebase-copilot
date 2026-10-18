import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { usePageTitle } from '../../app/title';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Callout } from '../../components/ui/Callout';
import { PageHeader } from '../../components/ui/PageHeader';
import { messages } from '../../messages';
import { getSettings, importKeys, type Settings } from '../import/api';
import { SettingsForm } from '../import/SettingsForm';
import { toDraft } from '../import/settingsDraft';
import { useProject } from './ProjectLayout';

export function ProjectSettingsPage() {
  const text = messages.projectSettings;
  const project = useProject();
  const queryClient = useQueryClient();
  const [saved, setSaved] = useState(false);
  usePageTitle([messages.pages.settings, project.name]);
  const settings = useQuery({
    queryKey: importKeys.settings(project.id),
    queryFn: ({ signal }) => getSettings(project.id, signal),
  });

  function onSaved() {
    setSaved(true);
    // The project now reports SETTINGS_CHANGED.
    void queryClient.invalidateQueries({ queryKey: importKeys.project(project.id), exact: true });
  }

  return (
    <section className="stack">
      <PageHeader title={messages.pages.settings} description={text.detail} />
      {project.activeSnapshot && (
        <Callout tone="info">
          <p>{text.outdatedWarning}</p>
          <p>
            <Link to={`/projects/${project.id}`}>{text.overviewLink}</Link>
          </p>
        </Callout>
      )}
      {saved && <p role="status">{text.saved}</p>}
      {settings.isPending && <Loading />}
      {settings.isError && (
        <ProblemAlert error={settings.error} onRetry={() => void settings.refetch()} />
      )}
      {settings.isSuccess && (
        // A saved version remounts the draft from the server's normalized settings.
        <SettingsEditor
          key={settings.data.version}
          projectId={project.id}
          settings={settings.data}
          onSaved={onSaved}
        />
      )}
    </section>
  );
}

interface SettingsEditorProps {
  projectId: string;
  settings: Settings;
  onSaved: () => void;
}

function SettingsEditor({ projectId, settings, onSaved }: SettingsEditorProps) {
  const [draft, setDraft] = useState(() => toDraft(settings));
  return (
    <SettingsForm
      projectId={projectId}
      settings={settings}
      draft={draft}
      onDraftChange={setDraft}
      onDiscard={() => setDraft(toDraft(settings))}
      onSaved={onSaved}
    />
  );
}
