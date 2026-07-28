import { statusTone, toneStyle } from "./status";

// 상태값을 색 배지로 렌더한다(running=초록, provisioning류=앰버, stopped류=회색, failed류=빨강).
export function StatusBadge({ value, size = "md" }: { value: string; size?: "sm" | "md" }) {
  const style = toneStyle(statusTone(value));
  const pad = size === "sm" ? "px-2 py-0.5 text-[10px]" : "px-2.5 py-1 text-[11px]";
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border font-extrabold ${pad}`}
      style={{ color: style.color, background: style.background, borderColor: style.borderColor }}
    >
      <span style={{ width: 6, height: 6, borderRadius: "9999px", background: style.color }} />
      {value}
    </span>
  );
}
