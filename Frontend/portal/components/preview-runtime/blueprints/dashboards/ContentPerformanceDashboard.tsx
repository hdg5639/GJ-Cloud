import { BlueprintMetricGrid, BlueprintSection, BlueprintSparkBars, BlueprintStatusPill, formatBlueprintValue } from "../core";
import type { BlueprintChartData, BlueprintMetric, BlueprintRecord } from "../core";

export function ContentPerformanceDashboard({ metrics, traffic, content }: { metrics: BlueprintMetric[]; traffic: BlueprintChartData; content: BlueprintRecord[] }) {
  return (
    <div className="space-y-4">
      <BlueprintMetricGrid metrics={metrics} columns={4} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <BlueprintSection title="Audience trend" description="Views and engagement over time.">
          <div className="grid gap-5 md:grid-cols-2">
            {traffic.series.slice(0, 2).map((series) => <div key={series.id}><div className="flex items-center justify-between"><strong className="text-sm">{series.label}</strong><span className="text-xs font-bold tabular-nums">{formatBlueprintValue(series.values.at(-1) ?? 0)}</span></div><div className="mt-3"><BlueprintSparkBars values={series.values} tone={series.tone ?? "brand"} /></div></div>)}
          </div>
        </BlueprintSection>
        <BlueprintSection title="Publishing health" description="Content lifecycle distribution.">
          <div className="space-y-3">{content.slice(0, 7).map((item, index) => <div key={String(item.id ?? index)} className="flex items-center justify-between gap-3 border-b border-line pb-3 last:border-0 last:pb-0"><div className="min-w-0"><strong className="block truncate text-sm">{formatBlueprintValue(item.title ?? item.name)}</strong><span className="text-[11px] text-muted-soft">{formatBlueprintValue(item.author ?? item.channel)}</span></div><BlueprintStatusPill value={item.status ?? "draft"} /></div>)}</div>
        </BlueprintSection>
      </div>
    </div>
  );
}
