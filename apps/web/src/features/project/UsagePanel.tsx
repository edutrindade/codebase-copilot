import { useQuery } from '@tanstack/react-query';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Card } from '../../components/ui/Card';
import { Stat, StatGrid } from '../../components/ui/Stat';
import { formatCost, formatDuration, formatNumber } from '../../format';
import { messages } from '../../messages';
import { getUsage, projectKeys, type Usage } from './api';

export function UsagePanel({ projectId }: { projectId: string }) {
  const text = messages.project.usage;
  const usage = useQuery({
    queryKey: projectKeys.usage(projectId),
    queryFn: ({ signal }) => getUsage(projectId, signal),
  });

  return (
    <Card as="section" className="stack" aria-labelledby="usage-title">
      <h2 id="usage-title">{text.title}</h2>
      <p className="muted">{text.detail}</p>
      {usage.isError && <ProblemAlert error={usage.error} onRetry={() => void usage.refetch()} />}
      {usage.isSuccess && <UsageTotals usage={usage.data} />}
    </Card>
  );
}

function UsageTotals({ usage }: { usage: Usage }) {
  const text = messages.project.usage;
  const { indexing, questions, cost } = usage;
  return (
    <>
      <Totals
        title={text.indexing}
        items={[
          [text.total, formatNumber(indexing.total)],
          [text.completed, formatNumber(indexing.completed)],
          [text.failed, formatNumber(indexing.failed)],
          [text.cancelled, formatNumber(indexing.cancelled)],
          [text.averageDuration, duration(indexing.averageDurationMs)],
          [text.p95Duration, duration(indexing.p95DurationMs)],
        ]}
      />
      <Totals
        title={text.questions}
        items={[
          [text.total, formatNumber(questions.total)],
          [text.answered, formatNumber(questions.answered)],
          [text.partial, formatNumber(questions.partial)],
          [text.abstained, formatNumber(questions.abstained)],
          [text.failed, formatNumber(questions.failed)],
          [text.pending, formatNumber(questions.pending)],
          [text.p50Latency, duration(questions.p50LatencyMs)],
          [text.p95Latency, duration(questions.p95LatencyMs)],
        ]}
      />
      <Totals
        title={text.tokens}
        items={[
          [text.inputTokens, formatNumber(cost.inputTokens)],
          [text.outputTokens, formatNumber(cost.outputTokens)],
          [
            text.estimatedCost,
            cost.estimatedCost === null || cost.currency === null
              ? text.none
              : formatCost(cost.estimatedCost, cost.currency),
          ],
        ]}
      />
      {cost.unknownCostAnswers > 0 && (
        <p className="muted">{text.unknownCost(cost.unknownCostAnswers)}</p>
      )}
    </>
  );
}

function Totals({ title, items }: { title: string; items: [string, string][] }) {
  return (
    <div className="stack">
      <h3>{title}</h3>
      <StatGrid>
        {items.map(([label, value]) => (
          <Stat key={label} label={label} value={value} />
        ))}
      </StatGrid>
    </div>
  );
}

function duration(ms: number | null) {
  return ms === null ? messages.project.usage.none : formatDuration(ms);
}
