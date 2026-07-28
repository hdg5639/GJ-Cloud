import { BlueprintMetricGrid, BlueprintProgressBar, BlueprintSection, BlueprintStatusPill, formatBlueprintValue } from "../core";
import type { BlueprintMetric, BlueprintRecord, BlueprintTimelineEvent } from "../core";

export function ProjectDeliveryDashboard({ metrics, milestones, activity }: { metrics: BlueprintMetric[]; milestones: BlueprintRecord[]; activity: BlueprintTimelineEvent[] }) {
  return (
    <div className="space-y-4">
      <BlueprintMetricGrid metrics={metrics} columns={4} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <BlueprintSection title="Milestones" description="Delivery progress and schedule health.">
          <div className="space-y-4">{milestones.map((milestone, index) => { const progress = typeof milestone.progress === "number" ? milestone.progress : 0; return <article key={String(milestone.id ?? index)}><div className="mb-2 flex items-center justify-between gap-3"><div><strong className="text-sm">{formatBlueprintValue(milestone.name ?? milestone.title)}</strong><p className="mt-1 text-[11px] text-muted-soft">{formatBlueprintValue(milestone.dueAt ?? milestone.owner)}</p></div><BlueprintStatusPill value={milestone.status ?? "pending"} /></div><BlueprintProgressBar value={progress} /></article>; })}</div>
        </BlueprintSection>
        <BlueprintSection title="Recent activity" description="Changes across the delivery stream.">
          <div className="space-y-4">{activity.slice(0, 8).map((event) => <div key={event.id} className="relative pl-5 before:absolute before:left-0 before:top-1 before:h-2.5 before:w-2.5 before:rounded-full before:bg-brand"><strong className="text-sm">{event.title}</strong>{event.description && <p className="mt-1 text-xs leading-5 text-muted-soft">{event.description}</p>}<p className="mt-1 text-[10px] text-muted-soft">{event.timestamp}</p></div>)}</div>
        </BlueprintSection>
      </div>
    </div>
  );
}
