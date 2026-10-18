import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { messages } from '../../messages';
import { getProviders, importKeys, startIndexing } from '../import/api';
import { acknowledgePrivacy, providerName } from '../import/privacy';
import { workspaceKeys, type Project } from '../workspaces/api';

interface ReindexDialogProps {
  project: Project;
  onClose: () => void;
}

export function ReindexDialog({ project, onClose }: ReindexDialogProps) {
  const text = messages.reindexDialog;
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const providers = useQuery({
    queryKey: importKeys.providers,
    queryFn: ({ signal }) => getProviders(signal),
  });
  useEffect(() => {
    if (providers.isError) {
      onClose();
    }
  }, [providers.isError, onClose]);
  if (!providers.isSuccess) {
    // The destination of the chunks must be on screen before anyone confirms.
    return null;
  }
  const { embeddings } = providers.data;
  const privacy =
    embeddings.provider === 'none'
      ? messages.privacy.noEmbeddings
      : embeddings.remote
        ? messages.privacy.remote(providerName(embeddings.provider), embeddings.model)
        : messages.privacy.localOnly;

  async function reindex() {
    await startIndexing(project.id);
    // Confirming here, with the destination stated, is the same consent as the overview's checkbox.
    if (embeddings.remote) {
      acknowledgePrivacy(project.id);
    }
    await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
    await queryClient.invalidateQueries({ queryKey: importKeys.project(project.id) });
    await navigate(`/projects/${project.id}`);
  }

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={text.title}
      description={
        <>
          <p>{text.detail(project.name)}</p>
          <p>{privacy}</p>
        </>
      }
      confirmLabel={text.confirm}
      confirmVariant="primary"
      workingLabel={messages.indexing.starting}
      onConfirm={reindex}
    />
  );
}
