"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api-client";
import type { CollaborationResponse, CollaborationType, ScopeType, TagResponse } from "@/lib/types";

interface Props {
  accessToken: string;
  scopeType: ScopeType;
  scopeId: string;
  onClose: () => void;
  onSuccess: (item: CollaborationResponse) => void;
  editing?: CollaborationResponse;
}

const TYPE_LABELS: Record<CollaborationType, string> = { NOTE: "메모", NOTICE: "공지", REQUEST: "요청" };

export default function CollaborationWriteModal({ accessToken, scopeType, scopeId, onClose, onSuccess, editing }: Props) {
  const [type, setType] = useState<CollaborationType>(editing?.type ?? "NOTE");
  const [tag, setTag] = useState(editing?.tag ?? "");
  const [title, setTitle] = useState(editing?.title ?? "");
  const [content, setContent] = useState(editing?.content ?? "");
  const [loading, setLoading] = useState(false);
  const [tagSuggestions, setTagSuggestions] = useState<TagResponse[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const tagTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (tagTimer.current) clearTimeout(tagTimer.current);
    if (!tag) { setTagSuggestions([]); return; }
    tagTimer.current = setTimeout(() => {
      api.collab.searchTags(accessToken, scopeType, scopeId, tag)
        .then(setTagSuggestions)
        .catch(() => {});
    }, 300);
  }, [tag]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;
    setLoading(true);
    try {
      const body = { scopeType, scopeId, type, tag: tag.trim().toLowerCase() || undefined, title: title.trim(), content: content.trim() };
      const result = editing
        ? await api.collab.update(accessToken, editing.id, { tag: body.tag, title: body.title, content: body.content })
        : await api.collab.create(accessToken, body);
      onSuccess(result);
    } catch (err) {
      alert(err instanceof Error ? err.message : "저장에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-panel border border-line rounded-xl shadow-xl w-full max-w-lg">
        <div className="flex items-center justify-between px-5 py-4 border-b border-line">
          <h2 className="text-sm font-medium text-foreground">{editing ? "협업 항목 수정" : "새 협업 항목"}</h2>
          <button onClick={onClose} className="text-muted-soft hover:text-muted text-lg leading-none">×</button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {!editing && (
            <div className="flex gap-2">
              {(["NOTE", "NOTICE", "REQUEST"] as CollaborationType[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setType(t)}
                  className={`flex-1 text-sm py-1.5 rounded-md border font-medium transition-colors ${
                    type === t
                      ? t === "NOTE" ? "border-[#3b82f6] bg-[#3b82f6]/10 text-[#93c5fd]"
                        : t === "NOTICE" ? "border-[#d69e2e] bg-[#d69e2e]/10 text-[#fbbf24]"
                        : "border-[#a855f7] bg-[#a855f7]/10 text-[#d8b4fe]"
                      : "border-line-strong text-muted hover:border-white/20"
                  }`}
                >
                  {TYPE_LABELS[t]}
                </button>
              ))}
            </div>
          )}

          <div className="relative">
            <label htmlFor="collab-tag" className="text-xs text-muted block mb-1">태그 <span className="text-muted-soft">(선택)</span></label>
            <input
              id="collab-tag"
              name="collab-tag"
              type="text"
              value={tag}
              onChange={(e) => { setTag(e.target.value); setShowSuggestions(true); }}
              onFocus={() => setShowSuggestions(true)}
              onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
              placeholder="예: port, email, cpu"
              className="w-full h-8 px-3 border border-line-strong bg-transparent text-foreground rounded-md text-sm"
            />
            {showSuggestions && tagSuggestions.length > 0 && (
              <div className="absolute top-full left-0 right-0 mt-1 bg-panel border border-line-strong rounded-md shadow-md z-10 overflow-hidden">
                {tagSuggestions.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    onMouseDown={() => { setTag(s.name); setShowSuggestions(false); }}
                    className="w-full text-left text-sm px-3 py-1.5 hover:bg-white/[0.06] flex items-center justify-between"
                  >
                    <span>#{s.name}</span>
                    <span className="text-xs text-muted-soft">{s.usageCount}회</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
            <label htmlFor="collab-title" className="text-xs text-muted block mb-1">제목</label>
            <input
              id="collab-title"
              name="collab-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="제목을 입력하세요"
              maxLength={200}
              required
              className="w-full h-9 px-3 border border-line-strong bg-transparent text-foreground rounded-md text-sm"
            />
          </div>

          <div>
            <label htmlFor="collab-content" className="text-xs text-muted block mb-1">내용</label>
            <textarea
              id="collab-content"
              name="collab-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="내용을 입력하세요"
              rows={5}
              required
              className="w-full px-3 py-2 border border-line-strong bg-transparent text-foreground rounded-md text-sm resize-none"
            />
          </div>

          {type === "REQUEST" && !editing && (
            <p className="text-xs text-[#d8b4fe] bg-[#a855f7]/10 border border-[#a855f7]/25 rounded px-3 py-2">
              요청으로 등록되며 초기 상태는 <strong>미해결</strong>입니다.
            </p>
          )}

          <div className="flex gap-2 justify-end pt-1">
            <button type="button" onClick={onClose} className="text-sm px-4 h-8 border border-line-strong text-muted rounded-md hover:bg-white/[0.06]">
              취소
            </button>
            <button
              type="submit"
              disabled={loading}
              className="text-sm px-4 h-8 bg-brand text-[#0a0c08] rounded-md hover:bg-brand-strong disabled:opacity-50 font-bold"
            >
              {loading ? "저장 중..." : editing ? "수정" : "등록"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
