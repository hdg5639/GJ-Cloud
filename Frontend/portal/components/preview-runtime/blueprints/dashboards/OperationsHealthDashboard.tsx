import { BlueprintMetricGrid, BlueprintProgressBar, BlueprintSection, BlueprintStatusPill } from "../core";
import type { BlueprintMetric, BlueprintRecord, BlueprintTimelineEvent } from "../core";
import { blueprintRecordTitle, formatBlueprintValue } from "../core";

export function OperationsHealthDashboard({
  metrics,
  services,
  incidents,
}: {
  metrics: BlueprintMetric[];
  services: BlueprintRecord[];
  incidents: BlueprintTimelineEvent[];
}) {
  return (
    <div className="space-y-4">
      <BlueprintMetricGrid metrics={metrics} columns={4} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <BlueprintSection title="Service health" description="Current status, saturation, and availability.">
          <div className="space-y-3">
            {services.map((service, index) => {
              const status = service.status ?? service.state ?? "unknown";
              const utilization = typeof service.utilization === "number" ? service.utilization : typeof service.cpu === "number" ? service.cpu : 0;
              return (
                <article key={String(service.id ?? index)} className="rounded-[13px] border border-line bg-white/[0.015] p-4">
                  <div className="flex items-center justify-between gap-3"><div><strong className="text-sm">{blueprintRecordTitle(service)}</strong><p className="mt-1 text-[11px] text-muted-soft">{formatBlueprintValue(service.region ?? service.environment ?? service.version)}</p></div><BlueprintStatusPill value={status} /></div>
                  <div className="mt-3"><BlueprintProgressBar value={utilization} label="Utilization" /></div>
                </article>
              );
            })}
          </div>
        </BlueprintSection>
        <BlueprintSection title="Active incidents" description="Unresolved operational events.">
          <div className="space-y-3">
            {incidents.slice(0, 8).map((incident) => (
              <div key={incident.id} className="border-b border-line pb-3 last:border-0 last:pb-0">
                <div className="flex items-start justify-between gap-2"><strong className="text-sm">{incident.title}</strong>{incident.status && <BlueprintStatusPill value={incident.status} tone={incident.tone} />}</div>
                {incident.description && <p className="mt-1 text-xs leading-5 text-muted-soft">{incident.description}</p>}
                {incident.timestamp && <p className="mt-1 text-[10px] text-muted-soft">{incident.timestamp}</p>}
              </div>
            ))}
          </div>
        </BlueprintSection>
      </div>
    </div>
  );
}
