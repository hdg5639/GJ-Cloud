"use client";

import { useEffect, useState, useCallback, type ReactNode } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { PortResponse } from "@/lib/api-client";
import { useVmEvents } from "@/hooks/use-vm-events";
import type { VmResponse, VmStatusEvent } from "@/lib/types";
import VmSpecModal from "@/components/vm-spec-modal";

function CodeBlock({
  id,
  value,
  copiedKey,
  onCopy,
}: {
  id: string;
  value: string;
  copiedKey: string | null;
  onCopy: (key: string | null) => void;
}) {
  const isCopied = copiedKey === id;
  function handleCopy() {
    navigator.clipboard.writeText(value);
    onCopy(id);
    setTimeout(() => onCopy(null), 1500);
  }
  return (
    <div className="relative group bg-slate-50 border border-slate-200 rounded-lg px-3 py-3 pr-20 overflow-x-auto">
      <pre className="font-mono text-xs text-slate-800 whitespace-pre-wrap break-all leading-relaxed">{value}</pre>
      <button
        onClick={handleCopy}
        className={`absolute right-2 top-2 text-[11px] font-medium px-2 py-1 rounded transition-all ${
          isCopied
            ? "bg-[#03C75A]/10 text-[#03C75A]"
            : "text-slate-400 hover:text-slate-700 hover:bg-slate-200"
        }`}
      >
        {isCopied ? "✓ 복사됨" : "복사"}
      </button>
    </div>
  );
}

function GuideStep({ num, title, children }: { num: number; title: string; children: ReactNode }) {
  return (
    <div className="flex gap-3">
      <div className="flex-shrink-0 w-5 h-5 rounded-full bg-gray-700 text-white text-[11px] font-semibold flex items-center justify-center mt-0.5">
        {num}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium text-gray-800 mb-1.5">{title}</p>
        {children}
      </div>
    </div>
  );
}

const STATUS_STYLE: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  CREATING: "bg-amber-100 text-amber-700",
  BOOTING: "bg-amber-100 text-amber-700",
  RUNNING: "bg-[#03C75A]/10 text-[#03C75A]",
  STARTING: "bg-amber-100 text-amber-700",
  STOPPING: "bg-amber-100 text-amber-700",
  STOPPED: "bg-gray-100 text-gray-600",
  SUSPENDING: "bg-amber-100 text-amber-700",
  SUSPENDED: "bg-gray-100 text-gray-600",
  FAILED: "bg-red-100 text-red-700",
  DELETING: "bg-red-100 text-red-700",
  DELETED: "bg-gray-100 text-gray-400",
};

