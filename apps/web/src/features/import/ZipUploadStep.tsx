import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useState, type FormEvent } from 'react';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { messages } from '../../messages';
import { getLatestImport, importKeys, uploadZip, type ImportJob } from './api';
import { StepIndicator } from './StepIndicator';

const IMPORT_POLL_MS = 1000;

function isActive(job: ImportJob | null | undefined): boolean {
  return job?.status === 'PENDING' || job?.status === 'EXTRACTING';
}

export function ZipUploadStep({ projectId }: { projectId: string }) {
  const fileId = useId();
  const [file, setFile] = useState<File | null>(null);
  const [missingFile, setMissingFile] = useState(false);
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
      setMissingFile(true);
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
        <div className="field">
          <label htmlFor={fileId}>{messages.newProject.upload.fileLabel}</label>
          <input
            id={fileId}
            type="file"
            accept=".zip,application/zip"
            disabled={busy}
            aria-invalid={missingFile || undefined}
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setMissingFile(false);
            }}
          />
          {missingFile && <p className="field-error">{messages.newProject.upload.chooseFile}</p>}
        </div>
        {upload.isError && <ProblemAlert error={upload.error} />}
        <div>
          <button type="submit" className="button button--primary" disabled={busy}>
            {upload.isPending
              ? messages.newProject.upload.uploading
              : messages.newProject.upload.submit}
          </button>
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
      <div role="alert" className="problem-alert">
        <p className="problem-alert__title">
          {job.status === 'REJECTED'
            ? messages.newProject.upload.rejectedTitle
            : messages.newProject.upload.failedTitle}
        </p>
        {job.error && <p>{job.error.message}</p>}
        <p>{messages.newProject.upload.tryAnother}</p>
      </div>
    );
  }
  return null;
}
