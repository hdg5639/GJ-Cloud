"use client";

import type { ReactNode } from "react";
import type { PreviewPage, Purpose } from "./types";

// Workflow Composition Phase 2 Change Request §10 "Purpose-specific shell behavior" — "The page
// shell must vary by purpose, not only the list component"라는 명시적 요구를 지금까지 어기고
// 있었다(purpose와 무관하게 항상 같은 가로 pill 내비게이션 하나뿐이었음, PreviewPageRenderer 안의
// Variant 컴포넌트 교체와 별개 축). ADMIN부터 먼저 만든다(§10 "sidebar navigation, dense toolbar,
// operational dashboard") — API_TEST/PRODUCT_LIKE는 이번 조각에서 기존 가로 pill 그대로 유지한다
// (각자의 차별화된 셸은 다음 조각의 몫으로 명시적으로 미룸).
export function ProductShell({
  purpose,
  pages,
  activePageId,
  onSelectPage,
  children,
}: {
  purpose: Purpose | null;
  pages: PreviewPage[];
  activePageId: string | null;
  onSelectPage: (pageId: string) => void;
  children: ReactNode;
}) {
  if (purpose === "ADMIN") {
    const activePage = pages.find((page) => page.id === activePageId);
    return (
      <div className="flex gap-4">
        <nav className="w-44 shrink-0 space-y-1 border-r border-line-strong pr-3">
          {pages.map((page) => (
            <button
              key={page.id}
              type="button"
              onClick={() => onSelectPage(page.id)}
              className={`block w-full rounded-md px-3 py-2 text-left text-xs font-bold ${
                activePageId === page.id ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04]"
              }`}
            >
              {page.title}
            </button>
          ))}
        </nav>
        <div className="min-w-0 flex-1">
          {/* §10 "dense toolbar" — 가로 pill보다 조밀하게, 현재 페이지명+capability 개수를 한 줄에 보여준다. */}
          <div className="mb-3 flex items-center justify-between border-b border-line-strong pb-2">
            <h3 className="text-sm font-extrabold">{activePage?.title ?? ""}</h3>
            <div className="flex items-center gap-2">
              <span className="rounded bg-white/[0.06] px-2 py-0.5 text-[10px] font-bold text-muted">
                {activePage?.capabilityIds.length ?? 0}개 기능
              </span>
              <span className="rounded bg-brand/15 px-2 py-0.5 text-[10px] font-bold text-brand-strong">관리자 모드</span>
            </div>
          </div>
          {children}
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="mb-3 flex flex-wrap gap-2">
        {pages.map((page) => (
          <button
            key={page.id}
            type="button"
            onClick={() => onSelectPage(page.id)}
            className={`rounded-md border px-3 py-1.5 text-xs font-bold ${
              activePageId === page.id ? "border-brand bg-soft text-brand-strong" : "border-line-strong text-muted"
            }`}
          >
            {page.title}
          </button>
        ))}
      </div>
      {children}
    </>
  );
}
