"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api-client";
import { Avatar } from "@/components/ui/avatar";
import { maskEmail } from "@/lib/mask-email";
import type { MemberSearchResult } from "@/lib/types";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface InviteTarget {
  userId?: string;
  email: string;
  nickname?: string;
  profileImageUrl?: string;
}

// GitHub 협업자 초대처럼 닉네임/이메일 일부만 입력해도 매칭되는 사용자가 아바타와 함께 드롭다운에
// 뜨고, 그중 하나를 선택해서 초대할 수 있게 하는 검색창. 검색 결과에 없는 이메일이면(미가입 사용자)
// 기존처럼 그 이메일로 직접 초대하는 폴백도 같이 제공한다.
export function MemberInviteCombobox({
  accessToken,
  orgId,
  onSelect,
}: {
  accessToken: string;
  orgId: string;
  onSelect: (target: InviteTarget) => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<MemberSearchResult[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // setState는 전부 타이머/프라미스 콜백 안에서만 호출 — 이펙트 본문 자체는 디바운스 타이머 예약만 함
  useEffect(() => {
    const trimmed = query.trim();
    const timer = setTimeout(() => {
      if (trimmed.length < 2) {
        setResults([]);
        setSearching(false);
        return;
      }
      setSearching(true);
      api.org
        .searchMembers(accessToken, orgId, trimmed)
        .then(setResults)
        .catch(() => setResults([]))
        .finally(() => setSearching(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [query, accessToken, orgId]);

  useEffect(() => {
    if (!open) return;
    function onClickOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [open]);

  function pick(result: MemberSearchResult) {
    onSelect({
      userId: result.userId,
      email: result.email,
      nickname: result.nickname ?? undefined,
      profileImageUrl: result.profileImageUrl ?? undefined,
    });
    reset();
  }

  function pickRawEmail() {
    onSelect({ email: query.trim() });
    reset();
  }

  function reset() {
    setQuery("");
    setResults([]);
    setOpen(false);
  }

  const trimmed = query.trim();
  const looksLikeEmail = EMAIL_RE.test(trimmed);
  const exactEmailMatch = results.some((r) => r.email.toLowerCase() === trimmed.toLowerCase());

  return (
    <div ref={containerRef} className="relative min-w-0 flex-1">
      <input
        type="text"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        placeholder="닉네임 또는 이메일로 검색"
        className="w-full min-h-[42px] rounded-[9px] border border-line-strong bg-panel px-3 text-sm text-foreground outline-none placeholder:text-muted-soft focus:border-brand focus:ring-2 focus:ring-brand/20"
      />
      {open && trimmed.length >= 2 && (
        <div className="absolute left-0 right-0 top-[calc(100%+6px)] z-20 max-h-72 overflow-y-auto rounded-[10px] border border-line-strong bg-panel shadow-xl shadow-black/30">
          {searching && <div className="px-3 py-2.5 text-xs text-muted-soft">검색 중...</div>}
          {!searching && results.length === 0 && !looksLikeEmail && (
            <div className="px-3 py-2.5 text-xs text-muted-soft">일치하는 사용자가 없어요</div>
          )}
          {!searching &&
            results.map((r) => (
              <button
                key={r.userId}
                type="button"
                onClick={() => pick(r)}
                className="flex w-full items-center gap-2.5 px-3 py-2 text-left hover:bg-white/[0.04]"
              >
                <Avatar
                  nickname={r.nickname}
                  email={r.email}
                  profileImageUrl={r.profileImageUrl}
                  sizePx={28}
                  textSizeClassName="text-[11px]"
                />
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold">{r.nickname ?? r.email}</p>
                  <p className="truncate text-xs text-muted-soft">{maskEmail(r.email)}</p>
                </div>
              </button>
            ))}
          {!searching && looksLikeEmail && !exactEmailMatch && (
            <button
              type="button"
              onClick={pickRawEmail}
              className="w-full border-t border-line px-3 py-2.5 text-left text-sm font-bold text-brand-strong hover:bg-white/[0.04]"
            >
              {trimmed}로 직접 초대
            </button>
          )}
        </div>
      )}
    </div>
  );
}
