"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { FileEntry } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const IMAGE_EXTENSIONS = ["jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico"];
const AUDIO_EXTENSIONS = ["mp3", "wav", "ogg", "m4a", "flac", "aac"];
const VIDEO_EXTENSIONS = ["mp4", "webm", "mov", "mkv", "avi"];

type PreviewKind = "image" | "audio" | "video";

function detectPreviewKind(name: string): PreviewKind | null {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  if (IMAGE_EXTENSIONS.includes(ext)) return "image";
  if (AUDIO_EXTENSIONS.includes(ext)) return "audio";
  if (VIDEO_EXTENSIONS.includes(ext)) return "video";
  return null;
}

export default function FileBrowserPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentPath, setCurrentPath] = useState("");
  const [rootPath, setRootPath] = useState<string | null>(null);
  const [entries, setEntries] = useState<FileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [showNewFolder, setShowNewFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<FileEntry | null>(null);

  const [editingFile, setEditingFile] = useState<FileEntry | null>(null);
  const [editingContent, setEditingContent] = useState("");
  const [editingLoading, setEditingLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [previewFile, setPreviewFile] = useState<FileEntry | null>(null);
  const [previewKind, setPreviewKind] = useState<PreviewKind | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const load = useCallback(
    async (path: string) => {
      if (!accessToken) return;
      setLoading(true);
      setError(null);
      try {
        const res = await api.ops.listFiles(accessToken, vmId, path || undefined);
        setEntries(res.entries);
        setCurrentPath(res.path);
        if (!path) setRootPath((prev) => prev ?? res.path);
      } catch (err) {
        setError(err instanceof Error ? err.message : "디렉토리 조회에 실패했습니다.");
      } finally {
        setLoading(false);
      }
    },
    [accessToken, vmId]
  );

  function goToParent() {
    if (!currentPath || currentPath === rootPath) return;
    const segments = currentPath.split("/").filter(Boolean);
    segments.pop();
    const parent = (currentPath.startsWith("/") ? "/" : "") + segments.join("/");
    load(parent);
  }

  useEffect(() => {
    load("");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, vmId]);

  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 2500);
    return () => clearTimeout(t);
  }, [notice]);

  function openEntry(entry: FileEntry) {
    if (entry.directory) {
      load(entry.path);
      return;
    }
    const kind = detectPreviewKind(entry.name);
    if (kind) {
      openPreview(entry, kind);
      return;
    }
    openEditor(entry);
  }

  async function openPreview(entry: FileEntry, kind: PreviewKind) {
    if (!accessToken) return;
    setPreviewFile(entry);
    setPreviewKind(kind);
    setPreviewLoading(true);
    setError(null);
    try {
      // 티켓 기반 스트리밍 URL — Range 요청(seek/버퍼링)을 지원해서 오디오/비디오도 전체를 미리 안 받고 재생 가능
      const { ticket } = await api.ops.issueStreamTicket(accessToken, vmId, entry.path);
      setPreviewUrl(`${process.env.NEXT_PUBLIC_OPS_API}/ops/${vmId}/files/stream?ticket=${ticket}`);
    } catch (err) {
      setPreviewFile(null);
      setPreviewKind(null);
      setError(err instanceof Error ? err.message : "미리보기를 불러올 수 없습니다.");
    } finally {
      setPreviewLoading(false);
    }
  }

  function closePreview() {
    setPreviewUrl(null);
    setPreviewFile(null);
    setPreviewKind(null);
  }

  async function openEditor(entry: FileEntry) {
    if (!accessToken) return;
    setEditingFile(entry);
    setEditingLoading(true);
    setError(null);
    try {
      const res = await api.ops.readFileContent(accessToken, vmId, entry.path);
      setEditingContent(res.content);
    } catch (err) {
      setEditingFile(null);
      setError(err instanceof Error ? err.message : "파일을 열 수 없습니다. (바이너리 파일이거나 크기 제한을 초과했을 수 있어요 — 다운로드를 이용하세요)");
    } finally {
      setEditingLoading(false);
    }
  }

  async function handleSave() {
    if (!accessToken || !editingFile) return;
    setSaving(true);
    try {
      await api.ops.saveFileContent(accessToken, vmId, editingFile.path, editingContent);
      setNotice("저장했습니다.");
      setEditingFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDownload(entry: FileEntry) {
    if (!accessToken) return;
    try {
      const blob = await api.ops.downloadFile(accessToken, vmId, entry.path);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = entry.name;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "다운로드에 실패했습니다.");
    }
  }

  async function handleUpload(files: FileList | null) {
    if (!accessToken || !files || files.length === 0) return;
    setError(null);
    try {
      for (const file of Array.from(files)) {
        await api.ops.uploadFile(accessToken, vmId, currentPath, file);
      }
      setNotice("업로드했습니다.");
      load(currentPath);
    } catch (err) {
      setError(err instanceof Error ? err.message : "업로드에 실패했습니다.");
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleCreateFolder() {
    if (!accessToken || !newFolderName.trim()) return;
    try {
      await api.ops.createDirectory(accessToken, vmId, currentPath, newFolderName.trim());
      setShowNewFolder(false);
      setNewFolderName("");
      load(currentPath);
    } catch (err) {
      setError(err instanceof Error ? err.message : "폴더 생성에 실패했습니다.");
    }
  }

  async function handleDelete() {
    if (!accessToken || !deleteTarget) return;
    try {
      await api.ops.deleteFile(accessToken, vmId, deleteTarget.path);
      setDeleteTarget(null);
      load(currentPath);
    } catch (err) {
      setError(err instanceof Error ? err.message : "삭제에 실패했습니다.");
    }
  }

  if (!accessToken) return <PageLoader />;

  const segments = currentPath ? currentPath.split("/").filter(Boolean) : [];
  const isAbsolute = currentPath.startsWith("/");

  return (
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <button onClick={() => router.back()} className="p-2 hover:bg-gray-100 rounded-lg transition-colors shrink-0" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-medium text-gray-900 shrink-0">파일 브라우저</h1>
          <div className="flex items-center gap-1 text-xs text-gray-500 overflow-x-auto whitespace-nowrap">
            <button onClick={() => load("")} className="hover:text-gray-800 hover:underline">홈</button>
            {segments.map((seg, i) => (
              <span key={i} className="flex items-center gap-1">
                <span className="text-gray-300">/</span>
                <button
                  onClick={() => load((isAbsolute ? "/" : "") + segments.slice(0, i + 1).join("/"))}
                  className="hover:text-gray-800 hover:underline"
                >
                  {seg}
                </button>
              </span>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button onClick={() => load(currentPath)} title="새로고침" className="h-8 w-8 flex items-center justify-center border border-gray-300 rounded-md hover:bg-gray-50">
            <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
          <button onClick={() => setShowNewFolder(true)} className="text-sm px-3.5 h-8 border border-gray-300 rounded-md hover:bg-gray-50">
            새 폴더
          </button>
          <button onClick={() => fileInputRef.current?.click()} className="text-sm px-3.5 h-8 bg-[#03C75A] text-white rounded-md hover:opacity-90">
            업로드
          </button>
          <input ref={fileInputRef} type="file" multiple hidden onChange={(e) => handleUpload(e.target.files)} />
        </div>
      </div>

      {notice && (
        <div className="bg-[#03C75A]/10 text-[#03C75A] px-4 py-2 rounded-md mb-3 text-sm">{notice}</div>
      )}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600">✕</button>
        </div>
      )}

      <div className="flex-1 border border-gray-200 rounded-lg overflow-auto">
        {loading ? (
          <PageLoader label="불러오는 중" />
        ) : entries.length === 0 && currentPath === rootPath ? (
          <p className="text-sm text-gray-400 text-center py-16">빈 디렉토리입니다</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
              <tr>
                <th className="px-4 py-2 font-medium">이름</th>
                <th className="px-4 py-2 font-medium w-28">크기</th>
                <th className="px-4 py-2 font-medium w-40">수정일</th>
                <th className="px-4 py-2 font-medium w-24">작업</th>
              </tr>
            </thead>
            <tbody>
              {currentPath && currentPath !== rootPath && (
                <tr className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2" colSpan={4}>
                    <button onClick={goToParent} className="flex items-center gap-2 text-left hover:underline">
                      <svg className="w-4 h-4 text-amber-400 shrink-0" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M3 5a2 2 0 012-2h4l2 2h8a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V5z" />
                      </svg>
                      <span className="text-gray-800">..</span>
                    </button>
                  </td>
                </tr>
              )}
              {entries.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-12 text-center text-sm text-gray-400">
                    빈 디렉토리입니다
                  </td>
                </tr>
              )}
              {entries.map((entry) => (
                <tr key={entry.path} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2">
                    <button onClick={() => openEntry(entry)} className="flex items-center gap-2 text-left hover:underline">
                      {entry.directory ? (
                        <svg className="w-4 h-4 text-amber-400 shrink-0" fill="currentColor" viewBox="0 0 24 24">
                          <path d="M3 5a2 2 0 012-2h4l2 2h8a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V5z" />
                        </svg>
                      ) : (
                        <svg className="w-4 h-4 text-gray-400 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M7 3h7l5 5v13a1 1 0 01-1 1H7a1 1 0 01-1-1V4a1 1 0 011-1z" />
                        </svg>
                      )}
                      <span className="text-gray-800">{entry.name}</span>
                    </button>
                  </td>
                  <td className="px-4 py-2 text-gray-500">{entry.directory ? "—" : formatSize(entry.size)}</td>
                  <td className="px-4 py-2 text-gray-500">{formatDate(entry.modifiedAt)}</td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      {!entry.directory && (
                        <button onClick={() => handleDownload(entry)} title="다운로드" className="text-gray-400 hover:text-gray-700">
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v12m0 0l-4-4m4 4l4-4M4 19h16" />
                          </svg>
                        </button>
                      )}
                      <button onClick={() => setDeleteTarget(entry)} title="삭제" className="text-gray-400 hover:text-red-600">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M6 6l1 14a1 1 0 001 1h8a1 1 0 001-1l1-14M4 6h16M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2" />
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 새 폴더 모달 */}
      {showNewFolder && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[340px]">
            <h2 className="text-base font-medium text-gray-900 mb-3">새 폴더</h2>
            <input
              autoFocus
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleCreateFolder()}
              placeholder="폴더 이름"
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm mb-5 focus:outline-none focus:border-[#03C75A]"
            />
            <div className="flex gap-2">
              <button
                onClick={() => { setShowNewFolder(false); setNewFolderName(""); }}
                className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600"
              >
                취소
              </button>
              <button
                onClick={handleCreateFolder}
                disabled={!newFolderName.trim()}
                className="flex-1 h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60"
              >
                생성
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[340px]">
            <h2 className="text-base font-medium text-gray-900 mb-2">
              {deleteTarget.directory ? "폴더 삭제" : "파일 삭제"}
            </h2>
            <p className="text-sm text-gray-500 mb-5">
              <span className="font-medium text-gray-800">{deleteTarget.name}</span>
              {deleteTarget.directory ? "과 하위 항목을" : "을"} 삭제하면 복구할 수 없습니다. 계속하시겠습니까?
            </p>
            <div className="flex gap-2">
              <button onClick={() => setDeleteTarget(null)} className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600">
                취소
              </button>
              <button onClick={handleDelete} className="flex-1 h-9 bg-red-500 text-white rounded-md text-sm">
                삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 편집 모달 */}
      {editingFile && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-6">
          <div className="bg-white rounded-xl p-6 w-full max-w-3xl h-[80vh] flex flex-col">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-base font-medium text-gray-900 truncate">{editingFile.name}</h2>
              <button onClick={() => setEditingFile(null)} className="text-gray-400 hover:text-gray-700">✕</button>
            </div>
            {editingLoading ? (
              <PageLoader label="불러오는 중" />
            ) : (
              <textarea
                value={editingContent}
                onChange={(e) => setEditingContent(e.target.value)}
                className="flex-1 w-full font-mono text-xs border border-gray-300 rounded-md p-3 resize-none focus:outline-none focus:border-[#03C75A]"
                spellCheck={false}
              />
            )}
            <div className="flex gap-2 mt-4">
              <button onClick={() => setEditingFile(null)} className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600">
                취소
              </button>
              <button
                onClick={handleSave}
                disabled={saving || editingLoading}
                className="flex-1 h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60"
              >
                {saving ? "저장 중..." : "저장"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 미리보기 모달 (이미지/오디오/비디오) */}
      {previewFile && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-6">
          <div className="bg-white rounded-xl p-6 w-full max-w-3xl max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-base font-medium text-gray-900 truncate">{previewFile.name}</h2>
              <button onClick={closePreview} className="text-gray-400 hover:text-gray-700">✕</button>
            </div>
            <div className="flex-1 flex items-center justify-center overflow-auto min-h-0">
              {previewLoading || !previewUrl ? (
                <PageLoader label="불러오는 중" />
              ) : previewKind === "image" ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={previewUrl} alt={previewFile.name} className="max-w-full max-h-full object-contain" />
              ) : previewKind === "audio" ? (
                <audio controls src={previewUrl} className="w-full" />
              ) : (
                <video controls src={previewUrl} className="max-w-full max-h-full" />
              )}
            </div>
            <div className="flex gap-2 mt-4">
              <button onClick={closePreview} className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600">
                닫기
              </button>
              <button
                onClick={() => handleDownload(previewFile)}
                className="flex-1 h-9 bg-[#03C75A] text-white rounded-md text-sm"
              >
                다운로드
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
