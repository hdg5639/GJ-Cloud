import { cn } from "./cn";

export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex justify-center gap-1.5 p-3.5">
      <button
        type="button"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
        className="h-8 w-8 rounded-lg border border-line bg-panel disabled:opacity-40"
      >
        ‹
      </button>
      {Array.from({ length: totalPages }, (_, i) => i + 1).map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onChange(n)}
          className={cn("h-8 w-8 rounded-lg border border-line bg-panel", n === page && "bg-soft text-brand-strong")}
        >
          {n}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
        className="h-8 w-8 rounded-lg border border-line bg-panel disabled:opacity-40"
      >
        ›
      </button>
    </div>
  );
}
