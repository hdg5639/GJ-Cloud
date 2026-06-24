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

const TYPE_STYLE = {
  NOTE: { badge: "bg-blue-50 text-blue-700 border-blue-200", border: "border-gray-200" },
  NOTICE: { badge: "bg-amber-50 text-amber-700 border-amber-200", border: "border-amber-200" },
  REQUEST: { badge: "bg-purple-50 text-purple-700 border-purple-200", border: "border-purple-200" },
};

const TYPE_LABEL = { NOTE: "메모", NOTICE: "공지", REQUEST: "요청" };

export default function CollaborationCard({ item, accessToken, isOwnerOrAdmin, currentUserId, onEdit, onUpdate, onDelete }: Props) {
  const style = TYPE_STYLE[item.type];
  const isAuthor = currentUserId === item.createdById;
  const canEdit = isAuthor || isOwnerOrAdmin;

  async function handlePin() {
    try {
      const updated = await api.collab.pin(accessToken, item.id);
      onUpdate(updated);
    } catch (err) {
      alert(err instanceof Error ? err.message : "처리에 실패했습니다");
    }
  }

  async function handleResolve() {
    try {
      const updated = await api.collab.resolve(accessToken, item.id);
      onUpdate(updated);
    } catch (err) {
      alert(err instanceof Error ? err.message : "처리에 실패했습니다");
    }
  }

  async function handleDelete() {
    if (!confirm("삭제하시겠습니까?")) return;
    try {
      await api.collab.delete(accessToken, item.id);
      onDelete(item.id);
    } catch (err) {
      alert(err instanceof Error ? err.message : "삭제에 실패했습니다");
    }
  }

  return (
    <div className={`border rounded-xl px-4 py-3.5 transition-shadow hover:shadow-sm ${
      item.pinned ? `${style.border} bg-amber-50/30 shadow-sm` : `border-gray-200 bg-white`
    }`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 flex-wrap">
          {item.pinned && <span className="text-sm">📌</span>}
          <span className={`text-[11px] font-medium px-2 py-0.5 rounded border ${style.badge}`}>
            {TYPE_LABEL[item.type]}
          </span>
          {item.type === "REQUEST" && item.status && (
            <span className={`text-[11px] font-medium px-2 py-0.5 rounded border ${
              item.status === "SOLVED"
                ? "bg-green-50 text-green-700 border-green-200"
                : "bg-red-50 text-red-600 border-red-200"
            }`}>
              {item.status === "SOLVED" ? "해결됨" : "미해결"}
            </span>
          )}
          {item.tag && (
            <span className="text-[11px] text-gray-500 bg-gray-50 border border-gray-200 px-1.5 py-0.5 rounded">
              #{item.tag}
            </span>
          )}
        </div>
        <div className="flex items-center gap-1 flex-shrink-0">
          {isOwnerOrAdmin && (
            <button
              onClick={handlePin}
              title={item.pinned ? "고정 해제" : "고정"}
              className={`text-xs px-2 py-1 rounded hover:bg-gray-100 ${item.pinned ? "text-amber-600" : "text-gray-400"}`}
            >
              📌
            </button>
          )}
          {canEdit && (
            <button onClick={() => onEdit(item)} className="text-xs px-2 py-1 rounded text-gray-400 hover:text-gray-600 hover:bg-gray-100">
              수정
            </button>
          )}
          {canEdit && (
            <button onClick={handleDelete} className="text-xs px-2 py-1 rounded text-red-400 hover:text-red-600 hover:bg-red-50">
              삭제
            </button>
          )}
        </div>
      </div>

      <p className="text-sm font-medium text-gray-900 mt-2">{item.title}</p>
      <p className="text-sm text-gray-600 mt-1 whitespace-pre-wrap leading-relaxed">{item.content}</p>

      <div className="flex items-center justify-between mt-3 pt-2.5 border-t border-gray-100">
        <span className="text-xs text-gray-400">{item.createdByEmail} · {relativeTime(item.createdAt)}</span>
        {item.type === "REQUEST" && item.status === "UNSOLVED" && (
          <button
            onClick={handleResolve}
            className="text-xs px-3 h-6 bg-green-50 text-green-700 border border-green-200 rounded hover:bg-green-100"
          >
            해결 완료
          </button>
        )}
        {item.type === "REQUEST" && item.status === "SOLVED" && item.resolvedByEmail && (
          <span className="text-xs text-gray-400">{item.resolvedByEmail} 해결</span>
        )}
      </div>
    </div>
  );
}
