"use client";

import { useEffect, useLayoutEffect, useState, useCallback, useRef, type ReactNode } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { PortResponse } from "@/lib/api-client";
import { useVmEvents } from "@/hooks/use-vm-events";
import type { VmResponse, VmStatusEvent, ProfileResponse } from "@/lib/types";
import VmSpecModal from "@/components/vm-spec-modal";
import { PageLoader } from "@/components/ui/loader";
import CollaborationWriteModal from "@/components/collaboration-write-modal";
import CollaborationCard from "@/components/collaboration-card";
import type { CollaborationResponse, CollaborationType } from "@/lib/types";

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

// 툴바 축소 우선순위: 앞에 있을수록 공간이 부족할 때 먼저 "더보기"로 이동
const TOOLBAR_COLLAPSE_ORDER = ["specChange", "performance", "backup", "docker", "deploy"] as const;
type ToolbarCollapsibleId = (typeof TOOLBAR_COLLAPSE_ORDER)[number];
type ToolbarMeasureId = ToolbarCollapsibleId | "power" | "console" | "files" | "more" | "delete" | "divider";
const TOOLBAR_MEASURE_IDS: ToolbarMeasureId[] = [
  "power", "console", "files", "docker", "deploy", "backup", "performance", "specChange", "more", "delete", "divider",
];
// 브라우저 줌/폰트 렌더링/스크롤바 폭 오차를 흡수하기 위한 여유 폭
const TOOLBAR_SAFETY_MARGIN = 28;

