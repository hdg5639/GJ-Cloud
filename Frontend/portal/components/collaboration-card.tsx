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
  NOTE:    { label: "메모",  badge: "bg-blue-100 text-blue-700",   pinBg: "bg-blue-50",   pinBorder: "border-l-blue-400"   },
  NOTICE:  { label: "공지",  badge: "bg-amber-100 text-amber-700", pinBg: "bg-amber-50",  pinBorder: "border-l-amber-400"  },
  REQUEST: { label: "요청",  badge: "bg-purple-100 text-purple-700", pinBg: "bg-purple-50", pinBorder: "border-l-purple-400" },
};

function IconEdit() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
    </svg>
  );
}

function IconTrash() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
      <polyline points="3 6 5 6 21 6"/>
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
    </svg>
  );
}

function IconPin({ filled }: { filled: boolean }) {
  return filled ? (
    <svg viewBox="0 0 24 24" fill="currentColor" className="w-3.5 h-3.5">
      <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5v6l1 1 1-1v-6h5v-2l-2-2z"/>
    </svg>
  ) : (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
      <line x1="12" y1="17" x2="12" y2="22"/>
      <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/>
    </svg>
  );
}

export default function CollaborationCard({ item, accessToken, isOwnerOrAdmin, currentUserId, onEdit, onUpdate, onDelete }: Props) {
  const meta = TYPE_META[item.type];
  const isAuthor = currentUserId === item.createdById;
  const canEdit = isAuthor || isOwnerOrAdmin;

  const cardClass = item.pinned
    ? `border-l-[3px] ${meta.pinBorder} ${meta.pinBg} rounded-r-xl`
    : "border border-gray-200 bg-white rounded-xl";

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

  return (
    <div className={`px-3.5 py-3 transition-shadow hover:shadow-sm ${cardClass}`}>
      {/* 상단 뱃지 + 액션 */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className={`text-[11px] font-medium px-2 py-0.5 rounded-full ${meta.badge}`}>
            {meta.label}
          </span>
          {item.tag && (
            <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
              #{item.tag}
            </span>
          )}
          {item.type === "REQUEST" && item.status && (
            <span className={`text-[11px] font-medium px-2 py-0.5 rounded-full ${
              item.status === "SOLVED" ? "bg-green-100 text-green-700" : "bg-red-100 text-red-600"
            }`}>
              {item.status === "SOLVED" ? "해결됨" : "미해결"}
            </span>
          )}
        </div>
        <div className="flex items-center gap-0.5 flex-shrink-0">
          {isOwnerOrAdmin && (
            <button
              onClick={handlePin}
              title={item.pinned ? "고정 해제" : "고정"}
              className={`p-1.5 rounded hover:bg-black/5 transition-colors ${item.pinned ? "text-amber-500" : "text-gray-300 hover:text-gray-500"}`}
            >
              <IconPin filled={item.pinned} />
            </button>
          )}
          {canEdit && (
            <button
              onClick={() => onEdit(item)}
              title="수정"
              className="p-1.5 rounded text-gray-300 hover:text-gray-600 hover:bg-black/5 transition-colors"
            >
              <IconEdit />
            </button>
          )}
          {canEdit && (
            <button
              onClick={handleDelete}
              title="삭제"
              className="p-1.5 rounded text-gray-300 hover:text-red-500 hover:bg-red-50 transition-colors"
            >
              <IconTrash />
            </button>
          )}
        </div>
      </div>

      {/* 제목 + 내용 */}
      <p className="text-sm font-semibold text-gray-900 leading-snug">{item.title}</p>
      <p className="text-xs text-gray-500 mt-1 whitespace-pre-wrap leading-relaxed line-clamp-3">{item.content}</p>

      {/* 하단: 작성자 · 시간 + 해결 버튼 */}
      <div className="flex items-center justify-between mt-2.5 pt-2 border-t border-black/5">
        <span className="text-[11px] text-gray-400">{item.createdByEmail} · {relativeTime(item.createdAt)}</span>
        {item.type === "REQUEST" && item.status === "UNSOLVED" && (
          <button
            onClick={handleResolve}
            className="text-[11px] font-medium px-2.5 h-5 bg-green-50 text-green-700 border border-green-200 rounded-full hover:bg-green-100 transition-colors"
          >
            해결 완료
          </button>
        )}
        {item.type === "REQUEST" && item.status === "SOLVED" && item.resolvedByEmail && (
          <span className="text-[11px] text-gray-400">{item.resolvedByEmail} 해결</span>
        )}
      </div>
    </div>
  );
}
