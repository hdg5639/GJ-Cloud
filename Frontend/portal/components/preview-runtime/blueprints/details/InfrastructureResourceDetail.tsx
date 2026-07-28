import { BlueprintActionButtons, BlueprintKeyValueGrid, BlueprintMetricGrid, BlueprintSection, BlueprintStatusPill, formatBlueprintValue } from "../core";
import type { BlueprintAction, BlueprintField, BlueprintMetric, BlueprintRecord, BlueprintTimelineEvent } from "../core";
import { TimelineCollection } from "../collections";

export function InfrastructureResourceDetail({
  resource,
  metrics,
  fields,
  actions,
  activity,
  related,
  onAction,
}: {
  resource: BlueprintRecord;
  metrics: BlueprintMetric[];
  fields: BlueprintField[];
  actions: BlueprintAction[];
  activity?: BlueprintTimelineEvent[];
  related?: React.ReactNode;
  onAction?: (action: BlueprintAction) => void;
}) {
  return <div className="space-y-4"><header className="rounded-[18px] border border-line bg-panel p-5"><div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between"><div><p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">Infrastructure resource</p><div className="mt-2 flex flex-wrap items-center gap-3"><h2 className="text-2xl font-black">{formatBlueprintValue(resource.name ?? resource.title ?? resource.id)}</h2><BlueprintStatusPill value={resource.status ?? resource.state ?? "unknown"} /></div><p className="mt-2 text-sm text-muted-soft">{formatBlueprintValue(resource.description ?? resource.region ?? resource.type)}</p></div><BlueprintActionButtons actions={actions} onAction={onAction} /></div></header><BlueprintMetricGrid metrics={metrics} columns={4} /><div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]"><div className="space-y-4"><BlueprintSection title="Configuration" description="Resolved resource properties and runtime configuration."><BlueprintKeyValueGrid fields={fields} columns={2} /></BlueprintSection>{related}</div>{activity && <BlueprintSection title="Activity" description="Recent lifecycle events and commands."><TimelineCollection events={activity} compact /></BlueprintSection>}</div></div>;
}
