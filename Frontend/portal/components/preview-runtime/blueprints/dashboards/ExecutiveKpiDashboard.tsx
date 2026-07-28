import { BlueprintMetricGrid, BlueprintSection, BlueprintSparkBars, BlueprintStatusPill } from "../core";
import type { BlueprintChartData, BlueprintMetric, BlueprintTimelineEvent } from "../core";

export function ExecutiveKpiDashboard({
  metrics,
  trend,
  highlights,
  title = "Executive overview",
}: {
  metrics: BlueprintMetric[];
  trend?: BlueprintChartData;
  highlights?: BlueprintTimelineEvent[];
  title?: string;
}) {
  const firstSeries = trend?.series[0];
  return (
    <div className="space-y-4">
      <BlueprintMetricGrid metrics={metrics} columns={Math.min(6, Math.max(2, metrics.length)) as 2 | 3 | 4 | 5 | 6} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(300px,0.8fr)]">
        <BlueprintSection title={title} description="High-level performance trend across the selected period.">
          {firstSeries ? (
            <div>
              <div className="flex items-center justify-between gap-3"><strong>{firstSeries.label}</strong><BlueprintStatusPill value="Live" tone="ok" /></div>
              <div className="mt-5"><BlueprintSparkBars values={firstSeries.values} tone={firstSeries.tone ?? "brand"} /></div>
              {trend && <div className="mt-3 flex justify-between text-[10px] text-muted-soft">{trend.labels.slice(0, 6).map((label) => <span key={label}>{label}</span>)}</div>}
            </div>
          ) : <p className="text-sm text-muted-soft">No trend data is available.</p>}
        </BlueprintSection>
        <BlueprintSection title="Highlights" description="Signals that deserve executive attention.">
          <div className="space-y-3">
            {(highlights ?? []).slice(0, 6).map((item) => (
              <div key={item.id} className="rounded-[12px] border border-line bg-white/[0.015] p-3">
                <div className="flex items-start justify-between gap-3"><strong className="text-sm">{item.title}</strong>{item.status && <BlueprintStatusPill value={item.status} tone={item.tone} />}</div>
                {item.description && <p className="mt-1 text-xs leading-5 text-muted-soft">{item.description}</p>}
                {item.timestamp && <p className="mt-2 text-[10px] text-muted-soft">{item.timestamp}</p>}
              </div>
            ))}
            {(highlights ?? []).length === 0 && <p className="text-sm text-muted-soft">No highlights yet.</p>}
          </div>
        </BlueprintSection>
      </div>
    </div>
  );
}
