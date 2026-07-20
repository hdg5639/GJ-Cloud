import { Fragment, ReactNode } from "react";

export function Breadcrumb({ items }: { items: Array<{ label: ReactNode; onClick?: () => void }> }) {
  return (
    <div className="mb-[9px] flex gap-2 text-xs text-muted-soft">
      {items.map((item, i) => (
        <Fragment key={i}>
          {i > 0 && <span>/</span>}
          {item.onClick ? (
            <button type="button" onClick={item.onClick} className="border-0 bg-transparent text-[#6c786f]">
              {item.label}
            </button>
          ) : (
            <span>{item.label}</span>
          )}
        </Fragment>
      ))}
    </div>
  );
}