export default function InstanceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { accessToken, refresh } = useAuth();
  const [vm, setVm] = useState<VmResponse | null>(null);
  const [accordionOpen, setAccordionOpen] = useState(false);
  const [installTab, setInstallTab] = useState<"mac" | "win" | "linux">("mac");
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [sshGuideTab, setSshGuideTab] = useState<"own" | "generated">("own");
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // SSH 접근 이메일
  const [sshEmails, setSshEmails] = useState<string[]>([]);
  const [newSshEmail, setNewSshEmail] = useState("");
  const [sshEmailLoading, setSshEmailLoading] = useState(false);

  // 포트 관리
  const [ports, setPorts] = useState<PortResponse[]>([]);
  const [showPortModal, setShowPortModal] = useState(false);
  const [portForm, setPortForm] = useState({
    port: "",
    protocol: "HTTP" as "HTTP" | "TCP",
    visibility: "PUBLIC" as "PUBLIC" | "PRIVATE",
    nickname: "",
    initialEmails: "",
  });
  const [portLoading, setPortLoading] = useState(false);
  const [expandedPortId, setExpandedPortId] = useState<string | null>(null);
  const [newPortEmail, setNewPortEmail] = useState<Record<string, string>>({});
  const [portEmailLoading, setPortEmailLoading] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!accessToken) return;
    api.vm.get(accessToken, id).then(setVm).catch(() => {});
    api.vm.getSshAccess(accessToken, id).then(setSshEmails).catch(() => {});
    api.vm.getPorts(accessToken, id).then(setPorts).catch(() => {});
  }, [accessToken, id]);

  const handleVmEvent = useCallback(
    (event: VmStatusEvent) => {
      if (event.vmId === id) {
        setVm((prev) =>
          prev
            ? {
                ...prev,
                status: event.status as VmResponse["status"],
                internalIp: event.internalIp ?? prev.internalIp,
                errorMessage: event.errorMessage ?? prev.errorMessage,
              }
            : prev
        );
      }
    },
    [id]
  );

  const { reconnect: reconnectSse } = useVmEvents(accessToken ?? "", handleVmEvent, !!accessToken, refresh);

  async function handlePower(action: "START" | "STOP" | "REBOOT" | "SUSPEND") {
    if (!accessToken) return;
    try {
      const updated = await api.vm.power(accessToken, id, action);
      setVm(updated);
    } catch (err) {
      alert(err instanceof Error ? err.message : "전원 제어에 실패했습니다");
    }
  }

  async function handleDelete() {
    if (!accessToken) return;
    setDeleting(true);
    try {
      await api.vm.delete(accessToken, id);
      router.push("/instances");
    } catch (err) {
      alert(err instanceof Error ? err.message : "삭제에 실패했습니다");
      setDeleting(false);
      setConfirmDelete(false);
    }
  }

  async function handleAddSshEmail(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !newSshEmail) return;
    setSshEmailLoading(true);
    try {
      await api.vm.addSshAccess(accessToken, id, newSshEmail);
      setSshEmails((prev) => [...prev, newSshEmail]);
      setNewSshEmail("");
    } catch (err) {
      alert(err instanceof Error ? err.message : "이메일 추가에 실패했습니다");
    } finally {
      setSshEmailLoading(false);
    }
  }

  async function handleRemoveSshEmail(email: string) {
    if (!accessToken) return;
    try {
      await api.vm.removeSshAccess(accessToken, id, email);
      setSshEmails((prev) => prev.filter((e) => e !== email));
    } catch (err) {
      alert(err instanceof Error ? err.message : "이메일 삭제에 실패했습니다");
    }
  }

  async function handleAddPort(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken) return;
    setPortLoading(true);
    try {
      const emails =
        portForm.visibility === "PRIVATE" && portForm.initialEmails
          ? portForm.initialEmails.split(",").map((e) => e.trim()).filter(Boolean)
          : undefined;
      const port = await api.vm.addPort(accessToken, id, {
        port: Number(portForm.port),
        protocol: portForm.protocol,
        visibility: portForm.visibility,
        nickname: portForm.nickname,
        ...(emails ? { initialEmails: emails } : {}),
      });
      setPorts((prev) => [...prev, port]);
      setShowPortModal(false);
      setPortForm({ port: "", protocol: "HTTP", visibility: "PUBLIC", nickname: "", initialEmails: "" });
    } catch (err) {
      alert(err instanceof Error ? err.message : "포트 추가에 실패했습니다");
    } finally {
      setPortLoading(false);
    }
  }

  async function handleDeletePort(portId: string) {
    if (!accessToken) return;
    try {
      await api.vm.deletePort(accessToken, id, portId);
      setPorts((prev) => prev.filter((p) => p.id !== portId));
    } catch (err) {
      alert(err instanceof Error ? err.message : "포트 삭제에 실패했습니다");
    }
  }

  async function handleAddPortEmail(portId: string) {
    if (!accessToken) return;
    const email = newPortEmail[portId];
    if (!email) return;
    setPortEmailLoading((prev) => ({ ...prev, [portId]: true }));
    try {
      const updated = await api.vm.addPortAccess(accessToken, id, portId, email);
      setPorts((prev) => prev.map((p) => (p.id === portId ? updated : p)));
      setNewPortEmail((prev) => ({ ...prev, [portId]: "" }));
    } catch (err) {
      alert(err instanceof Error ? err.message : "이메일 추가에 실패했습니다");
    } finally {
      setPortEmailLoading((prev) => ({ ...prev, [portId]: false }));
    }
  }

  async function handleRemovePortEmail(portId: string, email: string) {
    if (!accessToken) return;
    try {
      const updated = await api.vm.removePortAccess(accessToken, id, portId, email);
      setPorts((prev) => prev.map((p) => (p.id === portId ? updated : p)));
    } catch (err) {
      alert(err instanceof Error ? err.message : "이메일 삭제에 실패했습니다");
    }
  }

  if (!vm) return <p className="text-sm text-gray-400">불러오는 중...</p>;

  const isRunning = vm.status === "RUNNING";
  const isStopped = vm.status === "STOPPED";
  const isTransitioning = ["PENDING", "CREATING", "BOOTING", "STARTING", "STOPPING", "SUSPENDING", "DELETING"].includes(vm.status);

  return (
    <div>
      {/* 헤더 */}
      <div className="flex items-center gap-2 mb-1">
        <button onClick={() => router.push("/instances")} className="text-xs text-gray-500 hover:text-gray-700">
          인스턴스
        </button>
        <span className="text-xs text-gray-400">/</span>
        <span className="text-xs text-gray-700">{vm.name}</span>
      </div>

      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-medium text-gray-900">{vm.name}</h1>
          <span className={`text-xs font-medium px-2.5 py-1 rounded-md ${STATUS_STYLE[vm.status] ?? "bg-gray-100 text-gray-600"}`}>
            {vm.status}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={reconnectSse}
            title="상태 동기화"
            className="h-8 w-8 flex items-center justify-center border border-gray-300 rounded-md hover:bg-gray-50"
          >
            <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
          <div className="w-px h-5 bg-gray-200" />
          <button
            onClick={() => handlePower("REBOOT")}
            disabled={!isRunning}
            className="text-sm px-3.5 h-8 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-40"
          >
            재시작
          </button>
          <button
            onClick={() => handlePower(isRunning ? "STOP" : "START")}
            disabled={isTransitioning || vm.status === "SUSPENDED" || vm.status === "DELETED"}
            className="text-sm px-3.5 h-8 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-40"
          >
            {isRunning ? "정지" : "시작"}
          </button>
          <div className="w-px h-5 bg-gray-200" />
          <button
            onClick={() => router.push(`/instances/${id}/metrics`)}
            className="text-sm px-3.5 h-8 border border-gray-300 rounded-md hover:bg-gray-50"
          >
            메트릭
          </button>
          <button
            onClick={() => setShowUpgradeModal(true)}
            className="text-sm px-3.5 h-8 border border-gray-300 rounded-md hover:bg-gray-50"
          >
            스펙 변경
          </button>
          <div className="w-px h-5 bg-gray-200" />
          <button
            onClick={() => setConfirmDelete(true)}
            className="text-sm px-3.5 h-8 border border-red-200 bg-red-50 text-red-600 rounded-md hover:bg-red-100"
          >
            삭제
          </button>
        </div>
      </div>

      {/* needsReboot 배너 */}
      {vm.needsReboot && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 mb-5 flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-amber-800">재부팅이 필요합니다</p>
            <p className="text-xs text-amber-700 mt-0.5">플랜 변경(CPU/RAM)을 적용하려면 인스턴스를 재부팅해야 해요</p>
          </div>
          <button
            onClick={() => handlePower("REBOOT")}
            className="bg-amber-500 text-amber-950 text-xs font-medium px-3.5 h-8 rounded-md shrink-0"
          >
            지금 재부팅
          </button>
        </div>
      )}

      {/* ERROR 에러 메시지 */}
      {vm.status === "FAILED" && vm.errorMessage && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-5">
          <p className="text-sm font-medium text-red-800">오류 발생</p>
          <p className="text-xs text-red-700 mt-0.5">{vm.errorMessage}</p>
        </div>
      )}

      {/* 정보 카드 */}
      <div className="grid grid-cols-4 gap-3 mb-6">
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">플랜</p>
          <p className="text-base font-medium text-gray-900">{vm.planType}</p>
        </div>
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">디스크</p>
          <p className="text-base font-medium text-gray-900">{vm.diskSizeGb}GB</p>
        </div>
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">내부 IP</p>
          <p className="text-base font-medium text-gray-900">{vm.internalIp ?? "할당 중"}</p>
        </div>
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">서브도메인</p>
          <p className="text-base font-medium text-gray-900">{vm.subdomain ?? "-"}</p>
        </div>
      </div>

      {/* SSH 접속 정보 */}
      <div className="bg-gray-50 rounded-lg p-5 mb-4">
        <p className="text-xs font-medium text-gray-500 mb-3">SSH 접속 정보</p>
        <div className="grid grid-cols-3 gap-2 mb-3">
          <div className="bg-white border border-gray-200 rounded-md p-2.5">
            <p className="text-[11px] text-gray-400 mb-1">호스트네임</p>
            <p className="font-mono text-sm text-gray-900">{vm.subdomain}.gamjabox.cloud</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-md p-2.5">
            <p className="text-[11px] text-gray-400 mb-1">사용자</p>
            <p className="font-mono text-sm text-gray-900">ubuntu</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-md p-2.5">
            <p className="text-[11px] text-gray-400 mb-1">인증 방식</p>
            <p className="font-mono text-sm text-gray-900">등록한 SSH 키</p>
          </div>
        </div>

        <button
          onClick={() => setAccordionOpen(!accordionOpen)}
          className={`flex items-center justify-between w-full mt-1 px-3 py-2.5 rounded-lg border transition-colors ${
            accordionOpen
              ? "bg-gray-900 border-gray-900 text-white"
              : "bg-white border-gray-300 text-gray-700 hover:border-gray-400 hover:bg-gray-50"
          }`}
        >
          <span className="text-sm font-medium">🔌 SSH 접속 방법 보기</span>
          <span className={`text-xs transition-transform duration-200 ${accordionOpen ? "rotate-180" : ""}`}>▾</span>
        </button>

        {accordionOpen && (
          <div className="mt-4 space-y-6">

            <div className="flex items-start gap-2 px-3 py-2.5 bg-amber-50 border border-amber-200 rounded-lg">
              <span className="text-amber-500 text-sm mt-0.5">⚠</span>
              <p className="text-[11px] text-amber-800">VM 생성 직후에는 DNS 전파 속도로 인해 <strong>5~10분가량 SSH 접속이 되지 않을 수 있습니다.</strong> 잠시 후 다시 시도해주세요.</p>
            </div>

            {/* Step 1 — cloudflared 설치 */}
            <div>
              <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-widest mb-3">Step 1 — cloudflared 설치</p>
              <div className="flex gap-1 mb-2">
                {(["mac", "win", "linux"] as const).map((os) => (
                  <button
                    key={os}
                    onClick={() => setInstallTab(os)}
                    className={`text-xs px-3 h-7 rounded-md transition-colors ${
                      installTab === os
                        ? "bg-gray-900 text-white"
                        : "bg-white border border-gray-200 text-gray-500 hover:border-gray-400"
                    }`}
                  >
                    {os === "mac" ? "macOS" : os === "win" ? "Windows" : "Linux"}
                  </button>
                ))}
              </div>
              {installTab === "mac" && (
                <CodeBlock id="install-mac" value="brew install cloudflared" copiedKey={copiedKey} onCopy={setCopiedKey} />
              )}
              {installTab === "win" && (
                <CodeBlock id="install-win" value="winget install --id Cloudflare.cloudflared" copiedKey={copiedKey} onCopy={setCopiedKey} />
              )}
              {installTab === "linux" && (
                <CodeBlock
                  id="install-linux"
                  value={`sudo mkdir -p --mode=0755 /usr/share/keyrings
curl -fsSL https://pkg.cloudflare.com/cloudflare-public-v2.gpg | sudo tee /usr/share/keyrings/cloudflare-public-v2.gpg >/dev/null
echo 'deb [signed-by=/usr/share/keyrings/cloudflare-public-v2.gpg] https://pkg.cloudflare.com/cloudflared any main' | sudo tee /etc/apt/sources.list.d/cloudflared.list
sudo apt-get update && sudo apt-get install cloudflared`}
                  copiedKey={copiedKey}
                  onCopy={setCopiedKey}
                />
              )}
              <p className="text-[11px] text-gray-600 mt-2">설치 확인: <code className="bg-gray-200 text-gray-700 px-1 rounded">cloudflared --version</code></p>
            </div>

            {/* Step 2 — SSH 접속 */}
            <div>
              <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-widest mb-3">Step 2 — SSH 접속</p>
              <div className="flex gap-1 mb-4">
                <button
                  onClick={() => setSshGuideTab("own")}
                  className={`text-xs px-3 h-7 rounded-md transition-colors ${
                    sshGuideTab === "own"
                      ? "bg-gray-900 text-white"
                      : "bg-white border border-gray-200 text-gray-500 hover:border-gray-400"
                  }`}
                >
                  개인 SSH 키로 접속
                </button>
                <button
                  onClick={() => setSshGuideTab("generated")}
                  className={`text-xs px-3 h-7 rounded-md transition-colors ${
                    sshGuideTab === "generated"
                      ? "bg-gray-900 text-white"
                      : "bg-white border border-gray-200 text-gray-500 hover:border-gray-400"
                  }`}
                >
                  발급받은 키로 접속
                </button>
              </div>

              {sshGuideTab === "own" && (
                <div className="space-y-4">
                  <GuideStep num={1} title="공개키 확인">
                    <CodeBlock id="own-pubkey" value="cat ~/.ssh/id_ed25519.pub" copiedKey={copiedKey} onCopy={setCopiedKey} />
                    <p className="text-[11px] text-gray-600 mt-1.5">portal → SSH 키 → 키 등록 → <strong>직접 등록</strong> 탭에 붙여넣기</p>
                  </GuideStep>
                  <GuideStep num={2} title="~/.ssh/config 파일에 아래 내용 추가">
                    <div className="mb-1.5 flex items-center gap-1.5 text-[11px] text-slate-500">
                      <span>파일 경로:</span>
                      <code className="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded font-mono">~/.ssh/config</code>
                      <span className="text-slate-400">·</span>
                      <code className="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded font-mono">C:\Users\{"{사용자명}"}\.ssh\config</code>
                      <span className="text-slate-400">(Windows)</span>
                    </div>
                    <CodeBlock
                      id="own-config"
                      value={`Host ${vm.subdomain}
    HostName ${vm.subdomain}.gamjabox.cloud
    ProxyCommand cloudflared access ssh --hostname %h
    User ubuntu
    IdentityFile ~/.ssh/id_ed25519`}
                      copiedKey={copiedKey}
                      onCopy={setCopiedKey}
                    />
                    <p className="text-[11px] text-slate-500 mt-1.5">파일이 없으면 새로 만들면 됩니다. 들여쓰기는 반드시 <strong>스페이스 4칸</strong>.</p>
                  </GuideStep>
                  <GuideStep num={3} title="접속">
                    <CodeBlock id="own-connect" value={`ssh ${vm.subdomain}`} copiedKey={copiedKey} onCopy={setCopiedKey} />
                    <p className="text-[11px] text-gray-600 mt-1.5">최초 접속 시 브라우저에서 Cloudflare 인증 → 등록된 이메일로 확인 후 자동 접속</p>
                  </GuideStep>
                </div>
              )}

              {sshGuideTab === "generated" && (
                <div className="space-y-4">
                  <GuideStep num={1} title="다운로드한 키 파일 준비">
                    <div className="flex gap-1 mb-2">
                      {(["mac", "win"] as const).map((os) => (
                        <button
                          key={os}
                          onClick={() => setInstallTab(os)}
                          className={`text-[11px] px-2.5 h-6 rounded transition-colors ${
                            installTab === os
                              ? "bg-gray-700 text-white"
                              : "bg-white border border-gray-200 text-gray-500"
                          }`}
                        >
                          {os === "mac" ? "macOS / Linux" : "Windows"}
                        </button>
                      ))}
                    </div>
                    {installTab !== "win" ? (
                      <>
                        <CodeBlock
                          id="gen-move-mac"
                          value={`mv ~/Downloads/{키이름}_id_ed25519.pem ~/.ssh/{키이름}_id_ed25519.pem\nchmod 600 ~/.ssh/{키이름}_id_ed25519.pem`}
                          copiedKey={copiedKey}
                          onCopy={setCopiedKey}
                        />
                        <p className="text-[11px] text-gray-600 mt-1.5"><code className="bg-gray-200 text-gray-700 px-1 rounded">chmod 600</code> 필수 — 권한이 열려 있으면 SSH가 키 사용을 거부합니다</p>
                      </>
                    ) : (
                      <>
                        <CodeBlock
                          id="gen-move-win"
                          value={`Move-Item "$env:USERPROFILE\\Downloads\\{키이름}_id_ed25519.pem" "$env:USERPROFILE\\.ssh\\{키이름}_id_ed25519.pem"`}
                          copiedKey={copiedKey}
                          onCopy={setCopiedKey}
                        />
                        <p className="text-[11px] text-gray-600 mt-1.5">오류 시 파일 속성 → 상속 해제 후 본인 읽기 권한만 부여</p>
                      </>
                    )}
                  </GuideStep>
                  <GuideStep num={2} title="~/.ssh/config 파일에 아래 내용 추가">
                    <div className="mb-1.5 flex items-center gap-1.5 text-[11px] text-slate-500">
                      <span>파일 경로:</span>
                      <code className="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded font-mono">~/.ssh/config</code>
                      <span className="text-slate-400">·</span>
                      <code className="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded font-mono">C:\Users\{"{사용자명}"}\.ssh\config</code>
                      <span className="text-slate-400">(Windows)</span>
                    </div>
                    <CodeBlock
                      id="gen-config"
                      value={`Host ${vm.subdomain}
    HostName ${vm.subdomain}.gamjabox.cloud
    ProxyCommand cloudflared access ssh --hostname %h
    User ubuntu
    IdentityFile ~/.ssh/{키이름}_id_ed25519.pem`}
                      copiedKey={copiedKey}
                      onCopy={setCopiedKey}
                    />
                    <p className="text-[11px] text-slate-500 mt-1.5">파일이 없으면 새로 만들면 됩니다. 들여쓰기는 반드시 <strong>스페이스 4칸</strong>.</p>
                  </GuideStep>
                  <GuideStep num={3} title="접속">
                    <CodeBlock id="gen-connect" value={`ssh ${vm.subdomain}`} copiedKey={copiedKey} onCopy={setCopiedKey} />
                  </GuideStep>
                  <div className="bg-amber-50 border border-amber-200 rounded-md px-3 py-2.5 text-[11px] text-amber-700">
                    발급받은 개인키는 <strong>재다운로드 불가</strong>합니다. 분실 시 새 키를 발급받아 다시 등록해야 합니다.
                  </div>
                </div>
              )}
            </div>

            {/* 1회성 접속 */}
            <div>
              <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-widest mb-2">config 없이 1회성 접속</p>
              <CodeBlock
                id="oneliner"
                value={`ssh -i ~/.ssh/{키파일}.pem -o ProxyCommand="cloudflared access ssh --hostname ${vm.subdomain}.gamjabox.cloud" ubuntu@${vm.subdomain}.gamjabox.cloud`}
                copiedKey={copiedKey}
                onCopy={setCopiedKey}
              />
            </div>

            {/* FAQ */}
            <div>
              <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-widest mb-2">자주 발생하는 문제</p>
              <div className="bg-white border border-gray-200 rounded-md divide-y divide-gray-100 text-xs">
                {[
                  ["Cloudflare 인증 페이지가 안 뜸", "cloudflared 미설치 또는 PATH 미등록", "cloudflared --version 으로 확인"],
                  ["Permission denied (publickey)", "공개키 미등록 또는 다른 키 사용", "portal SSH 키 목록 및 IdentityFile 경로 재확인"],
                  ["UNPROTECTED PRIVATE KEY FILE", "개인키 파일 권한 문제", "chmod 600 적용"],
                  ["매번 인증 페이지가 뜸", "Cloudflare Access 세션 만료 (기본 24h)", "재인증 필요 — 정상 동작"],
                  ["인증됐는데 접속 거부", "SSH 접근 허용 이메일 미등록", "인스턴스 상세 → SSH 허용 이메일 확인"],
                ].map(([symptom, cause, fix]) => (
                  <div key={symptom} className="px-3 py-2.5 grid grid-cols-3 gap-2">
                    <span className="text-gray-800 font-medium">{symptom}</span>
                    <span className="text-gray-500">{cause}</span>
                    <span className="text-[#03C75A] font-medium">{fix}</span>
                  </div>
                ))}
              </div>
            </div>

          </div>
        )}
      </div>

      {/* SSH 접근 이메일 관리 */}
      <div className="bg-gray-50 rounded-lg p-5 mb-4">
        <p className="text-xs font-medium text-gray-500 mb-3">SSH 접근 허용 이메일</p>
        <div className="flex flex-col gap-1.5 mb-3">
          {sshEmails.map((email) => (
            <div key={email} className="flex items-center justify-between bg-white border border-gray-200 rounded-md px-3 py-2">
              <span className="text-sm text-gray-900">{email}</span>
              <button
                onClick={() => handleRemoveSshEmail(email)}
                className="text-xs text-gray-400 hover:text-red-500"
              >
                삭제
              </button>
            </div>
          ))}
          {sshEmails.length === 0 && (
            <p className="text-xs text-gray-400">허용된 이메일이 없습니다.</p>
          )}
        </div>
        {sshEmails.length < 10 && (
          <form onSubmit={handleAddSshEmail} className="flex gap-2">
            <input
              type="email"
              value={newSshEmail}
              onChange={(e) => setNewSshEmail(e.target.value)}
              placeholder="추가할 이메일"
              className="flex-1 h-8 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
            />
            <button
              type="submit"
              disabled={sshEmailLoading || !newSshEmail}
              className="h-8 px-3 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60"
            >
              추가
            </button>
          </form>
        )}
      </div>

      {/* 포트 관리 */}
      <div className="bg-gray-50 rounded-lg p-5">
        <div className="flex items-center justify-between mb-3">
          <p className="text-xs font-medium text-gray-500">포트 노출</p>
          {ports.length < 5 && (
            <button onClick={() => setShowPortModal(true)} className="text-xs text-[#03C75A] font-medium">
              + 포트 추가
            </button>
          )}
        </div>

        {ports.length === 0 ? (
          <p className="text-xs text-gray-400">노출된 포트가 없습니다.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {ports.map((port) => (
              <div key={port.id} className="bg-white border border-gray-200 rounded-md">
                <div className="flex items-center justify-between px-3 py-2.5">
                  <div>
                    <span className="text-sm font-medium text-gray-900">{port.nickname}</span>
                    <span className="text-xs text-gray-500 ml-2">
                      :{port.port} · {port.protocol} · {port.visibility === "PUBLIC" ? "공개" : "비공개"}
                    </span>
                    <p className="font-mono text-[11px] text-gray-400 mt-0.5">{port.fullDomain}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {port.visibility === "PRIVATE" && (
                      <button
                        onClick={() => setExpandedPortId(expandedPortId === port.id ? null : port.id)}
                        className="text-xs text-gray-500 hover:text-gray-700"
                      >
                        이메일 {expandedPortId === port.id ? "▴" : "▾"}
                      </button>
                    )}
                    <button
                      onClick={() => handleDeletePort(port.id)}
                      className="text-xs text-gray-400 hover:text-red-500"
                    >
                      삭제
                    </button>
                  </div>
                </div>

                {/* 비공개 포트 이메일 관리 */}
                {port.visibility === "PRIVATE" && expandedPortId === port.id && (
                  <div className="border-t border-gray-100 px-3 pb-3 pt-2">
                    <p className="text-[11px] text-gray-400 mb-2">접근 허용 이메일</p>
                    <div className="flex flex-col gap-1 mb-2">
                      {port.accessEmails.map((email) => (
                        <div key={email} className="flex items-center justify-between py-1">
                          <span className="text-xs text-gray-700">{email}</span>
                          <button
                            onClick={() => handleRemovePortEmail(port.id, email)}
                            className="text-[11px] text-gray-400 hover:text-red-500"
                          >
                            삭제
                          </button>
                        </div>
                      ))}
                      {port.accessEmails.length === 0 && (
                        <p className="text-xs text-gray-400">허용된 이메일이 없습니다.</p>
                      )}
                    </div>
                    {port.accessEmails.length < 10 && (
                      <div className="flex gap-2">
                        <input
                          type="email"
                          value={newPortEmail[port.id] ?? ""}
                          onChange={(e) => setNewPortEmail((prev) => ({ ...prev, [port.id]: e.target.value }))}
                          placeholder="이메일 추가"
                          className="flex-1 h-7 px-2 border border-gray-300 rounded text-xs focus:outline-none focus:border-[#03C75A]"
                        />
                        <button
                          onClick={() => handleAddPortEmail(port.id)}
                          disabled={portEmailLoading[port.id] || !newPortEmail[port.id]}
                          className="h-7 px-2.5 bg-[#03C75A] text-white rounded text-xs disabled:opacity-60"
                        >
                          추가
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 포트 추가 모달 */}
      {showPortModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[380px]">
            <h2 className="text-base font-medium text-gray-900 mb-4">포트 추가</h2>
            <form onSubmit={handleAddPort} className="flex flex-col gap-3">
              <div>
                <label className="text-xs text-gray-500 block mb-1">닉네임</label>
                <input
                  type="text"
                  value={portForm.nickname}
                  onChange={(e) => setPortForm((f) => ({ ...f, nickname: e.target.value.toLowerCase() }))}
                  placeholder="예: myapp (소문자·숫자·하이픈, 최대 20자)"
                  maxLength={20}
                  pattern="^[a-z0-9]([a-z0-9-]*[a-z0-9])?$"
                  required
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                />
                <p className="text-[11px] text-gray-400 mt-1">서브도메인: {portForm.nickname ? `${vm.subdomain}-${portForm.nickname}.gamjabox.cloud` : "—"}</p>
              </div>
              <div>
                <label className="text-xs text-gray-500 block mb-1">포트 번호</label>
                <input
                  type="number"
                  min={1}
                  max={65535}
                  value={portForm.port}
                  onChange={(e) => setPortForm((f) => ({ ...f, port: e.target.value }))}
                  placeholder="예: 3000"
                  required
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 block mb-1">프로토콜</label>
                <select
                  value={portForm.protocol}
                  onChange={(e) => setPortForm((f) => ({ ...f, protocol: e.target.value as "HTTP" | "TCP" }))}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                >
                  <option value="HTTP">HTTP</option>
                  <option value="TCP">TCP</option>
                </select>
              </div>
              <div>
                <label className="text-xs text-gray-500 block mb-1">공개 설정</label>
                <select
                  value={portForm.visibility}
                  onChange={(e) => setPortForm((f) => ({ ...f, visibility: e.target.value as "PUBLIC" | "PRIVATE" }))}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                >
                  <option value="PUBLIC">공개 (누구나 접근)</option>
                  <option value="PRIVATE">비공개 (이메일 인증 필요)</option>
                </select>
              </div>
              {portForm.visibility === "PRIVATE" && (
                <div>
                  <label className="text-xs text-gray-500 block mb-1">초기 허용 이메일 (쉼표로 구분, 미입력 시 본인 자동 추가)</label>
                  <input
                    type="text"
                    value={portForm.initialEmails}
                    onChange={(e) => setPortForm((f) => ({ ...f, initialEmails: e.target.value }))}
                    placeholder="a@example.com, b@example.com"
                    className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                  />
                </div>
              )}
              <div className="flex gap-2 mt-1">
                <button
                  type="button"
                  onClick={() => setShowPortModal(false)}
                  className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={portLoading || !portForm.port || !portForm.nickname}
                  className="flex-1 h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60"
                >
                  {portLoading ? "추가 중..." : "추가"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {confirmDelete && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[340px]">
            <h2 className="text-base font-medium text-gray-900 mb-2">인스턴스 삭제</h2>
            <p className="text-sm text-gray-500 mb-5">
              <span className="font-medium text-gray-800">{vm.name}</span>을 삭제하면 복구할 수 없습니다. 계속하시겠습니까?
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setConfirmDelete(false)}
                className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600"
              >
                취소
              </button>
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="flex-1 h-9 bg-red-500 text-white rounded-md text-sm disabled:opacity-60"
              >
                {deleting ? "삭제 중..." : "삭제"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 스펙 변경 모달 */}
      {showUpgradeModal && vm && (
        <VmSpecModal
          vm={vm}
          onClose={() => setShowUpgradeModal(false)}
          onSuccess={(updated) => {
            setVm(updated);
            setShowUpgradeModal(false);
          }}
        />
      )}
    </div>
  );
}
