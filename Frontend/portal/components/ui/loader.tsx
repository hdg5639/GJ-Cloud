export function Spinner({ className = "" }: { className?: string }) {
  return (
    <svg
      className={`animate-spin ${className}`}
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle className="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  );
}

export function PageLoader({ label = "불러오는 중", size = 72 }: { label?: string; size?: number }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-16">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/gamjabox-loader.svg" alt="" width={size} height={size} />
      <p className="text-sm text-muted-soft">{label}</p>
    </div>
  );
}

export function SkeletonRow({ cols = 4, dark = false }: { cols?: number; dark?: boolean }) {
  const bg = dark ? "bg-foreground/[0.12]" : "bg-foreground/[0.08]";
  return (
    <tr className="border-b border-line">
      {Array.from({ length: cols }).map((_, i) => (
        <td key={i} className="px-4 py-3">
          <div
            className={`h-3.5 ${bg} rounded animate-pulse`}
            style={{ width: `${55 + (i % 3) * 15}%` }}
          />
        </td>
      ))}
    </tr>
  );
}

export function SkeletonCard() {
  return (
    <div className="border border-line rounded-xl p-5 space-y-3 animate-pulse">
      <div className="flex items-center justify-between">
        <div className="h-4 bg-white/[0.06] rounded w-1/3" />
        <div className="h-5 bg-white/[0.06] rounded-full w-16" />
      </div>
      <div className="h-3 bg-white/[0.06] rounded w-1/2" />
      <div className="h-3 bg-white/[0.06] rounded w-2/3" />
    </div>
  );
}
