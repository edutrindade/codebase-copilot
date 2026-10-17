import { useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { messages } from '../../messages';
import { getProject, importKeys } from './api';
import { ReviewStep } from './ReviewStep';
import { SourceStep } from './SourceStep';
import { ZipUploadStep } from './ZipUploadStep';

export function NewProjectPage() {
  const { workspaceId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const projectId = searchParams.get('project');

  return (
    <section className="stack">
      <Link to={`/workspaces/${workspaceId}`} className="back-link">
        {messages.newProject.backToWorkspace}
      </Link>
      <h1>{messages.pages.newProject}</h1>
      {projectId === null ? (
        <SourceStep
          workspaceId={workspaceId}
          onCreated={(id) => setSearchParams({ project: id })}
        />
      ) : (
        <ProjectSteps projectId={projectId} />
      )}
    </section>
  );
}

// The step is derived from the project, so a reload resumes where the import stopped.
function ProjectSteps({ projectId }: { projectId: string }) {
  const project = useQuery({
    queryKey: importKeys.project(projectId),
    queryFn: ({ signal }) => getProject(projectId, signal),
  });

  if (project.isPending) {
    return <Loading />;
  }
  if (project.isError) {
    return <ProblemAlert error={project.error} onRetry={() => void project.refetch()} />;
  }
  const { source } = project.data;
  if (source.type === 'ZIP' && !source.currentImport) {
    return <ZipUploadStep projectId={projectId} />;
  }
  return <ReviewStep project={project.data} />;
}
