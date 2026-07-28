import { Table, Td, Th } from "@/components/ui/table";
import { BlueprintStatusPill, formatBlueprintValue } from "../core";
import type { BlueprintRecord } from "../core";

export function AuditLogTable({ entries, onSelect }: { entries: BlueprintRecord[]; onSelect?: (entry: BlueprintRecord) => void }) {
  return <Table><thead><tr><Th>Time</Th><Th>Actor</Th><Th>Action</Th><Th>Target</Th><Th>Result</Th><Th>Source</Th></tr></thead><tbody>{entries.map((entry, index) => <tr key={String(entry.id ?? index)} onClick={() => onSelect?.(entry)} className={onSelect ? "cursor-pointer hover:bg-white/[0.025]" : undefined}><Td>{formatBlueprintValue(entry.timestamp ?? entry.createdAt)}</Td><Td>{formatBlueprintValue(entry.actor ?? entry.user)}</Td><Td><strong>{formatBlueprintValue(entry.action ?? entry.event)}</strong></Td><Td>{formatBlueprintValue(entry.target ?? entry.resource)}</Td><Td><BlueprintStatusPill value={entry.result ?? entry.status ?? "success"} /></Td><Td>{formatBlueprintValue(entry.ip ?? entry.source)}</Td></tr>)}</tbody></Table>;
}
