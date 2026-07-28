"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ProductShell } from "./ProductShell";
import { PreviewPageRenderer } from "./PreviewPageRenderer";
import { ApiCallLog } from "./ApiCallLog";
import { rowId } from "./api";
import type { Block } from "./blueprint";
import type { ApiBinding, FlowBlueprint } from "./flow/types";
import type { ApiCallLogEntry, PreviewCapability, PreviewPage, PreviewAuthStrategy, Purpose } from "./types";
import type { PreviewPagePlan } from "@/lib/types";

type NavigationType = "OPEN_PAGE" | "OPEN_OVERLAY" | "GO_BACK" | "REPLACE_ROUTE";

// 배포 정적 앱과 포털 라이브 프리뷰가 공유하는 런타임 호스트. 백엔드가 만든 config/pages/blocks/flows/
// bindings를 받아 셸 + 페이지 이동 + PreviewPageRenderer를 조립한다. 선택/페이지 상태의 소스 오브
// 트루스는 URL 쿼리파라미터(?page=&selected=)라 새로고침·뒤로가기·직접 URL 진입이 유지된다.
// 라우터 라이브러리에 의존하지 않도록 순수 History API를 쓴다(배포 아티팩트엔 next/router가 없다).
export function PreviewRuntimeApp({
  apiBaseUrl,
  capabilities,
  pages,
  pagePlans = [],
  pageBlocks,
  flows,
  bindings,
  authStrategy,
  purpose,
}: {
  apiBaseUrl: string;
  capabilities: PreviewCapability[];
  pages: PreviewPage[];
  pagePlans?: PreviewPagePlan[];
  pageBlocks: Record<string, Block[]>;
  flows: FlowBlueprint[];
  bindings: ApiBinding[];
  authStrategy: PreviewAuthStrategy;
  purpose: Purpose | null;
}) {
  const [search, setSearch] = useState<string>(() =>
    typeof window === "undefined" ? "" : window.location.search
  );
  const [selectedRow, setSelectedRowState] = useState<Record<string, unknown> | null>(null);
  const [authToken, setAuthToken] = useState<string | null>(null);
  const [apiLog, setApiLog] = useState<ApiCallLogEntry[]>([]);

  useEffect(() => {
    const sync = () => setSearch(window.location.search);
    window.addEventListener("popstate", sync);
    return () => window.removeEventListener("popstate", sync);
  }, []);

  const params = useMemo(() => new URLSearchParams(search), [search]);

  const routeParameterNames = useCallback(
    () =>
      new Set<string>([
        "selected",
        "id",
        ...pagePlans.flatMap((plan) => plan.routeParameters.map((parameter) => parameter.name)),
      ]),
    [pagePlans]
  );

  const writeQuery = useCallback(
    (pageId: string | null, parameters: Record<string, string> = {}, mode: "push" | "replace" = "push") => {
      const next = new URLSearchParams(window.location.search);
      for (const name of routeParameterNames()) next.delete(name);
      if (pageId) next.set("page", pageId);
      else next.delete("page");
      for (const [name, value] of Object.entries(parameters)) {
        if (value) next.set(name, value);
      }
      const query = next.toString();
      const href = query ? `${window.location.pathname}?${query}` : window.location.pathname;
      if (mode === "replace") window.history.replaceState(null, "", href);
      else window.history.pushState(null, "", href);
      setSearch(window.location.search);
    },
    [routeParameterNames]
  );

  const activePageId = params.get("page") ?? pages[0]?.id ?? null;
  const activePage = pages.find((page) => page.id === activePageId) ?? pages[0];
  const activePagePlan = pagePlans.find((plan) => plan.id === activePageId);

  const selectedIdFromUrl = params.get("selected");
  const effectiveSelectedRow = selectedIdFromUrl
    ? selectedRow && rowId(selectedRow) === selectedIdFromUrl
      ? selectedRow
      : { id: selectedIdFromUrl }
    : null;
  const routeParameters = useMemo(
    () => Object.fromEntries(Array.from(params.entries()).filter(([name]) => name !== "page")),
    [params]
  );

  function selectPage(id: string | null) {
    setSelectedRowState(null);
    writeQuery(id);
  }

  function selectRow(row: Record<string, unknown> | null) {
    setSelectedRowState(row);
    const next = new URLSearchParams(window.location.search);
    if (row) next.set("selected", rowId(row));
    else next.delete("selected");
    const query = next.toString();
    window.history.pushState(null, "", query ? `${window.location.pathname}?${query}` : window.location.pathname);
    setSearch(window.location.search);
  }

  function navigate(targetPageId: string | null, parameters: Record<string, string>, type: NavigationType) {
    if (type === "GO_BACK") {
      window.history.back();
      return;
    }
    if (type === "OPEN_OVERLAY") {
      writeQuery(targetPageId ?? activePageId, parameters);
      return;
    }
    setSelectedRowState(null);
    writeQuery(targetPageId, parameters, type === "REPLACE_ROUTE" ? "replace" : "push");
  }

  const config = {
    apiBaseUrl: apiBaseUrl.trim(),
    authToken,
    onAuthTokenChange: setAuthToken,
    authStrategy,
    purpose,
    onApiCall: (entry: ApiCallLogEntry) => setApiLog((prev) => [entry, ...prev].slice(0, 30)),
  };

  return (
    <div className="flex flex-col gap-4">
      <ProductShell purpose={purpose} pages={pages} activePageId={activePageId} onSelectPage={selectPage}>
        {activePage && apiBaseUrl.trim() && (
          <PreviewPageRenderer
            page={activePage}
            pagePlan={activePagePlan}
            capabilities={capabilities}
            blocks={pageBlocks[activePage.id] ?? []}
            selectedRow={effectiveSelectedRow}
            onSelectRow={selectRow}
            routeParameters={routeParameters}
            onNavigate={navigate}
            flows={flows}
            bindings={bindings}
            config={config}
          />
        )}
      </ProductShell>

      <section className="rounded-panel border border-line bg-panel p-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-extrabold">요청·응답 확인</h2>
          {apiLog.length > 0 && (
            <button type="button" className="text-xs font-bold text-brand-strong" onClick={() => setApiLog([])}>
              기록 지우기
            </button>
          )}
        </div>
        <ApiCallLog entries={apiLog} />
      </section>
    </div>
  );
}
