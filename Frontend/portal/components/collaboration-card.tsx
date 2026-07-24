"use client";

import { api } from "@/lib/api-client";
import type { CollaborationResponse } from "@/lib/types";

interface Props {
  item: CollaborationResponse;
  accessToken: string;
  isOwnerOrAdmin: boolean;
  currentUserId?: string;
  onEdit: (item: CollaborationResponse) => void;
  onUpdate: (item: CollaborationResponse) => void;
  onDelete: (id: string) => void;
}

function relativeTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return "방금";
  if (m < 60) return `${m}분 전`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}

const TYPE_META = {
  NOTE: {
    label: "메모",
    badge: { bg: "rgba(59,130,246,0.22)", color: "#dbeafe", border: "rgba(96,165,250,0.5)" },
    pinned: { bg: "rgba(59,130,246,0.08)", border: "#3b82f6", text: "#dbeafe", sub: "#93c5fd", icon: "#60a5fa" },
  },
  NOTICE: {
    label: "공지",
    badge: { bg: "rgba(250,199,117,0.2)", color: "#fef3c7", border: "rgba(251,191,36,0.5)" },
    pinned: { bg: "rgba(250,199,117,0.08)", border: "#d69e2e", text: "#fde68a", sub: "#fbbf24", icon: "#f0b429" },
  },
  REQUEST: {
    label: "요청",
    badge: { bg: "rgba(248,113,113,0.2)", color: "#fee2e2", border: "rgba(248,113,113,0.5)" },
    pinned: { bg: "rgba(168,85,247,0.08)", border: "#a855f7", text: "#e9d5ff", sub: "#d8b4fe", icon: "#c084fc" },
  },
};

function IconEdit({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14, cursor: "pointer" }}>
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
    </svg>
  );
}

function IconTrash({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14, cursor: "pointer" }}>
      <polyline points="3 6 5 6 21 6"/>
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
    </svg>
  );
}

function IconPinFilled({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 24 24" fill={color} style={{ width: 14, height: 14 }}>
      <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5v6l1 1 1-1v-6h5v-2l-2-2z"/>
    </svg>
  );
}

function IconPin({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14, cursor: "pointer" }}>
      <line x1="12" y1="17" x2="12" y2="22"/>
      <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/>
    </svg>
  );
}

export default function CollaborationCard({ item, accessToken, isOwnerOrAdmin, currentUserId, onEdit, onUpdate, onDelete }: Props) {
  const meta = TYPE_META[item.type];
  const isAuthor = currentUserId === item.createdById;
  const canEdit = isAuthor || isOwnerOrAdmin;

  const iconColor = item.pinned ? meta.pinned.icon : "#9ca3af";

  async function handlePin() {
    try { onUpdate(await api.collab.pin(accessToken, item.id)); }
    catch (err) { alert(err instanceof Error ? err.message : "처리 실패"); }
  }
  async function handleResolve() {
    try { onUpdate(await api.collab.resolve(accessToken, item.id)); }
    catch (err) { alert(err instanceof Error ? err.message : "처리 실패"); }
  }
  async function handleDelete() {
    if (!confirm("삭제하시겠습니까?")) return;
    try { await api.collab.delete(accessToken, item.id); onDelete(item.id); }
    catch (err) { alert(err instanceof Error ? err.message : "삭제 실패"); }
  }

  const containerStyle: React.CSSProperties = item.pinned
    ? { backgroundColor: meta.pinned.bg, borderLeft: `3px solid ${meta.pinned.border}`, borderRadius: "0 8px 8px 0", padding: "15px 16px", position: "relative" }
    : { backgroundColor: "var(--panel)", border: "0.5px solid var(--line)", borderRadius: 8, padding: "15px 16px", position: "relative" };

  const titleColor = item.pinned ? meta.pinned.text : "var(--foreground)";
  const contentColor = item.pinned ? meta.pinned.sub : "var(--muted)";
  const metaColor = item.pinned ? meta.pinned.icon : "#9ca3af";

  return (
    <div style={containerStyle}>
      {/* 고정 아이콘 (pinned만) */}
      {item.pinned && (
        <span style={{ position: "absolute", top: 10, right: 10 }}>
          <IconPinFilled color={meta.pinned.icon} />
        </span>
      )}

      {/* 뱃지 행 */}
      <div style={{ display: "flex", alignItems: "center", gap: 4, flexWrap: "wrap" }}>
        <span style={{ backgroundColor: meta.badge.bg, color: meta.badge.color, border: `1px solid ${meta.badge.border}`, fontSize: 12, padding: "2px 8px", borderRadius: 6, fontWeight: 700 }}>
          {meta.label}
        </span>
        {item.tag && (
          <span style={{
            backgroundColor: "rgba(255,255,255,0.1)",
            color: "#f8fafc",
            border: "1px solid rgba(255,255,255,0.2)",
            fontSize: 12, padding: "2px 8px", borderRadius: 6, fontWeight: 650,
          }}>
            #{item.tag}
          </span>
        )}
        {item.type === "REQUEST" && item.status === "UNSOLVED" && (
          <span style={{ backgroundColor: "rgba(248,113,113,0.2)", color: "#fee2e2", border: "1px solid rgba(248,113,113,0.45)", fontSize: 12, padding: "2px 8px", borderRadius: 6, fontWeight: 700 }}>미해결</span>
        )}
        {item.type === "REQUEST" && item.status === "SOLVED" && (
          <span style={{ backgroundColor: "rgba(121,217,94,0.18)", color: "#dcfce7", border: "1px solid rgba(134,239,172,0.4)", fontSize: 12, padding: "2px 8px", borderRadius: 6, fontWeight: 700 }}>해결됨</span>
        )}
      </div>

      {/* 제목 */}
      <p style={{ fontSize: 15, fontWeight: 650, margin: "10px 0 5px", color: titleColor, lineHeight: 1.45 }}>{item.title}</p>
      {/* 내용 */}
      <p style={{ fontSize: 14, margin: "0 0 11px", color: contentColor, lineHeight: 1.65, display: "-webkit-box", WebkitLineClamp: 3, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
        {item.content}
      </p>

      {/* 하단: 작성자·시간 + 액션 */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <span style={{ fontSize: 12, color: metaColor }}>{item.createdByEmail} · {relativeTime(item.createdAt)}</span>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          {item.type === "REQUEST" && item.status === "UNSOLVED" && (
            <button
              onClick={handleResolve}
              style={{ height: 28, padding: "0 11px", fontSize: 12, width: "auto", cursor: "pointer" }}
              className="border border-line-strong rounded text-muted hover:bg-white/[0.06]"
            >
              해결 완료
            </button>
          )}
          {isOwnerOrAdmin && (
            <button onClick={handlePin} title={item.pinned ? "고정 해제" : "고정"} style={{ background: "none", border: "none", padding: 0, lineHeight: 0 }}>
              {item.pinned ? <IconPinFilled color={iconColor} /> : <IconPin color={iconColor} />}
            </button>
          )}
          {canEdit && (
            <button onClick={() => onEdit(item)} title="수정" style={{ background: "none", border: "none", padding: 0, lineHeight: 0 }}>
              <IconEdit color={iconColor} />
            </button>
          )}
          {canEdit && (
            <button onClick={handleDelete} title="삭제" style={{ background: "none", border: "none", padding: 0, lineHeight: 0 }}>
              <IconTrash color={item.pinned ? meta.pinned.icon : "#ef4444"} />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
