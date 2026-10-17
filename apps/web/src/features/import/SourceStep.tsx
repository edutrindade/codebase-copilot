import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { messages } from '../../messages';
import { workspaceKeys } from '../workspaces/api';
import { createProject, importKeys, listLocalRoots, type LocalRoot, type NewProject } from './api';
import { DirectoryBrowser, type FolderSelection } from './DirectoryBrowser';
import { StepIndicator, type SourceType } from './StepIndicator';

interface SourceStepProps {
  workspaceId: string;
  onCreated: (projectId: string) => void;
}

export function SourceStep({ workspaceId, onCreated }: SourceStepProps) {
  const roots = useQuery({
    queryKey: importKeys.roots,
    queryFn: ({ signal }) => listLocalRoots(signal),
  });

  return (
    <>
      {roots.isPending && <Loading />}
      {roots.isError && <ProblemAlert error={roots.error} onRetry={() => void roots.refetch()} />}
      {roots.isSuccess && (
        <SourceForm roots={roots.data} workspaceId={workspaceId} onCreated={onCreated} />
      )}
    </>
  );
}

function defaultName(roots: LocalRoot[], folder: FolderSelection): string {
  if (folder.path === '') {
    return roots.find((root) => root.key === folder.rootKey)?.label ?? '';
  }
  return folder.path.slice(folder.path.lastIndexOf('/') + 1);
}

interface SourceFormProps extends SourceStepProps {
  roots: LocalRoot[];
}

function SourceForm({ roots, workspaceId, onCreated }: SourceFormProps) {
  const nameId = useId();
  const hasRoots = roots.length > 0;
  const [sourceType, setSourceType] = useState<SourceType>(hasRoots ? 'LOCAL_FOLDER' : 'ZIP');
  const [folder, setFolder] = useState<FolderSelection>({ rootKey: roots[0]?.key ?? '', path: '' });
  const [typedName, setTypedName] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (project: NewProject) => createProject(workspaceId, project),
    onSuccess: async (project) => {
      queryClient.setQueryData(importKeys.project(project.id), project);
      await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
      onCreated(project.id);
    },
  });

  const local = sourceType === 'LOCAL_FOLDER';
  const name = typedName ?? (local ? defaultName(roots, folder) : '');

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate(
      local
        ? { sourceType, name, rootKey: folder.rootKey, relativePath: folder.path }
        : { sourceType, name },
    );
  }

  return (
    <form className="stack" onSubmit={submit}>
      <StepIndicator sourceType={sourceType} current="source" />
      <div className="intro">
        {messages.newProject.intro.map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
      <fieldset className="choice">
        <legend>{messages.newProject.sourceLegend}</legend>
        <label className="choice__option">
          <input
            type="radio"
            name="sourceType"
            value="LOCAL_FOLDER"
            checked={local}
            disabled={!hasRoots}
            onChange={() => setSourceType('LOCAL_FOLDER')}
          />
          <span>
            {messages.newProject.sourceLocal}
            <span className="muted"> — {messages.newProject.sourceLocalHint}</span>
          </span>
        </label>
        <label className="choice__option">
          <input
            type="radio"
            name="sourceType"
            value="ZIP"
            checked={!local}
            onChange={() => setSourceType('ZIP')}
          />
          <span>
            {messages.newProject.sourceZip}
            <span className="muted"> — {messages.newProject.sourceZipHint}</span>
          </span>
        </label>
      </fieldset>
      {!hasRoots && (
        <Callout tone="info">
          <p>{messages.newProject.noRoots}</p>
        </Callout>
      )}
      {local && <DirectoryBrowser roots={roots} value={folder} onChange={setFolder} />}
      <div className="field">
        <label htmlFor={nameId}>{messages.newProject.nameLabel}</label>
        <input
          id={nameId}
          type="text"
          maxLength={100}
          value={name}
          onChange={(event) => setTypedName(event.target.value)}
        />
      </div>
      {mutation.isError && <ProblemAlert error={mutation.error} />}
      <div>
        <Button type="submit" variant="primary" pending={mutation.isPending}>
          {mutation.isPending ? messages.newProject.creating : messages.newProject.create}
        </Button>
      </div>
    </form>
  );
}
