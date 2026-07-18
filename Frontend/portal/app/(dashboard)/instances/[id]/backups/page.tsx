"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DbBackupResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

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
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <button onClick={() => router.back()} className="p-2 hover:bg-gray-100 rounded-lg transition-colors shrink-0" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-medium text-gray-900 shrink-0">DB 백업</h1>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button onClick={load} title="새로고침" className="h-8 w-8 flex items-center justify-center border border-gray-300 rounded-md hover:bg-gray-50">
            <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
          <button onClick={() => setShowForm(true)} className="text-sm px-3.5 h-8 bg-[#03C75A] text-white rounded-md hover:opacity-90">
            + 백업 실행
          </button>
        </div>
      </div>

      {notice && <div className="bg-[#03C75A]/10 text-[#03C75A] px-4 py-2 rounded-md mb-3 text-sm">{notice}</div>}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600">✕</button>
        </div>
      )}

      <div className="flex-1 border border-gray-200 rounded-lg overflow-auto">
        {loading ? (
          <PageLoader label="불러오는 중" />
        ) : backups.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-16">백업 이력이 없습니다</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
              <tr>
                <th className="px-4 py-2 font-medium">생성일시</th>
                <th className="px-4 py-2 font-medium">서비스</th>
                <th className="px-4 py-2 font-medium">DB 종류</th>
                <th className="px-4 py-2 font-medium">크기</th>
                <th className="px-4 py-2 font-medium">상태</th>
                <th className="px-4 py-2 font-medium w-20">작업</th>
              </tr>
            </thead>
            <tbody>
              {backups.map((b) => (
                <tr key={b.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2 text-gray-500 text-xs">{formatDateTime(b.createdAt)}</td>
                  <td className="px-4 py-2 text-gray-800">{b.serviceName}</td>
                  <td className="px-4 py-2 text-gray-500">{b.dbType}</td>
                  <td className="px-4 py-2 text-gray-500">{formatSize(b.fileSizeBytes)}</td>
                  <td className="px-4 py-2">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-md ${b.succeeded ? "bg-[#03C75A]/10 text-[#03C75A]" : "bg-red-100 text-red-700"}`}>
                      {b.succeeded ? "성공" : "실패"}
                    </span>
                    {!b.succeeded && b.errorMessage && (
                      <span className="text-[11px] text-red-500 ml-2">{b.errorMessage}</span>
                    )}
                  </td>
                  <td className="px-4 py-2">
                    {b.succeeded && b.filePath && (
                      <button onClick={() => handleDownload(b)} className="text-xs text-[#03C75A] hover:underline">
                        다운로드
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 백업 실행 모달 */}
      {showForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[400px]">
            <h2 className="text-base font-medium text-gray-900 mb-4">DB 백업 실행</h2>
            <form onSubmit={handleTrigger} className="flex flex-col gap-3">
              <div>
                <label htmlFor="backup-service-name" className="text-xs text-gray-500 block mb-1">서비스명 (compose service name)</label>
                <input
                  id="backup-service-name"
                  name="backup-service-name"
                  autoFocus
                  value={form.serviceName}
                  onChange={(e) => setForm((f) => ({ ...f, serviceName: e.target.value }))}
                  placeholder="예: db"
                  required
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                />
              </div>
              <div>
                <label htmlFor="backup-db-type" className="text-xs text-gray-500 block mb-1">DB 종류</label>
                <select
                  id="backup-db-type"
                  name="backup-db-type"
                  value={form.dbType}
                  onChange={(e) => setForm((f) => ({ ...f, dbType: e.target.value }))}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                >
                  {DB_TYPES.map((t) => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="backup-database" className="text-xs text-gray-500 block mb-1">데이터베이스명</label>
                <input
                  id="backup-database"
                  name="backup-database"
                  value={form.database}
                  onChange={(e) => setForm((f) => ({ ...f, database: e.target.value }))}
                  required
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                />
              </div>
              {NEEDS_CREDENTIALS.has(form.dbType) && (
                <>
                  <div>
                    <label htmlFor="backup-username" className="text-xs text-gray-500 block mb-1">사용자명</label>
                    <input
                      id="backup-username"
                      name="backup-username"
                      value={form.username}
                      onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
                      className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                    />
                  </div>
                  <div>
                    <label htmlFor="backup-password" className="text-xs text-gray-500 block mb-1">비밀번호</label>
                    <input
                      id="backup-password"
                      name="backup-password"
                      type="password"
                      value={form.password}
                      onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                      className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                    />
                  </div>
                </>
              )}
              <div className="flex gap-2 mt-1">
                <button type="button" onClick={() => setShowForm(false)} className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600">
                  취소
                </button>
                <button type="submit" disabled={triggering || !form.serviceName || !form.database} className="flex-1 h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60">
                  {triggering ? "백업 중..." : "백업 실행"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
