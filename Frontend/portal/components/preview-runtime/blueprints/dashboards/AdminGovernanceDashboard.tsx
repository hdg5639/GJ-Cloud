import { BlueprintMetricGrid, BlueprintSection, BlueprintStatusPill, formatBlueprintValue } from "../core";
import type { BlueprintAlert, BlueprintMetric, BlueprintRecord } from "../core";

export function AdminGovernanceDashboard({ metrics, policyFindings, privilegedAccounts }: { metrics: BlueprintMetric[]; policyFindings: BlueprintAlert[]; privilegedAccounts: BlueprintRecord[] }) {
  return (
    <div className="space-y-4">
      <BlueprintMetricGrid metrics={metrics} columns={4} />
      <div className="grid gap-4 xl:grid-cols-2">
        <BlueprintSection title="Policy findings" description="Configuration and compliance issues requiring review.">
          <div className="space-y-3">{policyFindings.slice(0, 8).map((finding) => <article key={finding.id} className="rounded-[12px] border border-line bg-white/[0.015] p-3"><div className="flex items-start justify-between gap-3"><strong className="text-sm">{finding.title}</strong><BlueprintStatusPill value={finding.severity} tone={finding.severity === "CRITICAL" || finding.severity === "ERROR" ? "danger" : finding.severity === "WARNING" ? "warn" : "neutral"} /></div>{finding.description && <p className="mt-1 text-xs leading-5 text-muted-soft">{finding.description}</p>}</article>)}</div>
        </BlueprintSection>
        <BlueprintSection title="Privileged accounts" description="Accounts with elevated access.">
          <div className="space-y-3">{privilegedAccounts.slice(0, 8).map((account, index) => <div key={String(account.id ?? index)} className="flex items-center justify-between gap-3 border-b border-line pb-3 last:border-0 last:pb-0"><div className="min-w-0"><strong className="block truncate text-sm">{formatBlueprintValue(account.name ?? account.email)}</strong><span className="text-[11px] text-muted-soft">{formatBlueprintValue(account.role ?? account.scope)}</span></div><BlueprintStatusPill value={account.status ?? "active"} /></div>)}</div>
        </BlueprintSection>
      </div>
    </div>
  );
}