export default function InstanceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const fromOrg = searchParams.get("from") === "org";
  const fromOrgId = searchParams.get("orgId");
  const backPath = fromOrg && fromOrgId ? `/organizations/${fromOrgId}` : "/instances";
  const backLabel = fromOrg ? "협업" : "인스턴스";
  const { accessToken, refresh } = useAuth();
  const [vm, setVm] = useState<VmResponse | null>(null);
  const [accordionOpen, setAccordionOpen] = useState(false);
  const [installTab, setInstallTab] = useState<"mac" | "win" | "linux">("mac");
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [sshGuideTab, setSshGuideTab] = useState<"own" | "generated">("own");
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [showPowerMenu, setShowPowerMenu] = useState(false);
  const powerMenuRef = useRef<HTMLDivElement>(null);
  const [showMoreMenu, setShowMoreMenu] = useState(false);
  const moreMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (powerMenuRef.current && !powerMenuRef.current.contains(e.target as Node)) {
        setShowPowerMenu(false);
      }
      if (moreMenuRef.current && !moreMenuRef.current.contains(e.target as Node)) {
        setShowMoreMenu(false);
      }
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  // ── 툴바 반응형 축소: 실제 DOM 폭을 측정해서 들어갈 수 있는 만큼만 보여주고
  //    나머지는 "더보기"로 이동 (고정 브레이크포인트가 아니라 컨테이너 실측 기반)
  const toolbarRef = useRef<HTMLDivElement>(null);
  const leftSectionRef = useRef<HTMLDivElement>(null);
  const toolbarMeasureRefs = useRef<Partial<Record<ToolbarMeasureId, HTMLElement | null>>>({});
  const [toolbarWidth, setToolbarWidth] = useState(0);
  const [leftSectionWidth, setLeftSectionWidth] = useState(0);
  const [toolbarActionWidths, setToolbarActionWidths] = useState<Record<ToolbarMeasureId, number> | null>(null);

  useLayoutEffect(() => {
    let cancelled = false;

    function measure() {
      const widths = {} as Record<ToolbarMeasureId, number>;
      for (const key of TOOLBAR_MEASURE_IDS) {
        const el = toolbarMeasureRefs.current[key];
        const width = el?.getBoundingClientRect().width ?? 0;
        if (width <= 0) {
          if (!cancelled) setToolbarActionWidths(null);
          return;
        }
        widths[key] = width;
      }
      if (cancelled) return;
      setToolbarActionWidths(widths);
    }

    measure();
    const ro = new ResizeObserver(measure);
    TOOLBAR_MEASURE_IDS.forEach((key) => {
      const el = toolbarMeasureRefs.current[key];
      if (el) ro.observe(el);
    });

    const fonts = typeof document !== "undefined" && "fonts" in document ? document.fonts : null;
    const handleFontsChanged = () => measure();
    fonts?.addEventListener("loadingdone", handleFontsChanged);
    fonts?.ready.then(measure).catch(() => {});

    return () => {
      cancelled = true;
      ro.disconnect();
      fonts?.removeEventListener("loadingdone", handleFontsChanged);
    };
  }, [vm?.id]);

  useLayoutEffect(() => {
    const toolbarEl = toolbarRef.current;
    const leftEl = leftSectionRef.current;
    if (!toolbarEl || !leftEl) return;
    const measure = () => {
      setToolbarWidth(toolbarEl.getBoundingClientRect().width);
      setLeftSectionWidth(leftEl.getBoundingClientRect().width);
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(toolbarEl);
    ro.observe(leftEl);
    return () => ro.disconnect();
  }, [vm?.id]);

  // 측정된 폭을 바탕으로 몇 개까지 접을지 매 렌더링마다 순수 계산 (effect로 동기화하지 않음)
  const { toolbarCollapsedCount, toolbarDeleteCollapsed } = (() => {
    if (
      toolbarWidth <= 0 ||
      leftSectionWidth <= 0 ||
      !toolbarActionWidths ||
      !TOOLBAR_MEASURE_IDS.every((key) => toolbarActionWidths[key] > 0)
    ) {
      return { toolbarCollapsedCount: 0, toolbarDeleteCollapsed: false };
    }
    const actionWidths = toolbarActionWidths;
    const available = toolbarWidth - leftSectionWidth - TOOLBAR_SAFETY_MARGIN;
    const dividerWidth = actionWidths.divider;

    function widthFor(collapsedCount: number, deleteCollapsed: boolean) {
      const hidden = new Set(TOOLBAR_COLLAPSE_ORDER.slice(0, collapsedCount));
      let total =
        actionWidths.power + actionWidths.console + actionWidths.files;
      if (!hidden.has("docker")) total += actionWidths.docker;
      const restIds: ToolbarCollapsibleId[] = ["deploy", "backup", "performance", "specChange"];
      const restVisible = restIds.filter((rid) => !hidden.has(rid));
      const moreVisible = hidden.size > 0 || deleteCollapsed;
      if (restVisible.length > 0 || moreVisible) total += dividerWidth;
      restVisible.forEach((rid) => {
        total += actionWidths[rid];
      });
      if (moreVisible) total += actionWidths.more;
      if (!deleteCollapsed) total += dividerWidth + actionWidths.delete;
      return total;
    }

    for (let n = 0; n <= TOOLBAR_COLLAPSE_ORDER.length; n++) {
      if (widthFor(n, false) <= available) {
        return { toolbarCollapsedCount: n, toolbarDeleteCollapsed: false };
      }
    }
    return { toolbarCollapsedCount: TOOLBAR_COLLAPSE_ORDER.length, toolbarDeleteCollapsed: true };
  })();

  // SSH 접근 이메일
  const [sshEmails, setSshEmails] = useState<string[]>([]);
  const [newSshEmail, setNewSshEmail] = useState("");
  const [sshEmailLoading, setSshEmailLoading] = useState(false);

  // 협업
  const [collabItems, setCollabItems] = useState<CollaborationResponse[]>([]);
  const [collabTypeFilter, setCollabTypeFilter] = useState<CollaborationType | undefined>();
  const [showCollabWrite, setShowCollabWrite] = useState(false);
  const [editingCollab, setEditingCollab] = useState<CollaborationResponse | undefined>();

  // 포트 관리
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [ports, setPorts] = useState<PortResponse[]>([]);
  const [showPortModal, setShowPortModal] = useState(false);
  const [portForm, setPortForm] = useState({
    port: "",
    protocol: "HTTP" as "HTTP" | "TCP",
    visibility: "PUBLIC" as "PUBLIC" | "PRIVATE",
    nickname: "",
    initialEmails: "",
    customSubdomain: "",
  });
  const [subdomainCheck, setSubdomainCheck] = useState<"idle" | "checking" | "available" | "taken" | "reserved" | "pro-only">("idle");
  const subdomainTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [portLoading, setPortLoading] = useState(false);
  const [expandedPortId, setExpandedPortId] = useState<string | null>(null);
  const [newPortEmail, setNewPortEmail] = useState<Record<string, string>>({});
  const [portEmailLoading, setPortEmailLoading] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!accessToken) return;
    api.vm.get(accessToken, id).then(setVm).catch(() => {});
    api.vm.getSshAccess(accessToken, id).then(setSshEmails).catch(() => {});
    api.vm.getPorts(accessToken, id).then(setPorts).catch(() => {});
    api.user.profile(accessToken).then(setProfile).catch(() => {});
    api.collab.list(accessToken, "INSTANCE", id).then(setCollabItems).catch(() => {});
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
      router.push(backPath);
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

  function handleCustomSubdomainChange(value: string) {
    const lower = value.toLowerCase();
    setPortForm((f) => ({ ...f, customSubdomain: lower }));
    setSubdomainCheck("idle");
    if (subdomainTimer.current) clearTimeout(subdomainTimer.current);
    if (!lower || !accessToken) return;
    setSubdomainCheck("checking");
    subdomainTimer.current = setTimeout(async () => {
      try {
        const result = await api.vm.checkSubdomain(accessToken, id, lower);
        if (result.available) {
          setSubdomainCheck("available");
        } else {
          setSubdomainCheck((result.reason as "taken" | "reserved" | "pro-only") ?? "taken");
        }
      } catch {
        setSubdomainCheck("taken");
      }
    }, 500);
  }

  async function handleAddPort(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken) return;
    if (portForm.customSubdomain && subdomainCheck !== "available") return;
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
        ...(portForm.customSubdomain ? { customSubdomain: portForm.customSubdomain } : {}),
      });
      setPorts((prev) => [...prev, port]);
      setShowPortModal(false);
      setPortForm({ port: "", protocol: "HTTP", visibility: "PUBLIC", nickname: "", initialEmails: "", customSubdomain: "" });
      setSubdomainCheck("idle");
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

  if (!vm) return <PageLoader />;

  const isRunning = vm.status === "RUNNING";
  const isStopped = vm.status === "STOPPED";
  const isTransitioning = ["PENDING", "CREATING", "BOOTING", "STARTING", "STOPPING", "SUSPENDING", "DELETING"].includes(vm.status);

  const hiddenToolbarIds = new Set(TOOLBAR_COLLAPSE_ORDER.slice(0, toolbarCollapsedCount));
  const showDocker = !hiddenToolbarIds.has("docker");
  const showDeploy = !hiddenToolbarIds.has("deploy");
  const showBackup = !hiddenToolbarIds.has("backup");
  const showPerformance = !hiddenToolbarIds.has("performance");
  const showSpecChange = !hiddenToolbarIds.has("specChange");
  const restVisibleAny = showDeploy || showBackup || showPerformance || showSpecChange;
  const showMoreButton = hiddenToolbarIds.size > 0 || toolbarDeleteCollapsed;

  return (
    <div>
      {/* 헤더 */}
      <div className="flex items-center gap-2 mb-1">
        <button onClick={() => router.push(backPath)} className="text-xs text-gray-500 hover:text-gray-700">
          {backLabel}
        </button>
        <span className="text-xs text-gray-400">/</span>
        <span className="text-xs text-gray-700">{vm.name}</span>
      </div>

      <div className="mb-5">
        <div ref={toolbarRef} className="relative flex items-center w-full bg-white border border-gray-200 rounded-lg">
          {/* 왼쪽: 이름 · 상태 · 새로고침 */}
          <div ref={leftSectionRef} className="flex items-center gap-2.5 pl-4 pr-3.5 h-10 shrink-0">
            <h1 className="text-[15px] font-medium text-gray-900 truncate max-w-[200px]" title={vm.name}>{vm.name}</h1>
            <span className={`text-xs font-medium px-2 py-0.5 rounded-md whitespace-nowrap ${STATUS_STYLE[vm.status] ?? "bg-gray-100 text-gray-600"}`}>
              {vm.status}
            </span>
            <button
              onClick={reconnectSse}
              title="상태 동기화"
              className="flex items-center justify-center w-7 h-7 text-gray-400 hover:text-gray-700 hover:bg-gray-50 rounded-md transition-colors shrink-0"
            >
              <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
                <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>

          {/* 오른쪽: 전체 액션 영역 (margin-left: auto 로 우측 정렬) */}
          <div className="ml-auto flex items-center h-10 shrink-0">
            {/* 전원 */}
            <div className="relative" ref={powerMenuRef}>
              <button
                onClick={() => setShowPowerMenu((v) => !v)}
                disabled={isTransitioning || vm.status === "DELETED"}
                className={`flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors ${
                  showPowerMenu ? "bg-gray-100" : ""
                }`}
              >
                <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                  <path d="M18.36 6.64a9 9 0 1 1-12.73 0"/><line x1="12" y1="2" x2="12" y2="12"/>
                </svg>
                전원
                <svg className={`w-3 h-3 text-gray-400 transition-transform shrink-0 ${showPowerMenu ? "rotate-180" : ""}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              {showPowerMenu && (
                <div className="absolute left-0 mt-1 min-w-full bg-white border border-gray-200 rounded-md shadow-md z-20 overflow-hidden">
                  {isRunning ? (
                    <>
                      <button onClick={() => { handlePower("STOP"); setShowPowerMenu(false); }} className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 whitespace-nowrap">정지</button>
                      <div className="h-px bg-gray-100 mx-2" />
                      <button onClick={() => { handlePower("REBOOT"); setShowPowerMenu(false); }} className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 whitespace-nowrap">재시작</button>
                    </>
                  ) : (
                    <button onClick={() => { handlePower("START"); setShowPowerMenu(false); }} className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 whitespace-nowrap">시작</button>
                  )}
                </div>
              )}
            </div>

            {/* 콘솔 */}
            <button
              onClick={() => router.push(`/instances/${id}/console`)}
              disabled={!isRunning}
              title={isRunning ? undefined : "VM이 실행 중일 때만 콘솔에 접속할 수 있어요"}
              className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
            >
              <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                <polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/>
              </svg>
              콘솔
            </button>

            {/* 파일 */}
            <button
              onClick={() => router.push(`/instances/${id}/files`)}
              disabled={!isRunning}
              title={isRunning ? undefined : "VM이 실행 중일 때만 파일 브라우저를 이용할 수 있어요"}
              className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
            >
              <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                <path d="M3 5a2 2 0 012-2h4l2 2h8a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V5z"/>
              </svg>
              파일
            </button>

            {/* Docker */}
            {showDocker && (
              <button
                onClick={() => router.push(`/instances/${id}/docker`)}
                disabled={!isRunning}
                title={isRunning ? undefined : "VM이 실행 중일 때만 Docker 관리를 이용할 수 있어요"}
                className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
              >
                <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                  <rect x="2" y="7" width="20" height="14" rx="2"/><path d="M6 7V5a2 2 0 012-2h8a2 2 0 012 2v2"/><line x1="8" y1="12" x2="16" y2="12"/>
                </svg>
                Docker
              </button>
            )}

            {(restVisibleAny || showMoreButton) && <div className="w-px h-5 bg-gray-200 shrink-0" />}

            {/* 배포 */}
            {showDeploy && (
              <button
                onClick={() => router.push(`/instances/${id}/deployments`)}
                disabled={!isRunning}
                title={isRunning ? undefined : "VM이 실행 중일 때만 배포를 이용할 수 있어요"}
                className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
              >
                <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                  <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>
                </svg>
                배포
              </button>
            )}

            {/* 백업 */}
            {showBackup && (
              <button
                onClick={() => router.push(`/instances/${id}/backups`)}
                disabled={!isRunning}
                title={isRunning ? undefined : "VM이 실행 중일 때만 DB 백업을 이용할 수 있어요"}
                className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
              >
                <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                  <ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3"/>
                </svg>
                백업
              </button>
            )}

            {/* 성능 */}
            {showPerformance && (
              <button
                onClick={() => router.push(`/instances/${id}/metrics`)}
                className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 transition-colors"
              >
                <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                  <rect x="18" y="3" width="4" height="18" rx="1"/><rect x="10" y="8" width="4" height="13" rx="1"/><rect x="2" y="13" width="4" height="8" rx="1"/>
                </svg>
                성능
              </button>
            )}

            {/* 스펙 변경 */}
            {showSpecChange && (
              <button
                onClick={() => setShowUpgradeModal(true)}
                className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 transition-colors"
              >
                <svg className="w-[15px] h-[15px] text-gray-500 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                  <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                </svg>
                스펙 변경
              </button>
            )}

            {/* 더보기 */}
            {showMoreButton && (
              <div className="relative" ref={moreMenuRef}>
                <button
                  onClick={() => setShowMoreMenu((v) => !v)}
                  className={`flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-gray-600 hover:bg-gray-50 transition-colors ${
                    showMoreMenu ? "bg-gray-100" : ""
                  }`}
                >
                  더보기
                  <svg className={`w-3 h-3 text-gray-400 transition-transform shrink-0 ${showMoreMenu ? "rotate-180" : ""}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {showMoreMenu && (
                  <div className="absolute right-0 mt-1 min-w-[160px] bg-white border border-gray-200 rounded-md shadow-md z-20 overflow-hidden">
                    {!showDocker && (
                      <button
                        onClick={() => { setShowMoreMenu(false); router.push(`/instances/${id}/docker`); }}
                        disabled={!isRunning}
                        className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 disabled:opacity-40 disabled:hover:bg-transparent whitespace-nowrap"
                      >
                        Docker
                      </button>
                    )}
                    {!showDeploy && (
                      <button
                        onClick={() => { setShowMoreMenu(false); router.push(`/instances/${id}/deployments`); }}
                        disabled={!isRunning}
                        className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 disabled:opacity-40 disabled:hover:bg-transparent whitespace-nowrap"
                      >
                        배포
                      </button>
                    )}
                    {!showBackup && (
                      <button
                        onClick={() => { setShowMoreMenu(false); router.push(`/instances/${id}/backups`); }}
                        disabled={!isRunning}
                        className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 disabled:opacity-40 disabled:hover:bg-transparent whitespace-nowrap"
                      >
                        백업
                      </button>
                    )}
                    {!showPerformance && (
                      <button
                        onClick={() => { setShowMoreMenu(false); router.push(`/instances/${id}/metrics`); }}
                        className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 whitespace-nowrap"
                      >
                        성능
                      </button>
                    )}
                    {!showSpecChange && (
                      <button
                        onClick={() => { setShowMoreMenu(false); setShowUpgradeModal(true); }}
                        className="w-full text-left text-sm px-4 py-2 hover:bg-gray-50 text-gray-700 whitespace-nowrap"
                      >
                        스펙 변경
                      </button>
                    )}
                    {toolbarDeleteCollapsed && (
                      <>
                        <div className="h-px bg-gray-100 mx-2 my-1" />
                        <button
                          onClick={() => { setShowMoreMenu(false); setConfirmDelete(true); }}
                          className="w-full text-left text-sm px-4 py-2 hover:bg-red-50 text-red-600 whitespace-nowrap"
                        >
                          삭제
                        </button>
                      </>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* 삭제 */}
            {!toolbarDeleteCollapsed && (
              <>
                <div className="w-px h-5 bg-gray-200 shrink-0" />
                <button
                  onClick={() => setConfirmDelete(true)}
                  className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap shrink-0 text-red-600 hover:bg-red-50 rounded-r-lg transition-colors"
                >
                  <svg className="w-[15px] h-[15px] shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
                    <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                  </svg>
                  삭제
                </button>
              </>
            )}
          </div>

          {/* 측정 전용 숨김 레이어 — w-0 + overflow-hidden으로 자체 오버플로우를 차단해
              부모/조상 요소의 scrollWidth에 영향을 주지 않으면서 자식 버튼의 실제 폭만 측정 */}
          <div className="absolute top-0 left-0 w-0 h-10 overflow-hidden invisible pointer-events-none flex items-center" aria-hidden="true">
            <button ref={(el) => { toolbarMeasureRefs.current.power = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              전원
              <svg className="w-3 h-3 shrink-0" viewBox="0 0 24 24" />
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.console = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              콘솔
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.files = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              파일
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.docker = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              Docker
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.deploy = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              배포
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.backup = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              백업
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.performance = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              성능
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.specChange = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              스펙 변경
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.more = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              더보기
              <svg className="w-3 h-3 shrink-0" viewBox="0 0 24 24" />
            </button>
            <button ref={(el) => { toolbarMeasureRefs.current.delete = el; }} tabIndex={-1} className="flex items-center gap-1.5 text-sm px-3.5 h-10 whitespace-nowrap">
              <svg className="w-[15px] h-[15px] shrink-0" viewBox="0 0 24 24" />
              삭제
            </button>
            <div ref={(el) => { toolbarMeasureRefs.current.divider = el; }} className="w-px h-5 bg-gray-200 shrink-0" />
          </div>
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

      {/* 2단 레이아웃 */}
      <div className="flex gap-4 items-start">
      {/* ── 좌측: SSH / 이메일 / 포트 ── */}
      <div className="flex-[11] min-w-0 space-y-3">

      {/* SSH 접속 정보 */}
      <div className="bg-white border border-gray-200/70 rounded-xl p-4 px-5">
        <p className="text-sm font-medium text-gray-900 mb-3">SSH 접속 정보</p>
        <table className="w-full text-[13px]">
          <tbody>
            <tr>
              <td className="text-gray-500 py-1.5 w-24">호스트네임</td>
              <td className="text-right font-mono text-gray-900">{vm.subdomain}.gamjabox.cloud</td>
            </tr>
            <tr>
              <td className="text-gray-500 py-1.5">사용자</td>
              <td className="text-right font-mono text-gray-900">ubuntu</td>
            </tr>
            <tr>
              <td className="text-gray-500 py-1.5">인증 방식</td>
              <td className="text-right text-gray-900">등록한 SSH 키</td>
            </tr>
          </tbody>
        </table>

        <button
          onClick={() => setAccordionOpen(!accordionOpen)}
          className="flex items-center justify-between w-full mt-3 pt-3 border-t border-gray-100 text-gray-500 hover:text-gray-700 transition-colors"
        >
          <span className="flex items-center gap-1.5 text-[13px]">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
            SSH 접속 방법 보기
          </span>
          <svg className={`w-3.5 h-3.5 transition-transform duration-200 ${accordionOpen ? "rotate-90" : ""}`} fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
            <path d="M9 18l6-6-6-6"/>
          </svg>
        </button>

        {accordionOpen && (
          <div className="mt-4 space-y-6 text-gray-700">

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
      <div className="bg-white border border-gray-200/70 rounded-xl p-4 px-5">
        <p className="text-sm font-medium text-gray-900 mb-3">SSH 접근 허용 이메일</p>
        <div className="mb-2">
          {sshEmails.length === 0 && (
            <p className="text-[13px] text-gray-400 py-1">허용된 이메일이 없습니다.</p>
          )}
          {sshEmails.map((email) => (
            <div key={email} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
              <span className="text-[13px] text-gray-800">{email}</span>
              <button
                onClick={() => handleRemoveSshEmail(email)}
                className="text-gray-400 hover:text-red-500 p-0.5"
                title="삭제"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          ))}
        </div>
        {sshEmails.length < 10 && (
          <form onSubmit={handleAddSshEmail} className="flex gap-2 mt-2">
            <input
              id="instance-ssh-email"
              name="instance-ssh-email"
              type="email"
              value={newSshEmail}
              onChange={(e) => setNewSshEmail(e.target.value)}
              placeholder="추가할 이메일"
              className="flex-1 h-8 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
            />
            <button
              type="submit"
              disabled={sshEmailLoading || !newSshEmail}
              className="h-8 px-4 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60"
            >
              추가
            </button>
          </form>
        )}
      </div>

      {/* 포트 관리 */}
      <div className="bg-white border border-gray-200/70 rounded-xl p-4 px-5">
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm font-medium text-gray-900">포트 노출</p>
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
                          id={`port-email-${port.id}`}
                          name={`port-email-${port.id}`}
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

      </div>{/* end left col */}

      {/* ── 우측: 협업 패널 (sticky) ── */}
      <div className="flex-[10] min-w-0">
        <div className="sticky top-0 bg-gray-50 rounded-xl p-4">
          {/* 패널 헤더 */}
          <div className="flex items-center justify-between mb-2.5">
            <span className="text-[15px] font-medium text-gray-900">협업</span>
            <button
              onClick={() => { setEditingCollab(undefined); setShowCollabWrite(true); }}
              className="text-[13px] px-3 h-[30px] bg-[#03C75A]/10 text-[#03C75A] rounded-md hover:bg-[#03C75A]/20 font-medium border-0"
              style={{ width: "auto" }}
            >
              + 작성
            </button>
          </div>
          {/* 필터 */}
          <div className="flex gap-1.5 mb-3.5">
            {([undefined, "NOTE", "NOTICE", "REQUEST"] as (CollaborationType | undefined)[]).map((t) => (
              <button
                key={t ?? "all"}
                onClick={() => setCollabTypeFilter(t)}
                style={{ width: "auto", padding: "0 12px", height: 28, fontSize: 12 }}
                className={`rounded-md transition-colors ${
                  collabTypeFilter === t
                    ? "bg-white border border-gray-300 text-gray-800"
                    : "bg-transparent border-0 text-gray-500 hover:text-gray-700"
                }`}
              >
                {t === undefined ? "전체" : t === "NOTE" ? "메모" : t === "NOTICE" ? "공지" : "요청"}
              </button>
            ))}
          </div>
          {/* 카드 목록 */}
          <div className="overflow-y-auto max-h-[calc(100vh-260px)]">
            {(() => {
              const filtered = collabTypeFilter
                ? collabItems.filter((i) => i.type === collabTypeFilter)
                : collabItems;
              return filtered.length === 0 ? (
                <p className="text-[13px] text-gray-400 py-10 text-center">협업 항목이 없습니다.</p>
              ) : (
                <div className="flex flex-col gap-2">
                  {filtered.map((item) => (
                    <CollaborationCard
                      key={item.id}
                      item={item}
                      accessToken={accessToken!}
                      isOwnerOrAdmin={vm.userId === profile?.userId}
                      currentUserId={profile?.userId}
                      onEdit={(i) => { setEditingCollab(i); setShowCollabWrite(true); }}
                      onUpdate={(updated) => setCollabItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)))}
                      onDelete={(deletedId) => setCollabItems((prev) => prev.filter((i) => i.id !== deletedId))}
                    />
                  ))}
                </div>
              );
            })()}
          </div>
        </div>
      </div>

      </div>{/* end 2-col */}

      {/* 협업 작성/수정 모달 */}
      {showCollabWrite && (
        <CollaborationWriteModal
          accessToken={accessToken!}
          scopeType="INSTANCE"
          scopeId={id}
          editing={editingCollab}
          onClose={() => { setShowCollabWrite(false); setEditingCollab(undefined); }}
          onSuccess={(item) => {
            if (editingCollab) {
              setCollabItems((prev) => prev.map((i) => (i.id === item.id ? item : i)));
              setEditingCollab(undefined);
            } else {
              setCollabItems((prev) => [item, ...prev]);
            }
            setShowCollabWrite(false);
          }}
        />
      )}

      {/* 포트 추가 모달 */}
      {showPortModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[420px]">
            <h2 className="text-base font-medium text-gray-900 mb-4">포트 추가</h2>
            <form onSubmit={handleAddPort} className="flex flex-col gap-3">
              <div>
                <label htmlFor="port-nickname" className="text-xs text-gray-500 block mb-1">닉네임</label>
                <input
                  id="port-nickname"
                  name="port-nickname"
                  type="text"
                  value={portForm.nickname}
                  onChange={(e) => setPortForm((f) => ({ ...f, nickname: e.target.value.toLowerCase() }))}
                  placeholder="예: myapp (소문자·숫자·하이픈, 최대 20자)"
                  maxLength={20}
                  pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
                  required
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                />
                <p className="text-[11px] text-gray-400 mt-1">
                  서브도메인: {portForm.customSubdomain
                    ? `${portForm.customSubdomain}.gamjabox.cloud`
                    : portForm.nickname
                      ? `${vm.subdomain}-${portForm.nickname}.gamjabox.cloud`
                      : "—"}
                </p>
              </div>
              <div className={profile?.planType !== "PRO" ? "opacity-50 pointer-events-none select-none" : ""}>
                <label htmlFor="port-custom-subdomain" className="text-xs text-gray-500 block mb-1">
                  커스텀 서브도메인
                  {profile?.planType !== "PRO"
                    ? <span className="ml-1.5 text-[10px] font-medium text-amber-600 bg-amber-50 border border-amber-200 px-1.5 py-0.5 rounded">PRO 전용</span>
                    : <span className="text-[10px] text-blue-500 ml-1">PRO</span>
                  }
                </label>
                <input
                  id="port-custom-subdomain"
                  name="port-custom-subdomain"
                  type="text"
                  value={portForm.customSubdomain}
                  onChange={(e) => handleCustomSubdomainChange(e.target.value)}
                  placeholder="예: myservice (선착순 점유, 미입력 시 자동 생성)"
                  maxLength={30}
                  pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
                  disabled={profile?.planType !== "PRO"}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
                />
                {portForm.customSubdomain && (
                  <p className={`text-[11px] mt-1 ${
                    subdomainCheck === "available" ? "text-green-600" :
                    subdomainCheck === "checking" ? "text-gray-400" :
                    "text-red-500"
                  }`}>
                    {subdomainCheck === "checking" && "확인 중..."}
                    {subdomainCheck === "available" && "✓ 사용 가능"}
                    {subdomainCheck === "taken" && "이미 사용 중인 서브도메인입니다"}
                    {subdomainCheck === "reserved" && "예약된 서브도메인입니다"}
                    {subdomainCheck === "pro-only" && "PRO 플랜 전용입니다"}
                  </p>
                )}
              </div>
              <div>
                <label htmlFor="port-number" className="text-xs text-gray-500 block mb-1">포트 번호</label>
                <input
                  id="port-number"
                  name="port-number"
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
                <label htmlFor="port-protocol" className="text-xs text-gray-500 block mb-1">프로토콜</label>
                <select
                  id="port-protocol"
                  name="port-protocol"
                  value={portForm.protocol}
                  onChange={(e) => setPortForm((f) => ({ ...f, protocol: e.target.value as "HTTP" | "TCP" }))}
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                >
                  <option value="HTTP">HTTP</option>
                  <option value="TCP">TCP</option>
                </select>
              </div>
              <div>
                <label htmlFor="port-visibility" className="text-xs text-gray-500 block mb-1">공개 설정</label>
                <select
                  id="port-visibility"
                  name="port-visibility"
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
                  <label htmlFor="port-initial-emails" className="text-xs text-gray-500 block mb-1">초기 허용 이메일 (쉼표로 구분, 미입력 시 본인 자동 추가)</label>
                  <input
                    id="port-initial-emails"
                    name="port-initial-emails"
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
                  disabled={portLoading || !portForm.port || !portForm.nickname || (!!portForm.customSubdomain && subdomainCheck !== "available")}
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
