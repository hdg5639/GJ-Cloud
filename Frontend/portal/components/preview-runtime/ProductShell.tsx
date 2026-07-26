"use client";

import type { ReactNode } from "react";
import type { PreviewPage, Purpose } from "./types";

function pageCapabilityCount(pages: PreviewPage[]): number {
  return new Set(pages.flatMap((page) => page.capabilityIds)).size;
}

// Workflow Composition Phase 2 Change Request §10 — purpose별 셸 차이는 개별 Block Variant와
// 별개 축이다. API_TEST는 기술적·압축형 콘솔, ADMIN은 사이드바+조밀한 툴바, PRODUCT_LIKE는
// 사용자-facing 제품 셸을 사용한다. 셸은 어떤 Block을 배치할지는 결정하지 않고 페이지 이동과
// 상위 정보 구조만 담당한다.
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
  const activePage = pages.find((page) => page.id === activePageId) ?? pages[0];
  const totalCapabilities = pageCapabilityCount(pages);

  if (purpose === "ADMIN") {
    return (
      <div className="overflow-hidden rounded-panel border border-line bg-panel">
        <div className="flex min-h-[520px]">
          <aside className="w-52 shrink-0 border-r border-line-strong bg-black/10 p-3">
            <div className="mb-4 px-2">
              <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">Admin Console</p>
              <p className="mt-1 text-xs text-muted-soft">운영 및 리소스 관리</p>
            </div>
            <nav className="space-y-1">
              {pages.map((page) => (
                <button
                  key={page.id}
                  type="button"
                  onClick={() => onSelectPage(page.id)}
                  className={`block w-full rounded-md px-3 py-2 text-left text-xs font-bold transition-colors ${
                    activePage?.id === page.id
                      ? "bg-soft text-brand-strong"
                      : "text-muted hover:bg-white/[0.04] hover:text-foreground"
                  }`}
                >
                  <span className="block truncate">{page.title}</span>
                  <span className="mt-0.5 block text-[10px] font-medium text-muted-soft">
                    {page.capabilityIds.length} capabilities
                  </span>
                </button>
              ))}
            </nav>
          </aside>
          <main className="min-w-0 flex-1 p-4">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-line-strong pb-3">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-widest text-muted-soft">Administration</p>
                <h3 className="mt-0.5 text-base font-extrabold">{activePage?.title ?? ""}</h3>
              </div>
              <div className="flex items-center gap-2">
                <span className="rounded bg-white/[0.06] px-2 py-1 text-[10px] font-bold text-muted">
                  {activePage?.capabilityIds.length ?? 0}개 기능
                </span>
                <span className="rounded bg-brand/15 px-2 py-1 text-[10px] font-bold text-brand-strong">ADMIN</span>
              </div>
            </div>
            {children}
          </main>
        </div>
      </div>
    );
  }

  if (purpose === "PRODUCT_LIKE") {
    return (
      <div className="overflow-hidden rounded-panel border border-line bg-panel shadow-2xl shadow-black/10">
        <header className="border-b border-line-strong bg-gradient-to-r from-brand/[0.08] via-transparent to-transparent px-5 pt-5">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="inline-flex size-7 items-center justify-center rounded-lg bg-brand text-xs font-black text-black">G</span>
                <div>
                  <p className="text-sm font-extrabold">Service Preview</p>
                  <p className="text-[11px] text-muted-soft">실제 사용자 흐름을 위한 동작형 프론트</p>
                </div>
              </div>
            </div>
            <span className="mb-1 rounded-full border border-line-strong bg-black/10 px-3 py-1 text-[10px] font-bold text-muted">
              {pages.length} pages · {totalCapabilities} capabilities
            </span>
          </div>
          <nav className="mt-5 flex gap-5 overflow-x-auto">
            {pages.map((page) => (
              <button
                key={page.id}
                type="button"
                onClick={() => onSelectPage(page.id)}
                className={`whitespace-nowrap border-b-2 pb-3 text-xs font-extrabold transition-colors ${
                  activePage?.id === page.id
                    ? "border-brand text-foreground"
                    : "border-transparent text-muted hover:text-foreground"
                }`}
              >
                {page.title}
              </button>
            ))}
          </nav>
        </header>
        <main className="p-5">
          <div className="mb-4">
            <p className="text-[10px] font-bold uppercase tracking-widest text-brand-strong">Current page</p>
            <h3 className="mt-1 text-lg font-black tracking-tight">{activePage?.title ?? ""}</h3>
          </div>
          {children}
        </main>
      </div>
    );
  }

  // API_TEST 또는 purpose 미지정 폴백 — Swagger 대체 목적에 맞게 기술 정보가 눈에 띄고,
  // 페이지 전환은 가장 압축된 콘솔형 segmented navigation을 사용한다.
  return (
    <div className="overflow-hidden rounded-panel border border-line bg-[#0d1012]">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line-strong px-4 py-3">
        <div>
          <p className="font-mono text-xs font-extrabold text-brand-strong">API Test Console</p>
          <p className="mt-0.5 text-[10px] text-muted-soft">OpenAPI operations · request/response inspection</p>
        </div>
        <div className="flex gap-2 font-mono text-[10px] text-muted">
          <span className="rounded border border-line-strong px-2 py-1">PAGES {pages.length}</span>
          <span className="rounded border border-line-strong px-2 py-1">OPS {totalCapabilities}</span>
        </div>
      </div>
      <div className="border-b border-line-strong bg-black/20 px-3 py-2">
        <nav className="flex gap-1 overflow-x-auto">
          {pages.map((page) => (
            <button
              key={page.id}
              type="button"
              onClick={() => onSelectPage(page.id)}
              className={`whitespace-nowrap rounded px-2.5 py-1.5 font-mono text-[11px] font-bold ${
                activePage?.id === page.id
                  ? "bg-brand/15 text-brand-strong"
                  : "text-muted hover:bg-white/[0.04] hover:text-foreground"
              }`}
            >
              /{page.id}
            </button>
          ))}
        </nav>
      </div>
      <main className="p-4">
        <div className="mb-3 flex items-center justify-between gap-2 font-mono text-[10px] text-muted-soft">
          <span>PAGE: {activePage?.title ?? ""}</span>
          <span>{activePage?.capabilityIds.length ?? 0} operations</span>
        </div>
        {children}
      </main>
    </div>
  );
}
