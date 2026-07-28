"use client";

import { useMemo, useState } from "react";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import type { BlueprintDirectoryEntry } from "../core";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function AssignOwnerModal({ open, onClose, candidates, currentOwnerId, onAssign }: { open: boolean; onClose: () => void; candidates: BlueprintDirectoryEntry[]; currentOwnerId?: string; onAssign: (candidate: BlueprintDirectoryEntry | null) => void | Promise<void> }) {
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState(currentOwnerId ?? "");
  const filtered = useMemo(() => candidates.filter((candidate) => `${candidate.title} ${candidate.subtitle ?? ""}`.toLowerCase().includes(search.toLowerCase())), [candidates, search]);
  const selected = candidates.find((candidate) => candidate.id === selectedId) ?? null;
  return <BlueprintModalFrame open={open} onClose={onClose} title="Assign owner" description="Choose the person responsible for this resource." eyebrow="Ownership" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" onClick={() => onAssign(selected)}>Assign</Button></>}><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search people" /><div className="mt-3 max-h-80 space-y-2 overflow-auto">{filtered.map((candidate) => <button key={candidate.id} type="button" onClick={() => setSelectedId(candidate.id)} className={`flex w-full items-center gap-3 rounded-[12px] border p-3 text-left ${candidate.id === selectedId ? "border-brand bg-brand/8" : "border-line hover:border-line-strong"}`}><Avatar nickname={candidate.title} email={candidate.subtitle} profileImageUrl={candidate.avatarUrl} /><div className="min-w-0"><strong className="block truncate text-sm">{candidate.title}</strong><span className="text-xs text-muted-soft">{candidate.subtitle ?? candidate.role}</span></div></button>)}</div></BlueprintModalFrame>;
}
