"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DbBackupResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Button } from "@/components/ui/button";
import { Table, Th, Td } from "@/components/ui/table";
import { Modal } from "@/components/ui/modal";
import { Field, Input, Select } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/badge";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function formatSize(bytes: number | null): string {
  if (bytes === null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

const DB_TYPES = [
  { value: "postgresql", label: "PostgreSQL" },
  { value: "mysql", label: "MySQL" },
  { value: "redis", label: "Redis" },
  { value: "mongodb", label: "MongoDB" },
];

const NEEDS_CREDENTIALS = new Set(["postgresql", "mysql"]);

export default function DbBackupsPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [backups, setBackups] = useState<DbBackupResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ serviceName: "", dbType: "postgresql", database: "", username: "", password: "" });
  const [triggering, setTriggering] = useState(false);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      setBackups(await api.ops.backups.list(accessToken, vmId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "백업 이력 조회에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [accessToken, vmId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 2500);
    return () => clearTimeout(t);
  }, [notice]);

  async function handleTrigger(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !form.serviceName || !form.database) return;
    setTriggering(true);
    setError(null);
    try {
      const result = await api.ops.backups.trigger(accessToken, vmId, {
        serviceName: form.serviceName,
        dbType: form.dbType,
        database: form.database,
        username: form.username || undefined,
        password: form.password || undefined,
      });
      setBackups((prev) => [result, ...prev]);
      setNotice(result.succeeded ? "백업을 완료했습니다." : "백업이 실패했습니다.");
      setShowForm(false);
      setForm({ serviceName: "", dbType: "postgresql", database: "", username: "", password: "" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "백업 실행에 실패했습니다.");
    } finally {
      setTriggering(false);
    }
  }

  async function handleDownload(backup: DbBackupResponse) {
    if (!accessToken || !backup.filePath) return;
    try {
      const blob = await api.ops.downloadFile(accessToken, vmId, backup.filePath);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = backup.filePath.split("/").pop() ?? "backup.dump";
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "다운로드에 실패했습니다.");
    }
  }

  if (!accessToken) return <PageLoader />;

  return (
    <div className="flex flex-col h-[calc(100vh-170px)]">
      <InstanceSectionNav vmId={vmId} />
      <div className="mb-3 flex items-center rounded-panel border border-line bg-panel">
        <div className="flex h-10 shrink-0 items-center gap-2.5 pl-4 pr-3.5">
          <button onClick={() => router.back()} className="flex h-7 w-7 items-center justify-center rounded-md text-muted-soft transition-colors hover:bg-white/[0.06] hover:text-muted" aria-label="뒤로가기">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-[15px] font-bold whitespace-nowrap">DB 백업</h1>
        </div>
        <div className="ml-auto flex h-10 shrink-0 items-center">
          <button onClick={() => setShowForm(true)} className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm font-bold text-brand-strong transition-colors hover:bg-white/[0.06]">
            ＋ 백업 실행
          </button>
          <div className="h-5 w-px shrink-0 bg-line" />
          <button onClick={load} title="새로고침" className="flex h-10 w-10 shrink-0 items-center justify-center text-muted transition-colors hover:bg-white/[0.06] rounded-r-panel">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {notice && <div className="bg-soft text-brand-strong px-4 py-2 rounded-md mb-3 text-sm">{notice}</div>}
      {error && (
        <div className="bg-danger/10 border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-danger/60 hover:text-danger">✕</button>
        </div>
      )}

      <div className="flex-1 rounded-panel border border-line overflow-auto">
        {loading ? (
          <PageLoader label="불러오는 중" />
        ) : backups.length === 0 ? (
          <p className="text-sm text-muted-soft text-center py-16">백업 이력이 없습니다</p>
        ) : (
          <Table>
            <thead className="sticky top-0">
              <tr>
                <Th>생성일시</Th>
                <Th>서비스</Th>
                <Th>DB 종류</Th>
                <Th>크기</Th>
                <Th>상태</Th>
                <Th className="w-20">작업</Th>
              </tr>
            </thead>
            <tbody>
              {backups.map((b) => (
                <tr key={b.id} className="hover:bg-white/[0.03]">
                  <Td className="text-muted-soft text-xs">{formatDateTime(b.createdAt)}</Td>
                  <Td className="text-foreground">{b.serviceName}</Td>
                  <Td className="text-muted">{b.dbType}</Td>
                  <Td className="text-muted">{formatSize(b.fileSizeBytes)}</Td>
                  <Td>
                    <StatusBadge tone={b.succeeded ? "ok" : "off"} className={!b.succeeded ? "bg-danger/10 text-danger" : undefined}>
                      {b.succeeded ? "성공" : "실패"}
                    </StatusBadge>
                    {!b.succeeded && b.errorMessage && (
                      <span className="text-[11px] text-danger ml-2">{b.errorMessage}</span>
                    )}
                  </Td>
                  <Td>
                    {b.succeeded && b.filePath && (
                      <button onClick={() => handleDownload(b)} className="text-xs text-brand-strong hover:underline font-bold">
                        다운로드
                      </button>
                    )}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </div>

      {/* 백업 실행 모달 */}
      <Modal open={showForm} onClose={() => setShowForm(false)}>
        <div className="mx-auto w-[400px] rounded-panel bg-panel p-6">
          <h2 className="text-base font-bold mb-4">DB 백업 실행</h2>
          <form onSubmit={handleTrigger} className="flex flex-col gap-1">
            <Field label="서비스명 (compose service name)" htmlFor="backup-service-name">
              <Input
                id="backup-service-name"
                name="backup-service-name"
                autoFocus
                value={form.serviceName}
                onChange={(e) => setForm((f) => ({ ...f, serviceName: e.target.value }))}
                placeholder="예: db"
                required
              />
            </Field>
            <Field label="DB 종류" htmlFor="backup-db-type">
              <Select
                id="backup-db-type"
                name="backup-db-type"
                value={form.dbType}
                onChange={(e) => setForm((f) => ({ ...f, dbType: e.target.value }))}
              >
                {DB_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </Select>
            </Field>
            <Field label="데이터베이스명" htmlFor="backup-database">
              <Input
                id="backup-database"
                name="backup-database"
                value={form.database}
                onChange={(e) => setForm((f) => ({ ...f, database: e.target.value }))}
                required
              />
            </Field>
            {NEEDS_CREDENTIALS.has(form.dbType) && (
              <>
                <Field label="사용자명" htmlFor="backup-username">
                  <Input
                    id="backup-username"
                    name="backup-username"
                    value={form.username}
                    onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
                  />
                </Field>
                <Field label="비밀번호" htmlFor="backup-password">
                  <Input
                    id="backup-password"
                    name="backup-password"
                    type="password"
                    value={form.password}
                    onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                  />
                </Field>
              </>
            )}
            <div className="flex gap-2 mt-1">
              <Button type="button" onClick={() => setShowForm(false)} className="flex-1">
                취소
              </Button>
              <Button type="submit" variant="primary" disabled={triggering || !form.serviceName || !form.database} className="flex-1">
                {triggering ? "백업 중..." : "백업 실행"}
              </Button>
            </div>
          </form>
        </div>
      </Modal>
    </div>
  );
}
