"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { PageLoader } from "@/components/ui/loader";

export default function GithubCallbackClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { accessToken } = useAuth();
  const started = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const code = searchParams.get("code");
  const state = searchParams.get("state");
  const requestError = !code || !state
    ? "GitHub 연결 정보가 올바르지 않습니다."
    : null;

  useEffect(() => {
    if (!accessToken || started.current || requestError || !code || !state) return;
    started.current = true;

    api.ops.github.completeInstallation(accessToken, code, state)
      .then((result) => {
        localStorage.setItem("gamjabox:github-connected", JSON.stringify({
          vmId: result.vmId,
          completedAt: Date.now(),
        }));
        if (window.opener && !window.opener.closed) {
          window.opener.postMessage(
            { type: "gamjabox:github-connected", vmId: result.vmId },
            window.location.origin
          );
        }
        if (window.name === "gamjabox-github-connect") {
          window.close();
          return;
        }
        router.replace(`/instances/${result.vmId}/deployments?github=connected`);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "GitHub App 연결을 완료하지 못했습니다.");
      });
  }, [accessToken, code, requestError, router, state]);

  const visibleError = requestError ?? error;
  if (visibleError) {
    return (
      <div className="mx-auto mt-20 max-w-lg rounded-panel border border-danger-soft bg-danger/10 p-6">
        <h1 className="text-lg font-bold text-danger">GitHub 연결 실패</h1>
        <p className="mt-2 text-sm text-muted">{visibleError}</p>
        <button
          type="button"
          onClick={() => router.back()}
          className="mt-5 rounded-md bg-brand px-4 py-2 text-sm font-bold text-white"
        >
          이전 화면으로
        </button>
      </div>
    );
  }

  return <PageLoader label="GitHub 저장소 연결 중" />;
}
