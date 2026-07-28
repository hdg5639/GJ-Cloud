"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { BlueprintPermission } from "../core";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function PermissionMatrixModal({ open, onClose, groups, onSave }: { open: boolean; onClose: () => void; groups: Array<{ id: string; label: string; permissions: BlueprintPermission[] }>; onSave: (permissions: BlueprintPermission[]) => void | Promise<void> }) {
  const initial = groups.flatMap((group) => group.permissions);
  const [permissions, setPermissions] = useState(initial);
  function toggle(id: string, enabled: boolean) { setPermissions((current) => current.map((permission) => permission.id === id && !permission.locked ? { ...permission, enabled } : permission)); }
  return <BlueprintModalFrame open={open} onClose={onClose} title="Permission matrix" description="Configure capability access by permission group." eyebrow="Access control" size="lg" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" onClick={() => onSave(permissions)}>Save permissions</Button></>}><div className="space-y-5">{groups.map((group) => <section key={group.id}><h3 className="mb-2 text-sm font-extrabold">{group.label}</h3><div className="overflow-hidden rounded-[13px] border border-line">{group.permissions.map((permission) => { const current = permissions.find((item) => item.id === permission.id) ?? permission; return <label key={permission.id} className="flex items-start justify-between gap-4 border-b border-line p-3 last:border-0"><div><strong className="text-sm">{permission.label}</strong>{permission.description && <p className="mt-1 text-xs text-muted-soft">{permission.description}</p>}</div><input type="checkbox" checked={current.enabled} disabled={permission.locked} onChange={(event) => toggle(permission.id, event.target.checked)} className="mt-1 h-4 w-4 accent-[var(--brand)]" /></label>; })}</div></section>)}</div></BlueprintModalFrame>;
}
