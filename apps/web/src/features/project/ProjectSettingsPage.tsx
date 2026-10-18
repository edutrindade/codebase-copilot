import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { usePageTitle } from '../../app/title';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Callout } from '../../components/ui/Callout';
import { PageHeader } from '../../components/ui/PageHeader';
import { useToast } from '../../components/ui/Toast';
import { messages } from '../../messages';
import { getSettings, importKeys, type Settings } from '../import/api';
import { SETTINGS_SECTION_ID, SettingsForm } from '../import/SettingsForm';
import { toDraft } from '../import/settingsDraft';
import { useProject } from './ProjectLayout';

export function ProjectSettingsPage() {
  const text = messages.projectSettings;
  const project = useProject();
  const queryClient = useQueryClient();
  const notify = useToast();
  const [saves, setSaves] = useState(0);
  usePageTitle([messages.pages.settings, project.name]);
  const settings = useQuery({
    queryKey: importKeys.settings(project.id),
    queryFn: ({ signal }) => getSettings(project.id, signal),
  });
  const outdated = project.outdatedReasons.includes('SETTINGS_CHANGED');

  // The save bar goes away with the changes, so focus moves to the form instead of the page.
  useEffect(() => {
    if (saves > 0) {
      document.getElementById(SETTINGS_SECTION_ID)?.focus();
    }
  }, [saves]);

  function onSaved() {
    setSaves((count) => count + 1);
    notify({
      tone: 'success',
      title: messages.toast.settingsSaved,
      description: project.activeSnapshot ? text.saved : undefined,
    });
    // The project now reports SETTINGS_CHANGED.
    void queryClient.invalidateQueries({ queryKey: importKeys.project(project.id), exact: true });
  }

  return (
    <section className="stack">
      <PageHeader title={messages.pages.settings} description={text.detail} />
      {project.activeSnapshot && outdated && (
        <Callout tone="warning">
          <p>{text.outdated}</p>
          <p>
            <Link to={`/projects/${project.id}`}>{text.overviewLink}</Link>
          </p>
        </Callout>
      )}
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
          saveWarning={project.activeSnapshot ? text.outdatedWarning : undefined}
          onSaved={onSaved}
        />
      )}
    </section>
  );
}

interface SettingsEditorProps {
  projectId: string;
  settings: Settings;
  saveWarning?: string;
  onSaved: () => void;
}

function SettingsEditor({ projectId, settings, saveWarning, onSaved }: SettingsEditorProps) {
  const [draft, setDraft] = useState(() => toDraft(settings));
  return (
    <SettingsForm
      projectId={projectId}
      settings={settings}
      draft={draft}
      onDraftChange={setDraft}
      onDiscard={() => setDraft(toDraft(settings))}
      onSaved={onSaved}
      layout="page"
      saveWarning={saveWarning}
    />
  );
}
