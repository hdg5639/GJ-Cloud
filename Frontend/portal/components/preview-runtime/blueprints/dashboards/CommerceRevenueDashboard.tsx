import { BlueprintMetricGrid, BlueprintSection, BlueprintSparkBars, BlueprintStatusPill, formatBlueprintValue } from "../core";
import type { BlueprintChartData, BlueprintMetric, BlueprintRecord } from "../core";

export function CommerceRevenueDashboard({
  metrics,
  revenue,
  topProducts,
  orders,
}: {
  metrics: BlueprintMetric[];
  revenue: BlueprintChartData;
  topProducts: BlueprintRecord[];
  orders: BlueprintRecord[];
}) {
  const series = revenue.series[0];
  return (
    <div className="space-y-4">
      <BlueprintMetricGrid metrics={metrics} columns={4} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,0.8fr)]">
        <BlueprintSection title="Revenue trend" description="Gross revenue across the selected period.">
          {series && <><div className="flex items-end justify-between gap-3"><div><p className="text-xs text-muted-soft">{series.label}</p><strong className="text-3xl tabular-nums">{formatBlueprintValue(series.values.at(-1) ?? 0)}</strong></div><BlueprintStatusPill value="Updated" tone="ok" /></div><div className="mt-6"><BlueprintSparkBars values={series.values} tone="brand" /></div></>}
        </BlueprintSection>
        <BlueprintSection title="Top products" description="Highest performing catalog entries.">
          <ol className="space-y-3">
            {topProducts.slice(0, 6).map((product, index) => <li key={String(product.id ?? index)} className="flex items-center justify-between gap-3"><div className="flex min-w-0 items-center gap-3"><span className="grid h-7 w-7 place-items-center rounded-full bg-white/[0.05] text-xs font-black">{index + 1}</span><div className="min-w-0"><strong className="block truncate text-sm">{formatBlueprintValue(product.name ?? product.title)}</strong><span className="text-[11px] text-muted-soft">{formatBlueprintValue(product.category ?? product.sku)}</span></div></div><span className="text-xs font-bold tabular-nums">{formatBlueprintValue(product.revenue ?? product.sales)}</span></li>)}
          </ol>
        </BlueprintSection>
      </div>
      <BlueprintSection title="Recent orders" description="Latest order states and totals.">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {orders.slice(0, 8).map((order, index) => <article key={String(order.id ?? index)} className="rounded-[12px] border border-line bg-white/[0.015] p-3"><div className="flex items-center justify-between gap-2"><strong className="text-xs">#{formatBlueprintValue(order.id ?? order.orderNumber)}</strong><BlueprintStatusPill value={order.status ?? "pending"} /></div><p className="mt-3 text-lg font-black tabular-nums">{formatBlueprintValue(order.total ?? order.amount)}</p><p className="mt-1 text-[11px] text-muted-soft">{formatBlueprintValue(order.customer ?? order.email)}</p></article>)}
        </div>
      </BlueprintSection>
    </div>
  );
}
