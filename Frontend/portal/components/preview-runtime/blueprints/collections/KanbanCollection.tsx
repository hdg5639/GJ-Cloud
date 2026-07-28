import { cn } from "@/components/ui/cn";
import { BlueprintStatusPill } from "../core";
import type { BlueprintKanbanCard, BlueprintKanbanColumn } from "../core";

export function KanbanCollection({ columns, onCardClick, onAddCard, className }: { columns: BlueprintKanbanColumn[]; onCardClick?: (card: BlueprintKanbanCard, column: BlueprintKanbanColumn) => void; onAddCard?: (column: BlueprintKanbanColumn) => void; className?: string }) {
  return (
    <div className={cn("grid auto-cols-[minmax(280px,1fr)] grid-flow-col gap-4 overflow-x-auto pb-2", className)}>
      {columns.map((column) => (
        <section key={column.id} className="min-w-[280px] rounded-[15px] border border-line bg-panel p-3">
          <header className="mb-3 flex items-start justify-between gap-3 px-1">
            <div><div className="flex items-center gap-2"><h3 className="text-sm font-extrabold">{column.title}</h3><span className="rounded-full bg-white/[0.06] px-2 py-0.5 text-[10px] tabular-nums text-muted-soft">{column.cards.length}</span></div>{column.description && <p className="mt-1 text-[11px] text-muted-soft">{column.description}</p>}</div>
            {onAddCard && <button type="button" onClick={() => onAddCard(column)} className="grid h-7 w-7 place-items-center rounded-[8px] border border-line bg-white/[0.02] text-muted hover:text-foreground">+</button>}
          </header>
          <div className="space-y-2.5">
            {column.cards.map((card) => <button key={card.id} type="button" onClick={() => onCardClick?.(card, column)} className="block w-full rounded-[13px] border border-line bg-background p-3 text-left transition hover:border-line-strong"><div className="flex items-start justify-between gap-2"><strong className="text-sm">{card.title}</strong>{card.status && <BlueprintStatusPill value={card.status} />}</div>{card.subtitle && <p className="mt-1 text-xs leading-5 text-muted-soft">{card.subtitle}</p>}{card.tags && card.tags.length > 0 && <div className="mt-3 flex flex-wrap gap-1.5">{card.tags.slice(0, 4).map((tag) => <span key={tag} className="rounded-full bg-white/[0.05] px-2 py-1 text-[10px] text-muted">{tag}</span>)}</div>}<div className="mt-3 flex items-center justify-between text-[10px] text-muted-soft"><span>{card.owner ?? "Unassigned"}</span><span>{card.dueAt ?? ""}</span></div></button>)}
          </div>
        </section>
      ))}
    </div>
  );
}
