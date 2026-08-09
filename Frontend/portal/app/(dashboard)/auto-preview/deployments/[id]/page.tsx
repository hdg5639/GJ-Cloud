"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { ManagedPreviewResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Panel } from "@/components/ui/panel";
import { StatusBadge } from "@/components/ui/badge";

const TERMINAL = new Set(["RUNNING", "FAILED", "EXPIRED", "STOPPED"]);

export default function ManagedPreviewDeploymentPage() {
  const { accessToken } = useAuth();
  const id = useParams().id as string;
  const [preview, setPreview] = useState<ManagedPreviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const load = async () => {
      try {
        const data = await api.ops.preview.managedDeployment(accessToken, id);
        if (cancelled) return;
        setPreview(data);
        setError(null);
        if (!TERMINAL.has(data.status)) timer = setTimeout(load, 3000);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "배포 상태를 불러오지 못했습니다.");
      }
    };
    void load();
    return () => { cancelled = true; if (timer) clearTimeout(timer); };
  }, [accessToken, id]);

  if (!accessToken || (!preview && !error)) return <PageLoader label="관리형 Preview 배포 준비 중" />;

  return (
    <div className="mx-auto max-w-[900px]">
      <Link href="/auto-preview" className="mb-5 inline-flex text-xs font-bold text-muted hover:text-foreground">← Auto Preview로 돌아가기</Link>
      <header className="mb-6">
        <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">MANAGED AUTO PREVIEW</span>
        <div className="mt-1 flex flex-wrap items-center gap-3">
          <h1 className="text-[24px] font-extrabold tracking-tight">관리형 Preview 배포</h1>
          {preview && <StatusBadge tone={preview.status === "RUNNING" ? "ok" : "off"}>{preview.status}</StatusBadge>}
        </div>
        <p className="mt-2 text-sm text-muted">GamjaBox가 격리된 실행 환경과 외부 주소, 만료 정리를 자동으로 관리합니다.</p>
      </header>

      {error ? <Panel className="border-danger/30 p-5 text-sm text-danger">{error}</Panel> : preview && (
        <Panel className="p-5 sm:p-6">
          {!TERMINAL.has(preview.status) && (
            <div className="mb-5 h-1.5 overflow-hidden rounded-full bg-white/10"><div className="h-full w-1/2 animate-pulse rounded-full bg-brand" /></div>
          )}
          <dl className="grid gap-5 sm:grid-cols-2">
            <div><dt className="text-[11px] font-bold text-muted-soft">배포 상태</dt><dd className="mt-1 text-sm font-bold">{preview.status === "QUEUED" ? "대기 중" : preview.status === "BUILDING" ? "이미지 빌드·기동 중" : preview.status}</dd></div>
            <div><dt className="text-[11px] font-bold text-muted-soft">자동 정리 시각</dt><dd className="mt-1 text-sm font-bold">{new Date(preview.expiresAt).toLocaleString("ko-KR")}</dd></div>
          </dl>
          {preview.status === "RUNNING" && preview.url && (
            <a href={preview.url} target="_blank" rel="noreferrer" className="mt-6 flex items-center justify-between rounded-xl border border-brand/30 bg-brand/10 px-4 py-4 text-sm font-extrabold text-brand-strong hover:bg-brand/15">
              Preview 열기 <span aria-hidden>↗</span>
            </a>
          )}
          {preview.errorMessage && <p className="mt-5 rounded-xl bg-danger/10 p-4 text-xs leading-5 text-danger">{preview.errorMessage}</p>}
          <p className="mt-5 text-[11px] leading-5 text-muted-soft">보안을 위해 실행 워커의 VMID, 내부 IP, SSH 정보는 제공되지 않습니다.</p>
        </Panel>
      )}
    </div>
  );
}
