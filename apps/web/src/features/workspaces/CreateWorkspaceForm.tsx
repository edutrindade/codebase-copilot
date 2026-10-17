import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { messages } from '../../messages';
import { createWorkspace, workspaceKeys } from './api';

export function CreateWorkspaceForm() {
  const inputId = useId();
  const [name, setName] = useState('');
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const mutation = useMutation({
    mutationFn: createWorkspace,
    onSuccess: async (workspace) => {
      await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
      await navigate(`/workspaces/${workspace.id}`);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate(name);
  }

  return (
    <form className="stack" onSubmit={submit}>
      <div className="field">
        <label htmlFor={inputId}>{messages.workspaces.nameLabel}</label>
        <input
          id={inputId}
          type="text"
          maxLength={100}
          value={name}
          placeholder={messages.workspaces.namePlaceholder}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      {mutation.isError && <ProblemAlert error={mutation.error} />}
      <div>
        <Button type="submit" variant="primary" pending={mutation.isPending}>
          {mutation.isPending ? messages.workspaces.creating : messages.workspaces.create}
        </Button>
      </div>
    </form>
  );
}
