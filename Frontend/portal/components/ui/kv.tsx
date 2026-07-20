import { ReactNode } from "react";

export function KeyValueList({ items }: { items: Array<{ label: ReactNode; value: ReactNode }> }) {
  return (
    <dl className="m-0">
      {items.map((item, i) => (
        <div key={i} className="flex justify-between gap-4 border-b border-[#edf1ee] py-3 last:border-b-0">
          <dt className="text-sm text-muted">{item.label}</dt>
          <dd className="m-0 text-sm font-bold">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
