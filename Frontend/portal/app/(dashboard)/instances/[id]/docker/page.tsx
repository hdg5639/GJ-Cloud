"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { ContainerInfo, ImageInfo, NetworkInfo, ComposeStackInfo } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Button } from "@/components/ui/button";
import { Table, Th, Td } from "@/components/ui/table";
import { Modal } from "@/components/ui/modal";
import { Field, Input, Select } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/badge";

type Tab = "containers" | "images" | "networks" | "compose";

const STATE_TONE: Record<string, "ok" | "off"> = {
  running: "ok",
  exited: "off",
  paused: "off",
  restarting: "off",
  dead: "off",
  created: "off",
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
      <div className="mb-3 flex items-center rounded-panel border border-line bg-panel">
        <div className="flex h-10 shrink-0 items-center gap-2.5 pl-4 pr-3.5">
          <button onClick={() => router.back()} className="flex h-7 w-7 items-center justify-center rounded-md text-muted-soft transition-colors hover:bg-[#f2f6f3] hover:text-muted" aria-label="뒤로가기">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-[15px] font-bold whitespace-nowrap">Docker 관리</h1>
        </div>

        {installed && (
          <div className="flex h-10 items-center gap-1 px-1">
            {TABS.map((t) => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                className={`text-xs px-3 h-7 rounded-md whitespace-nowrap transition-colors ${
                  tab === t.key ? "bg-[#445248] text-white" : "text-muted hover:bg-[#f2f6f3]"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
        )}

        {installed && (
          <div className="ml-auto flex h-10 shrink-0 items-center">
            {tab === "networks" && (
              <>
                <button
                  onClick={() => setShowCreateNetwork(true)}
                  className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm text-[#445248] transition-colors hover:bg-[#f2f6f3]"
                >
                  ＋ 네트워크 생성
                </button>
                <div className="h-5 w-px shrink-0 bg-line" />
              </>
            )}
            <button
              onClick={() => loadTab(tab)}
              title="새로고침"
              className="flex h-10 w-10 shrink-0 items-center justify-center text-muted transition-colors hover:bg-[#f2f6f3] rounded-r-panel"
            >
              <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>
        )}
      </div>

      {notice && <div className="bg-soft text-brand-strong px-4 py-2 rounded-md mb-3 text-sm">{notice}</div>}
      {error && (
        <div className="bg-[#fdf4f4] border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-danger/60 hover:text-danger">✕</button>
        </div>
      )}

      {!installed ? (
        <div className="flex-1 rounded-panel border border-line flex flex-col items-center justify-center gap-3">
          <p className="text-sm text-muted">이 VM에는 아직 Docker가 설치되어 있지 않습니다.</p>
          <Button variant="primary" onClick={handleInstall} disabled={installing}>
            {installing ? "설치 중... (수 분 소요될 수 있어요)" : "Docker 설치"}
          </Button>
        </div>
      ) : (
        <div className="flex-1 rounded-panel border border-line overflow-auto">
          {loading ? (
            <PageLoader label="불러오는 중" />
          ) : tab === "containers" ? (
            containers.length === 0 ? (
              <p className="text-sm text-muted-soft text-center py-16">실행 중인 컨테이너가 없습니다</p>
            ) : (
              <Table>
                <thead className="sticky top-0">
                  <tr>
                    <Th>이름</Th>
                    <Th>이미지</Th>
                    <Th>상태</Th>
                    <Th>포트</Th>
                    <Th className="w-40">작업</Th>
                  </tr>
                </thead>
                <tbody>
                  {containers.map((c) => {
                    const state = c.State?.toLowerCase() ?? "";
                    const isRunning = state === "running";
                    const isBusy = busyId === c.ID;
                    return (
                      <tr key={c.ID} className="hover:bg-[#fbfdfc]">
                        <Td className="text-[#3d4941]">{c.Names}</Td>
                        <Td className="text-muted-soft font-mono text-xs">{c.Image}</Td>
                        <Td>
                          <StatusBadge tone={STATE_TONE[state] ?? "off"}>{c.Status}</StatusBadge>
                        </Td>
                        <Td className="text-muted-soft text-xs">{c.Ports || "—"}</Td>
                        <Td>
                          <div className="flex items-center gap-2 text-xs">
                            {isRunning ? (
                              <>
                                <button disabled={isBusy} onClick={() => handleContainerAction(c, "restart")} className="text-muted-soft hover:text-[#3f4c43] disabled:opacity-40">재시작</button>
                                <button disabled={isBusy} onClick={() => handleContainerAction(c, "stop")} className="text-muted-soft hover:text-[#9c6b1f] disabled:opacity-40">정지</button>
                              </>
                            ) : (
                              <button disabled={isBusy} onClick={() => handleContainerAction(c, "start")} className="text-muted-soft hover:text-brand-strong disabled:opacity-40">시작</button>
                            )}
                            <button onClick={() => openLogs(c)} className="text-muted-soft hover:text-[#3f4c43]">로그</button>
                            <button disabled={isBusy} onClick={() => setDeleteTarget({ type: "container", id: c.ID, label: c.Names })} className="text-muted-soft hover:text-danger disabled:opacity-40">삭제</button>
                          </div>
                        </Td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            )
          ) : tab === "images" ? (
            images.length === 0 ? (
              <p className="text-sm text-muted-soft text-center py-16">이미지가 없습니다</p>
            ) : (
              <Table>
                <thead className="sticky top-0">
                  <tr>
                    <Th>저장소</Th>
                    <Th>태그</Th>
                    <Th>크기</Th>
                    <Th>생성일</Th>
                    <Th className="w-20">작업</Th>
                  </tr>
                </thead>
                <tbody>
                  {images.map((img) => (
                    <tr key={img.ID} className="hover:bg-[#fbfdfc]">
                      <Td className="text-[#3d4941] font-mono text-xs">{img.Repository}</Td>
                      <Td className="text-muted-soft font-mono text-xs">{img.Tag}</Td>
                      <Td className="text-muted">{img.Size}</Td>
                      <Td className="text-muted text-xs">{img.CreatedAt}</Td>
                      <Td>
                        <button
                          disabled={busyId === img.ID}
                          onClick={() => setDeleteTarget({ type: "image", id: img.ID, label: `${img.Repository}:${img.Tag}` })}
                          className="text-xs text-muted-soft hover:text-danger disabled:opacity-40"
                        >
                          삭제
                        </button>
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )
          ) : tab === "networks" ? (
            networks.length === 0 ? (
              <p className="text-sm text-muted-soft text-center py-16">네트워크가 없습니다</p>
            ) : (
              <Table>
                <thead className="sticky top-0">
                  <tr>
                    <Th>이름</Th>
                    <Th>드라이버</Th>
                    <Th>스코프</Th>
                    <Th className="w-20">작업</Th>
                  </tr>
                </thead>
                <tbody>
                  {networks.map((net) => (
                    <tr key={net.ID} className="hover:bg-[#fbfdfc]">
                      <Td className="text-[#3d4941]">{net.Name}</Td>
                      <Td className="text-muted">{net.Driver}</Td>
                      <Td className="text-muted">{net.Scope}</Td>
                      <Td>
                        <button
                          disabled={busyId === net.ID || ["bridge", "host", "none"].includes(net.Name)}
                          onClick={() => setDeleteTarget({ type: "network", id: net.ID, label: net.Name })}
                          className="text-xs text-muted-soft hover:text-danger disabled:opacity-40"
                        >
                          삭제
                        </button>
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )
          ) : composeStacks.length === 0 ? (
            <p className="text-sm text-muted-soft text-center py-16">배포된 Compose 스택이 없습니다</p>
          ) : (
            <Table>
              <thead className="sticky top-0">
                <tr>
                  <Th>이름</Th>
                  <Th>상태</Th>
                  <Th>설정 파일</Th>
                </tr>
              </thead>
              <tbody>
                {composeStacks.map((stack) => (
                  <tr key={stack.Name} className="hover:bg-[#fbfdfc]">
                    <Td className="text-[#3d4941]">{stack.Name}</Td>
                    <Td className="text-muted">{stack.Status}</Td>
                    <Td className="text-muted font-mono text-xs truncate max-w-xs">{stack.ConfigFiles}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </div>
      )}

      {/* 네트워크 생성 모달 */}
      <Modal open={showCreateNetwork} onClose={() => setShowCreateNetwork(false)}>
        <div className="mx-auto w-[380px] rounded-panel bg-panel p-6">
          <h2 className="text-base font-bold mb-4">네트워크 생성</h2>
          <form onSubmit={handleCreateNetwork} className="flex flex-col gap-1">
            <Field label="이름" htmlFor="network-name">
              <Input
                id="network-name"
                name="network-name"
                autoFocus
                value={networkForm.name}
                onChange={(e) => setNetworkForm((f) => ({ ...f, name: e.target.value }))}
                required
              />
            </Field>
            <Field label="드라이버" htmlFor="network-driver">
              <Select
                id="network-driver"
                name="network-driver"
                value={networkForm.driver}
                onChange={(e) => setNetworkForm((f) => ({ ...f, driver: e.target.value }))}
              >
                <option value="bridge">bridge</option>
                <option value="overlay">overlay</option>
                <option value="host">host</option>
                <option value="none">none</option>
              </Select>
            </Field>
            <div className="flex gap-2 mt-1">
              <Button type="button" onClick={() => setShowCreateNetwork(false)} className="flex-1">
                취소
              </Button>
              <Button type="submit" variant="primary" disabled={networkCreating || !networkForm.name.trim()} className="flex-1">
                {networkCreating ? "생성 중..." : "생성"}
              </Button>
            </div>
          </form>
        </div>
      </Modal>

      {/* 삭제 확인 모달 */}
      <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        {deleteTarget && (
          <div className="mx-auto w-[340px] rounded-panel bg-panel p-6">
            <h2 className="text-base font-bold mb-2">
              {deleteTarget.type === "container" ? "컨테이너 삭제" : deleteTarget.type === "image" ? "이미지 삭제" : "네트워크 삭제"}
            </h2>
            <p className="text-sm text-muted mb-5">
              <span className="font-bold text-[#3f4c43]">{deleteTarget.label}</span>을(를) 삭제하면 복구할 수 없습니다. 계속하시겠습니까?
            </p>
            <div className="flex gap-2">
              <Button onClick={() => setDeleteTarget(null)} className="flex-1">
                취소
              </Button>
              <Button variant="danger-solid" onClick={handleDelete} disabled={busyId === deleteTarget.id} className="flex-1">
                삭제
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* 로그 모달 */}
      <Modal open={!!logsTarget} onClose={() => setLogsTarget(null)}>
        {logsTarget && (
          <div className="mx-auto flex h-[70vh] w-full max-w-3xl flex-col rounded-panel bg-panel p-6">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-base font-bold truncate">{logsTarget.Names} 로그</h2>
              <div className="flex items-center gap-2">
                <select
                  value={logsTail}
                  onChange={(e) => {
                    const v = Number(e.target.value);
                    setLogsTail(v);
                    fetchLogs(logsTarget, v);
                  }}
                  className="text-xs h-7 px-2 border border-line-strong rounded-md"
                >
                  <option value={200}>최근 200줄</option>
                  <option value={1000}>최근 1000줄</option>
                  <option value={5000}>최근 5000줄</option>
                </select>
                <button onClick={() => fetchLogs(logsTarget, logsTail)} className="text-xs text-muted hover:text-[#3f4c43]">새로고침</button>
                <button onClick={() => setLogsTarget(null)} className="text-muted-soft hover:text-muted">✕</button>
              </div>
            </div>
            <div className="flex-1 bg-[#121814] rounded-panel p-3 overflow-auto">
              {logsLoading ? (
                <PageLoader label="불러오는 중" />
              ) : (
                <pre className="text-[11px] text-gray-100 whitespace-pre-wrap break-all font-mono">{logsContent || "로그가 없습니다."}</pre>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
