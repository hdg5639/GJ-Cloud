"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { ContainerInfo, ImageInfo, NetworkInfo, ComposeStackInfo } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

type Tab = "containers" | "images" | "networks" | "compose";

const STATE_STYLE: Record<string, string> = {
  running: "bg-[#03C75A]/10 text-[#03C75A]",
  exited: "bg-gray-100 text-gray-600",
  paused: "bg-amber-100 text-amber-700",
  restarting: "bg-amber-100 text-amber-700",
  dead: "bg-red-100 text-red-700",
  created: "bg-gray-100 text-gray-600",
};

type DeleteTarget =
  | { type: "container"; id: string; label: string }
  | { type: "image"; id: string; label: string }
  | { type: "network"; id: string; label: string };

export default function DockerManagementPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [checking, setChecking] = useState(true);
  const [installed, setInstalled] = useState(false);
  const [installing, setInstalling] = useState(false);

  const [tab, setTab] = useState<Tab>("containers");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [containers, setContainers] = useState<ContainerInfo[]>([]);
  const [images, setImages] = useState<ImageInfo[]>([]);
  const [networks, setNetworks] = useState<NetworkInfo[]>([]);
  const [composeStacks, setComposeStacks] = useState<ComposeStackInfo[]>([]);

  const [busyId, setBusyId] = useState<string | null>(null);
  const [logsTarget, setLogsTarget] = useState<ContainerInfo | null>(null);
  const [logsContent, setLogsContent] = useState("");
  const [logsLoading, setLogsLoading] = useState(false);
  const [logsTail, setLogsTail] = useState(200);

  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [showCreateNetwork, setShowCreateNetwork] = useState(false);
  const [networkForm, setNetworkForm] = useState({ name: "", driver: "bridge" });
  const [networkCreating, setNetworkCreating] = useState(false);

  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 2500);
    return () => clearTimeout(t);
  }, [notice]);

  const checkStatus = useCallback(async () => {
    if (!accessToken) return;
    setChecking(true);
    try {
      const status = await api.ops.docker.status(accessToken, vmId);
      setInstalled(status.installed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Docker 상태 확인에 실패했습니다.");
    } finally {
      setChecking(false);
    }
  }, [accessToken, vmId]);

  useEffect(() => {
    checkStatus();
  }, [checkStatus]);

  const loadTab = useCallback(
    async (target: Tab) => {
      if (!accessToken || !installed) return;
      setLoading(true);
      setError(null);
      try {
        if (target === "containers") setContainers(await api.ops.docker.listContainers(accessToken, vmId));
        else if (target === "images") setImages(await api.ops.docker.listImages(accessToken, vmId));
        else if (target === "networks") setNetworks(await api.ops.docker.listNetworks(accessToken, vmId));
        else setComposeStacks(await api.ops.docker.listComposeStacks(accessToken, vmId));
      } catch (err) {
        setError(err instanceof Error ? err.message : "목록 조회에 실패했습니다.");
      } finally {
        setLoading(false);
      }
    },
    [accessToken, vmId, installed]
  );

  useEffect(() => {
    loadTab(tab);
  }, [tab, loadTab]);

  async function handleInstall() {
    if (!accessToken) return;
    setInstalling(true);
    setError(null);
    try {
      await api.ops.docker.install(accessToken, vmId);
      setNotice("Docker를 설치했습니다.");
      await checkStatus();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Docker 설치에 실패했습니다.");
    } finally {
      setInstalling(false);
    }
  }

  async function handleContainerAction(container: ContainerInfo, action: "start" | "stop" | "restart") {
    if (!accessToken) return;
    setBusyId(container.ID);
    setError(null);
    try {
      if (action === "start") await api.ops.docker.startContainer(accessToken, vmId, container.ID);
      else if (action === "stop") await api.ops.docker.stopContainer(accessToken, vmId, container.ID);
      else await api.ops.docker.restartContainer(accessToken, vmId, container.ID);
      setNotice(action === "start" ? "시작했습니다." : action === "stop" ? "정지했습니다." : "재시작했습니다.");
      await loadTab("containers");
    } catch (err) {
      setError(err instanceof Error ? err.message : "컨테이너 제어에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  async function openLogs(container: ContainerInfo) {
    setLogsTarget(container);
    await fetchLogs(container, logsTail);
  }

  async function fetchLogs(container: ContainerInfo, tail: number) {
    if (!accessToken) return;
    setLogsLoading(true);
    try {
      const res = await api.ops.docker.containerLogs(accessToken, vmId, container.ID, tail);
      setLogsContent(res.logs);
    } catch (err) {
      setLogsContent("");
      setError(err instanceof Error ? err.message : "로그 조회에 실패했습니다.");
    } finally {
      setLogsLoading(false);
    }
  }

  async function handleDelete() {
    if (!accessToken || !deleteTarget) return;
    setBusyId(deleteTarget.id);
    setError(null);
    try {
      if (deleteTarget.type === "container") await api.ops.docker.removeContainer(accessToken, vmId, deleteTarget.id);
      else if (deleteTarget.type === "image") await api.ops.docker.removeImage(accessToken, vmId, deleteTarget.id);
      else await api.ops.docker.removeNetwork(accessToken, vmId, deleteTarget.id);
      setNotice("삭제했습니다.");
      setDeleteTarget(null);
      await loadTab(tab);
    } catch (err) {
      setError(err instanceof Error ? err.message : "삭제에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleCreateNetwork(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !networkForm.name.trim()) return;
    setNetworkCreating(true);
    setError(null);
    try {
      await api.ops.docker.createNetwork(accessToken, vmId, networkForm.name.trim(), networkForm.driver || undefined);
      setNotice("네트워크를 생성했습니다.");
      setShowCreateNetwork(false);
      setNetworkForm({ name: "", driver: "bridge" });
      await loadTab("networks");
    } catch (err) {
      setError(err instanceof Error ? err.message : "네트워크 생성에 실패했습니다.");
    } finally {
      setNetworkCreating(false);
    }
  }

  if (!accessToken || checking) return <PageLoader />;

  const TABS: { key: Tab; label: string }[] = [
    { key: "containers", label: "컨테이너" },
    { key: "images", label: "이미지" },
    { key: "networks", label: "네트워크" },
    { key: "compose", label: "Compose 스택" },
  ];

  return (
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <button onClick={() => router.back()} className="p-2 hover:bg-gray-100 rounded-lg transition-colors shrink-0" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-medium text-gray-900 shrink-0">Docker 관리</h1>
          {installed && (
            <div className="flex items-center gap-1">
              {TABS.map((t) => (
                <button
                  key={t.key}
                  onClick={() => setTab(t.key)}
                  className={`text-xs px-3 h-7 rounded-md transition-colors ${
                    tab === t.key ? "bg-gray-900 text-white" : "bg-white border border-gray-200 text-gray-500 hover:border-gray-400"
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>
          )}
        </div>
        {installed && (
          <div className="flex items-center gap-2 shrink-0">
            {tab === "networks" && (
              <button onClick={() => setShowCreateNetwork(true)} className="text-sm px-3.5 h-8 bg-[#03C75A] text-white rounded-md hover:opacity-90">
                + 네트워크 생성
              </button>
            )}
            <button onClick={() => loadTab(tab)} title="새로고침" className="h-8 w-8 flex items-center justify-center border border-gray-300 rounded-md hover:bg-gray-50">
              <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>
        )}
      </div>

      {notice && <div className="bg-[#03C75A]/10 text-[#03C75A] px-4 py-2 rounded-md mb-3 text-sm">{notice}</div>}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600">✕</button>
        </div>
      )}

      {!installed ? (
        <div className="flex-1 border border-gray-200 rounded-lg flex flex-col items-center justify-center gap-3">
          <p className="text-sm text-gray-600">이 VM에는 아직 Docker가 설치되어 있지 않습니다.</p>
          <button
            onClick={handleInstall}
            disabled={installing}
            className="text-sm px-4 h-9 bg-[#03C75A] text-white rounded-md disabled:opacity-60"
          >
            {installing ? "설치 중... (수 분 소요될 수 있어요)" : "Docker 설치"}
          </button>
        </div>
      ) : (
        <div className="flex-1 border border-gray-200 rounded-lg overflow-auto">
          {loading ? (
            <PageLoader label="불러오는 중" />
          ) : tab === "containers" ? (
            containers.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-16">실행 중인 컨테이너가 없습니다</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
                  <tr>
                    <th className="px-4 py-2 font-medium">이름</th>
                    <th className="px-4 py-2 font-medium">이미지</th>
                    <th className="px-4 py-2 font-medium">상태</th>
                    <th className="px-4 py-2 font-medium">포트</th>
                    <th className="px-4 py-2 font-medium w-40">작업</th>
                  </tr>
                </thead>
                <tbody>
                  {containers.map((c) => {
                    const state = c.State?.toLowerCase() ?? "";
                    const isRunning = state === "running";
                    const isBusy = busyId === c.ID;
                    return (
                      <tr key={c.ID} className="border-b border-gray-100 hover:bg-gray-50">
                        <td className="px-4 py-2 text-gray-800">{c.Names}</td>
                        <td className="px-4 py-2 text-gray-500 font-mono text-xs">{c.Image}</td>
                        <td className="px-4 py-2">
                          <span className={`text-xs font-medium px-2 py-0.5 rounded-md ${STATE_STYLE[state] ?? "bg-gray-100 text-gray-600"}`}>
                            {c.Status}
                          </span>
                        </td>
                        <td className="px-4 py-2 text-gray-500 text-xs">{c.Ports || "—"}</td>
                        <td className="px-4 py-2">
                          <div className="flex items-center gap-2 text-xs">
                            {isRunning ? (
                              <>
                                <button disabled={isBusy} onClick={() => handleContainerAction(c, "restart")} className="text-gray-400 hover:text-gray-700 disabled:opacity-40">재시작</button>
                                <button disabled={isBusy} onClick={() => handleContainerAction(c, "stop")} className="text-gray-400 hover:text-amber-600 disabled:opacity-40">정지</button>
                              </>
                            ) : (
                              <button disabled={isBusy} onClick={() => handleContainerAction(c, "start")} className="text-gray-400 hover:text-[#03C75A] disabled:opacity-40">시작</button>
                            )}
                            <button onClick={() => openLogs(c)} className="text-gray-400 hover:text-gray-700">로그</button>
                            <button disabled={isBusy} onClick={() => setDeleteTarget({ type: "container", id: c.ID, label: c.Names })} className="text-gray-400 hover:text-red-600 disabled:opacity-40">삭제</button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )
          ) : tab === "images" ? (
            images.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-16">이미지가 없습니다</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
                  <tr>
                    <th className="px-4 py-2 font-medium">저장소</th>
                    <th className="px-4 py-2 font-medium">태그</th>
                    <th className="px-4 py-2 font-medium">크기</th>
                    <th className="px-4 py-2 font-medium">생성일</th>
                    <th className="px-4 py-2 font-medium w-20">작업</th>
                  </tr>
                </thead>
                <tbody>
                  {images.map((img) => (
                    <tr key={img.ID} className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="px-4 py-2 text-gray-800 font-mono text-xs">{img.Repository}</td>
                      <td className="px-4 py-2 text-gray-500 font-mono text-xs">{img.Tag}</td>
                      <td className="px-4 py-2 text-gray-500">{img.Size}</td>
                      <td className="px-4 py-2 text-gray-500 text-xs">{img.CreatedAt}</td>
                      <td className="px-4 py-2">
                        <button
                          disabled={busyId === img.ID}
                          onClick={() => setDeleteTarget({ type: "image", id: img.ID, label: `${img.Repository}:${img.Tag}` })}
                          className="text-xs text-gray-400 hover:text-red-600 disabled:opacity-40"
                        >
                          삭제
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          ) : tab === "networks" ? (
            networks.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-16">네트워크가 없습니다</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
                  <tr>
                    <th className="px-4 py-2 font-medium">이름</th>
                    <th className="px-4 py-2 font-medium">드라이버</th>
                    <th className="px-4 py-2 font-medium">스코프</th>
                    <th className="px-4 py-2 font-medium w-20">작업</th>
                  </tr>
                </thead>
                <tbody>
                  {networks.map((net) => (
                    <tr key={net.ID} className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="px-4 py-2 text-gray-800">{net.Name}</td>
                      <td className="px-4 py-2 text-gray-500">{net.Driver}</td>
                      <td className="px-4 py-2 text-gray-500">{net.Scope}</td>
                      <td className="px-4 py-2">
                        <button
                          disabled={busyId === net.ID || ["bridge", "host", "none"].includes(net.Name)}
                          onClick={() => setDeleteTarget({ type: "network", id: net.ID, label: net.Name })}
                          className="text-xs text-gray-400 hover:text-red-600 disabled:opacity-40"
                        >
                          삭제
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          ) : composeStacks.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-16">배포된 Compose 스택이 없습니다</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
                <tr>
                  <th className="px-4 py-2 font-medium">이름</th>
                  <th className="px-4 py-2 font-medium">상태</th>
                  <th className="px-4 py-2 font-medium">설정 파일</th>
                </tr>
              </thead>
              <tbody>
                {composeStacks.map((stack) => (
                  <tr key={stack.Name} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-4 py-2 text-gray-800">{stack.Name}</td>
                    <td className="px-4 py-2 text-gray-500">{stack.Status}</td>
                    <td className="px-4 py-2 text-gray-500 font-mono text-xs truncate max-w-xs">{stack.ConfigFiles}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* 네트워크 생성 모달 */}
      {showCreateNetwork && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[380px]">
            <h2 className="text-base font-medium text-gray-900 mb-4">네트워크 생성</h2>
            <form onSubmit={handleCreateNetwork} className="flex flex-col gap-3">
              <div>
                <label htmlFor="network-name" className="text-xs text-gray-500 block mb-1">이름</label>
                <input
                  id="network-name"
                  name="network-name"
                  autoFocus
                  value={networkForm.name}
                  onChange={(e) => setNetworkForm((f) => ({ ...f, name: e.target.value }))}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                  required
                />
              </div>
              <div>
                <label htmlFor="network-driver" className="text-xs text-gray-500 block mb-1">드라이버</label>
                <select
                  id="network-driver"
                  name="network-driver"
                  value={networkForm.driver}
                  onChange={(e) => setNetworkForm((f) => ({ ...f, driver: e.target.value }))}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                >
                  <option value="bridge">bridge</option>
                  <option value="overlay">overlay</option>
                  <option value="host">host</option>
                  <option value="none">none</option>
                </select>
              </div>
              <div className="flex gap-2 mt-1">
                <button type="button" onClick={() => setShowCreateNetwork(false)} className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600">
                  취소
                </button>
                <button type="submit" disabled={networkCreating || !networkForm.name.trim()} className="flex-1 h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60">
                  {networkCreating ? "생성 중..." : "생성"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[340px]">
            <h2 className="text-base font-medium text-gray-900 mb-2">
              {deleteTarget.type === "container" ? "컨테이너 삭제" : deleteTarget.type === "image" ? "이미지 삭제" : "네트워크 삭제"}
            </h2>
            <p className="text-sm text-gray-500 mb-5">
              <span className="font-medium text-gray-800">{deleteTarget.label}</span>을(를) 삭제하면 복구할 수 없습니다. 계속하시겠습니까?
            </p>
            <div className="flex gap-2">
              <button onClick={() => setDeleteTarget(null)} className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600">
                취소
              </button>
              <button onClick={handleDelete} disabled={busyId === deleteTarget.id} className="flex-1 h-9 bg-red-500 text-white rounded-md text-sm disabled:opacity-60">
                삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 로그 모달 */}
      {logsTarget && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-6">
          <div className="bg-white rounded-xl p-6 w-full max-w-3xl h-[70vh] flex flex-col">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-base font-medium text-gray-900 truncate">{logsTarget.Names} 로그</h2>
              <div className="flex items-center gap-2">
                <select
                  value={logsTail}
                  onChange={(e) => {
                    const v = Number(e.target.value);
                    setLogsTail(v);
                    fetchLogs(logsTarget, v);
                  }}
                  className="text-xs h-7 px-2 border border-gray-300 rounded-md"
                >
                  <option value={200}>최근 200줄</option>
                  <option value={1000}>최근 1000줄</option>
                  <option value={5000}>최근 5000줄</option>
                </select>
                <button onClick={() => fetchLogs(logsTarget, logsTail)} className="text-xs text-gray-500 hover:text-gray-700">새로고침</button>
                <button onClick={() => setLogsTarget(null)} className="text-gray-400 hover:text-gray-700">✕</button>
              </div>
            </div>
            <div className="flex-1 bg-gray-900 rounded-md p-3 overflow-auto">
              {logsLoading ? (
                <PageLoader label="불러오는 중" />
              ) : (
                <pre className="text-[11px] text-gray-100 whitespace-pre-wrap break-all font-mono">{logsContent || "로그가 없습니다."}</pre>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
