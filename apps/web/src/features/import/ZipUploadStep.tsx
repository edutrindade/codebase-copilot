import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useState, type FormEvent } from 'react';
import { FileArchive, Upload } from 'lucide-react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Callout } from '../../components/ui/Callout';
import { Dropzone } from '../../components/ui/Dropzone';
import { Progress } from '../../components/ui/Progress';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { getLatestImport, importKeys, uploadZip, type ImportJob } from './api';
import { StepIndicator } from './StepIndicator';

const IMPORT_POLL_MS = 1000;

function isActive(job: ImportJob | null | undefined): boolean {
  return job?.status === 'PENDING' || job?.status === 'EXTRACTING';
}

export function ZipUploadStep({ projectId }: { projectId: string }) {
  const text = messages.newProject.upload;
  const errorId = useId();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<'missing' | 'notZip' | null>(null);
  const queryClient = useQueryClient();
  const latest = useQuery({
    queryKey: importKeys.latestImport(projectId),
    queryFn: ({ signal }) => getLatestImport(projectId, signal),
    refetchInterval: (query) => (isActive(query.state.data) ? IMPORT_POLL_MS : false),
  });
  const upload = useMutation({
    mutationFn: (zip: File) => uploadZip(projectId, zip),
    onSuccess: (job) => queryClient.setQueryData(importKeys.latestImport(projectId), job),
  });

  const job = latest.data;
  const completed = job?.status === 'COMPLETED';
  useEffect(() => {
    // The project only gets a current import once extraction completes; reloading it moves on to the review.
    if (completed) {
      void queryClient.invalidateQueries({ queryKey: importKeys.project(projectId), exact: true });
    }
  }, [completed, projectId, queryClient]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (file === null) {
      setFileError((error) => error ?? 'missing');
      return;
    }
    upload.mutate(file);
  }

  const busy = upload.isPending || isActive(job);

  return (
    <div className="stack">
      <StepIndicator sourceType="ZIP" current="upload" />
      <h2>{messages.newProject.upload.title}</h2>
      <p>{messages.newProject.upload.detail}</p>
      {latest.isPending && <Loading />}
      {latest.isError && (
        <ProblemAlert error={latest.error} onRetry={() => void latest.refetch()} />
      )}
      {job && <ImportStatus job={job} />}
      <form className="stack" onSubmit={submit}>
        <Dropzone
          label={text.fileLabel}
          prompt={text.dropPrompt}
          hint={text.limit}
          accept=".zip,application/zip"
          disabled={busy}
          invalid={fileError !== null}
          errorId={errorId}
          onFile={(chosen) => {
            const zip = chosen.name.toLowerCase().endsWith('.zip');
            setFile(zip ? chosen : null);
            setFileError(zip ? null : 'notZip');
          }}
        >
          {file && (
            <p className="dropzone__file">
              <FileArchive aria-hidden="true" className="browser__icon" />
              {text.chosen(file.name, formatBytes(file.size))}
            </p>
          )}
          {fileError && (
            <p id={errorId} className="field-error">
              {fileError === 'missing' ? text.chooseFile : text.notZip}
            </p>
          )}
        </Dropzone>
        {upload.isPending && file && (
          <div className="stack">
            <Progress value={null} label={text.sending(file.name, formatBytes(file.size))} />
            <p className="muted">{text.sending(file.name, formatBytes(file.size))}</p>
          </div>
        )}
        {upload.isError && <ProblemAlert error={upload.error} />}
        <div>
          <Button
            type="submit"
            variant="primary"
            icon={Upload}
            disabled={busy}
            pending={upload.isPending}
          >
            {upload.isPending
              ? messages.newProject.upload.uploading
              : messages.newProject.upload.submit}
          </Button>
        </div>
      </form>
    </div>
  );
}

function ImportStatus({ job }: { job: ImportJob }) {
  if (job.status === 'PENDING' || job.status === 'EXTRACTING') {
    return (
      <p role="status">
        {job.status === 'PENDING'
          ? messages.newProject.upload.pending
          : messages.newProject.upload.extracting}
      </p>
    );
  }
  if (job.status === 'REJECTED' || job.status === 'FAILED') {
    return (
      <Callout
        role="alert"
        tone="danger"
        title={
          job.status === 'REJECTED'
            ? messages.newProject.upload.rejectedTitle
            : messages.newProject.upload.failedTitle
        }
      >
        {job.error && <p>{job.error.message}</p>}
        <p>{messages.newProject.upload.tryAnother}</p>
      </Callout>
    );
  }
  return null;
}
