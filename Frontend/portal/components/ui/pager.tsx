import { cn } from "./cn";

function visiblePages(page: number, totalPages: number): Array<number | string> {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1);
  const candidates = new Set([1, totalPages, page - 1, page, page + 1]);
  const pages = Array.from(candidates)
    .filter((candidate) => candidate >= 1 && candidate <= totalPages)
    .sort((left, right) => left - right);
  const result: Array<number | string> = [];
  pages.forEach((candidate, index) => {
    const previous = pages[index - 1];
    if (previous !== undefined && candidate - previous > 1) {
      result.push(`ellipsis-${previous}-${candidate}`);
    }
    result.push(candidate);
  });
  return result;
}

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
      {visiblePages(page, totalPages).map((item) => typeof item === "number" ? (
          <button
            key={item}
            type="button"
            onClick={() => onChange(item)}
            className={cn("h-8 min-w-8 rounded-lg border border-line bg-panel px-2", item === page && "bg-soft text-brand-strong")}
          >
            {item}
          </button>
        ) : (
          <span key={item} className="grid h-8 min-w-6 place-items-center text-muted-soft" aria-hidden>…</span>
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
