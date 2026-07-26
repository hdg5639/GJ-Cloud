"use client";

import { useEffect, useRef, useState } from "react";
import { PreviewPageRenderer } from "@/components/preview-runtime/PreviewPageRenderer";
import { compileBlocks, resolveBlocks } from "@/components/preview-runtime/blueprint";
import type { PreviewCapability, PreviewPage, PreviewRuntimeConfig } from "@/components/preview-runtime/types";

// Auto Preview Phase C 검증용 페이지 — 손으로 작성한 샘플 Blueprint(capabilities/pages)를 실제 API
// 없이(fetch를 목데이터로 대체) 렌더러가 올바르게 조립하는지 확인한다. 프로덕션 기능이 아니며
// 사이드바 등 어디에서도 링크되지 않는다. Phase D/E에서 실제 분석 결과 + 배포로 대체될 예정.
//
// Direction Recovery Change Request §13.1 — 실제 마법사(app/(dashboard)/instances/[id]/preview)는
// 이제 POST /ops/preview/blocks가 계산한 Block을 그대로 쓰지만, 이 페이지는 백엔드가 없는 순수
// 프론트 샌드박스라 그 엔드포인트를 호출할 수 없다. 예외적으로 blueprint.ts의 resolveBlocks/
// compileBlocks를 여기서 직접 호출해 Block을 만든다.

const CAPABILITIES: PreviewCapability[] = [
  {
    id: "auth.login", resourceName: "auth", type: "LOGIN", operationId: "login",
    path: "/auth/login", method: "POST", hasSearch: false, hasSort: false, hasPagination: false,
    confidence: "HIGH", evidence: [], fields: ["email", "password"], accessTokenPath: "data.accessToken", searchParam: null,
    risk: "SAFE", automationPolicy: "AUTO_SAFE", collectionPath: null, totalCountPath: null,
    kind: "AUTH", action: null, dependencies: [],
  },
  {
    id: "vms.list", resourceName: "vms", type: "LIST", operationId: "listVms",
    path: "/vms", method: "GET", hasSearch: true, hasSort: false, hasPagination: false,
    confidence: "HIGH", evidence: [], fields: [], accessTokenPath: null, searchParam: "search",
    risk: "SAFE", automationPolicy: "AUTO_SAFE", collectionPath: null, totalCountPath: null,
    kind: "QUERY", action: null, dependencies: [],
  },
  {
    id: "vms.detail", resourceName: "vms", type: "DETAIL", operationId: "getVm",
    path: "/vms/{id}", method: "GET", hasSearch: false, hasSort: false, hasPagination: false,
    confidence: "HIGH", evidence: [], fields: [], accessTokenPath: null, searchParam: null,
    risk: "SAFE", automationPolicy: "AUTO_SAFE", collectionPath: null, totalCountPath: null,
    kind: "QUERY", action: null, dependencies: [],
  },
  {
    id: "vms.create", resourceName: "vms", type: "CREATE", operationId: "createVm",
    path: "/vms", method: "POST", hasSearch: false, hasSort: false, hasPagination: false,
    confidence: "HIGH", evidence: [], fields: ["name", "planType"], accessTokenPath: null, searchParam: null,
    risk: "STATE_CHANGING", automationPolicy: "USER_INITIATED", collectionPath: null, totalCountPath: null,
    kind: "MUTATION", action: null, dependencies: [],
  },
  {
    id: "vms.delete", resourceName: "vms", type: "DELETE", operationId: "deleteVm",
    path: "/vms/{id}", method: "DELETE", hasSearch: false, hasSort: false, hasPagination: false,
    confidence: "HIGH", evidence: [], fields: [], accessTokenPath: null, searchParam: null,
    risk: "DESTRUCTIVE", automationPolicy: "EXPLICIT_CONFIRMATION", collectionPath: null, totalCountPath: null,
    kind: "MUTATION", action: null, dependencies: [],
  },
];

const PAGES: PreviewPage[] = [
  { id: "auth-login", title: "로그인", skeleton: "AUTH_PAGE", capabilityIds: ["auth.login"] },
  { id: "vms-page", title: "Vms", skeleton: "LIST_DETAIL", capabilityIds: ["vms.list", "vms.detail", "vms.create", "vms.delete"] },
];

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function installMockFetch(): () => void {
  const original = window.fetch;
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = init?.method ?? "GET";

    if (url.includes("/auth/login")) {
      return jsonResponse({ accessToken: "mock-token-123" });
    }
    if (url.includes("/vms/") && method === "GET") {
      const id = url.split("/vms/")[1];
      return jsonResponse({ id, name: `VM ${id}`, planType: "FREE", status: "RUNNING" });
    }
    if (url.includes("/vms/") && method === "DELETE") {
      return new Response(null, { status: 204 });
    }
    if (url.endsWith("/vms") && method === "GET") {
      return jsonResponse([
        { id: "1", name: "web-server", planType: "FREE", status: "RUNNING" },
        { id: "2", name: "db-server", planType: "PRO", status: "STOPPED" },
      ]);
    }
    if (url.endsWith("/vms") && method === "POST") {
      return jsonResponse({ id: "3", ...JSON.parse((init?.body as string) ?? "{}") }, 201);
    }
    return original(input, init);
  };
  return () => {
    window.fetch = original;
  };
}

export default function PreviewDemoPage() {
  const [authToken, setAuthToken] = useState<string | null>(null);
  const [activePage, setActivePage] = useState(PAGES[0]);
  const installed = useRef(false);

  useEffect(() => {
    if (installed.current) return;
    installed.current = true;
    return installMockFetch();
  }, []);

  const config: PreviewRuntimeConfig = {
    apiBaseUrl: "https://mock.example.com",
    authToken,
    onAuthTokenChange: setAuthToken,
    authStrategy: { type: "BEARER", headerName: "Authorization", prefix: "Bearer ", queryParamName: null },
    purpose: "PRODUCT_LIKE",
  };
  const blocks = compileBlocks(resolveBlocks(activePage, CAPABILITIES), config.purpose);

  return (
    <div className="mx-auto max-w-4xl p-8">
      <h1 className="mb-1 text-lg font-bold">Auto Preview Phase C 검증 페이지</h1>
      <p className="mb-6 text-sm text-muted">목데이터로 fetch를 대체한 렌더러 확인용 — 프로덕션 라우트 아님.</p>

      <div className="mb-4 flex gap-2">
        {PAGES.map((page) => (
          <button
            key={page.id}
            type="button"
            onClick={() => setActivePage(page)}
            className={`rounded-md border px-3 py-1.5 text-xs font-bold ${
              activePage.id === page.id ? "border-brand bg-soft text-brand-strong" : "border-line-strong text-muted"
            }`}
          >
            {page.title}
          </button>
        ))}
      </div>

      {authToken && <p className="mb-3 text-xs text-brand-strong">로그인됨 (token: {authToken})</p>}

      <div className="rounded-panel border border-line bg-panel p-5">
        <PreviewPageRenderer page={activePage} capabilities={CAPABILITIES} blocks={blocks} config={config} />
      </div>
    </div>
  );
}
