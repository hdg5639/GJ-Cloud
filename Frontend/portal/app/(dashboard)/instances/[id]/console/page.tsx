"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { PageLoader } from "@/components/ui/loader";
import { StatusBadge } from "@/components/ui/badge";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";
import { InstanceToolbar } from "@/components/ui/instance-toolbar";
import type { Terminal } from "@xterm/xterm";
import type { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";

type ConnectionStatus = "connecting" | "connected" | "closed" | "error" | "paused";

export default function ConsolePage() {
  return <TerminalConsole />;
}

export function TerminalConsole({ systemWorker = false }: { systemWorker?: boolean }) {
  const params = useParams();
  const router = useRouter();
  const vmId = systemWorker ? "" : params.id as string;
  const { accessToken } = useAuth();
  const authenticated = Boolean(accessToken);
  const accessTokenRef = useRef(accessToken);
  const connectionGenerationRef = useRef(0);
  const activeRef = useRef(false);
  const sessionRef = useRef<{ key: string; id: string } | null>(null);
  const connectingRef = useRef(false);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCountRef = useRef(0);
  const connectRef = useRef<() => Promise<void>>(async () => {});

  // 토큰은 새 연결의 티켓 발급에만 사용한다. 갱신이 기존 SSH/PTY를 종료하지 않게 한다.
  useEffect(() => {
    accessTokenRef.current = accessToken;
  }, [accessToken]);

  const containerRef = useRef<HTMLDivElement>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const termRef = useRef<Terminal | null>(null);
  const fitRef = useRef<FitAddon | null>(null);

  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  const disconnectTransport = useCallback(() => {
    connectionGenerationRef.current += 1;
    connectingRef.current = false;
    if (heartbeatRef.current) clearInterval(heartbeatRef.current);
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
    heartbeatRef.current = null;
    retryTimerRef.current = null;
    const previousSocket = wsRef.current;
    wsRef.current = null;
    previousSocket?.close(1000, "view inactive");
  }, []);

  const teardown = useCallback(() => {
    disconnectTransport();
    termRef.current?.dispose();
    termRef.current = null;
    fitRef.current = null;
    if (containerRef.current) containerRef.current.innerHTML = "";
  }, [disconnectTransport]);

  const connect = useCallback(async () => {
    const token = accessTokenRef.current;
    if (!token || !containerRef.current || !activeRef.current || connectingRef.current) return;
    teardown();
    connectingRef.current = true;
    const generation = connectionGenerationRef.current;
    const isCurrent = () => generation === connectionGenerationRef.current;
    setStatus("connecting");
    setErrorMessage(null);
    const retry = () => {
      connectingRef.current = false;
      if (heartbeatRef.current) clearInterval(heartbeatRef.current);
      heartbeatRef.current = null;
      if (!isCurrent() || !activeRef.current) return;
      if (retryCountRef.current >= 5) {
        setStatus("error");
        setErrorMessage("콘솔 연결을 복구하지 못했습니다. 다시 연결을 눌러주세요.");
        return;
      }
      setStatus("connecting");
      const delay = Math.min(1000 * 2 ** retryCountRef.current++, 15000);
      retryTimerRef.current = setTimeout(() => void connectRef.current(), delay);
    };

    try {
      const [{ Terminal }, { FitAddon }] = await Promise.all([
        import("@xterm/xterm"),
        import("@xterm/addon-fit"),
      ]);

      if (!isCurrent() || !containerRef.current) return;

      const term = new Terminal({
        cursorBlink: true,
        scrollback: 5000,
        fontSize: 13,
        fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
        theme: { background: "#0f172a" },
      });
      const fitAddon = new FitAddon();
      term.loadAddon(fitAddon);
      term.open(containerRef.current);
      fitAddon.fit();
      termRef.current = term;
      fitRef.current = fitAddon;

      // Shift + 방향키로 터미널 텍스트를 선택하는 키보드 선택 컨트롤러.
      // 터미널은 에디터처럼 자유 캐럿이 없으므로 앵커/캐럿을 버퍼 절대 좌표로 직접
      // 관리하고, 공개 API select(col, row, length)로 선형 선택을 만든다. length가
      // cols를 넘으면 다음 행으로 이어지는 xterm 동작을 이용해 여러 줄도 선택된다.
      type SelCell = { x: number; y: number };
      let selAnchor: SelCell | null = null;
      let selCaret: SelCell | null = null;

      // 커서의 버퍼 절대 위치. cursorY는 baseY 기준 상대값이라 baseY를 더한다.
      const cursorCell = (): SelCell => {
        const buf = term.buffer.active;
        return { x: buf.cursorX, y: buf.baseY + buf.cursorY };
      };

      const applySelection = () => {
        if (!selAnchor || !selCaret) return;
        const cols = term.cols;
        const forward =
          selAnchor.y < selCaret.y || (selAnchor.y === selCaret.y && selAnchor.x <= selCaret.x);
        const start = forward ? selAnchor : selCaret;
        const end = forward ? selCaret : selAnchor;
        const length = (end.y - start.y) * cols + (end.x - start.x);
        if (length <= 0) {
          term.clearSelection();
          return;
        }
        term.select(start.x, start.y, length);
      };

      const ensureCaretVisible = () => {
        if (!selCaret) return;
        const top = term.buffer.active.viewportY;
        if (selCaret.y < top) term.scrollToLine(selCaret.y);
        else if (selCaret.y > top + term.rows - 1) term.scrollToLine(selCaret.y - term.rows + 1);
      };

      const clearKeyboardSelection = () => {
        selAnchor = null;
        selCaret = null;
      };

      type NavDir = "left" | "right" | "up" | "down" | "home" | "end";
      const extendSelection = (dir: NavDir) => {
        const cols = term.cols;
        const lastRow = term.buffer.active.length - 1;
        if (!selCaret || !selAnchor) {
          const origin = cursorCell();
          selAnchor = { ...origin };
          selCaret = { ...origin };
        }
        const c = selCaret;
        switch (dir) {
          case "left":
            if (c.x > 0) c.x -= 1;
            else if (c.y > 0) {
              c.y -= 1;
              c.x = cols - 1;
            }
            break;
          case "right":
            if (c.x < cols - 1) c.x += 1;
            else if (c.y < lastRow) {
              c.y += 1;
              c.x = 0;
            }
            break;
          case "up":
            if (c.y > 0) c.y -= 1;
            break;
          case "down":
            if (c.y < lastRow) c.y += 1;
            break;
          case "home":
            c.x = 0;
            break;
          case "end":
            c.x = cols; // 줄 끝 경계까지 포함
            break;
        }
        ensureCaretVisible();
        applySelection();
      };

      const navMap: Record<string, NavDir> = {
        ArrowLeft: "left",
        ArrowRight: "right",
        ArrowUp: "up",
        ArrowDown: "down",
        Home: "home",
        End: "end",
      };

      // Windows/Linux의 Ctrl+C/Ctrl+V는 xterm이 PTY 입력으로 보내버려 동작하지 않으므로
      // 직접 클립보드에 연결한다. 복사는 Cmd+C(맥)도 함께 처리한다 — 키보드로 만든
      // 선택은 아래 정리 블록이 먼저 지워버려 xterm 네이티브 copy 이벤트가 못 읽기 때문.
      // 붙여넣기는 맥 Cmd+V의 네이티브 경로(clipboard read 권한 프롬프트 방지)를 유지한다.
      term.attachCustomKeyEventHandler((event) => {
        if (event.type !== "keydown") return true;

        // 단독 수식키는 무시(Shift만 눌렀다고 진행 중인 선택을 지우지 않도록).
        if (["Shift", "Control", "Alt", "Meta"].includes(event.key)) return true;

        // Shift + 방향키/Home/End: 키보드로 텍스트 선택 (맥·윈도우 공통).
        const nav = navMap[event.key];
        if (nav && event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey) {
          extendSelection(nav);
          event.preventDefault();
          return false;
        }

        // 복사/붙여넣기. 키보드로 만든 선택도 여기서 그대로 복사된다.
        if (event.ctrlKey || event.metaKey) {
          const key = event.key.toLowerCase();
          // 복사: Ctrl+C(윈도우/리눅스)와 Cmd+C(맥) 모두. 선택이 있을 때만 복사(없으면
          // Ctrl+C는 SIGINT 전달), Ctrl+Shift+C는 명시적 복사.
          if (key === "c" && (event.shiftKey || term.hasSelection())) {
            const selection = term.getSelection();
            if (selection) {
              void navigator.clipboard?.writeText(selection).catch(() => {});
              event.preventDefault();
              return false;
            }
          }
          // 붙여넣기: Windows/Linux는 Ctrl+V를 가로채 clipboard.readText로 넣는다.
          // 맥 Cmd+V는 네이티브 붙여넣기에 맡긴다.
          if (event.ctrlKey && key === "v") {
            void navigator.clipboard
              ?.readText()
              .then((text) => {
                if (text) term.paste(text);
              })
              .catch(() => {});
            event.preventDefault();
            return false;
          }
        }

        // 그 외 키 입력이 오면 진행 중인 키보드 선택을 정리한다.
        // Escape는 선택만 취소하고 PTY로 보내지 않는다.
        if (selCaret) {
          clearKeyboardSelection();
          term.clearSelection();
          if (event.key === "Escape") {
            event.preventDefault();
            return false;
          }
        }

        return true;
      });

      // 티켓은 일회용(30초 TTL, Redis GETDEL) — 발급 직후 바로 WS 핸드셰이크에 사용해야 함
      const ticketResponse = systemWorker
        ? await api.admin.systemWorker.consoleTicket(token)
        : { ...(await api.ops.issueTerminalTicket(token, vmId)), connectionId: vmId };
      if (!isCurrent()) return;
      const wsBase = process.env.NEXT_PUBLIC_OPS_API!.replace(/^http/, "ws");
      const storageKey = `terminal-session:${systemWorker ? "system-worker" : vmId}`;
      let sessionId: string | null = sessionRef.current?.key === storageKey ? sessionRef.current.id : null;
      try { sessionId ??= sessionStorage.getItem(storageKey); } catch { /* storage can be disabled */ }
      if (!sessionId || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(sessionId)) {
        sessionId = crypto.randomUUID();
        try { sessionStorage.setItem(storageKey, sessionId); } catch { /* reconnect still works in this mount */ }
      }
      sessionRef.current = { key: storageKey, id: sessionId };
      const ws = new WebSocket(`${wsBase}/ws/terminal/${ticketResponse.connectionId}?ticket=${encodeURIComponent(ticketResponse.ticket)}&sessionId=${sessionId}`);
      ws.binaryType = "arraybuffer";
      wsRef.current = ws;
      let lastReceived = Date.now();
      let ready = false;

      ws.onopen = () => {
        if (!isCurrent()) return;
        ws.send(JSON.stringify({ type: "resize", cols: term.cols, rows: term.rows }));
        heartbeatRef.current = setInterval(() => {
          if (!isCurrent() || ws.readyState !== WebSocket.OPEN) return;
          if (Date.now() - lastReceived > 60000) {
            ws.close(4000, "heartbeat timeout");
            return;
          }
          ws.send(JSON.stringify({ type: "heartbeat" }));
        }, 20000);
      };
      ws.onmessage = (event) => {
        if (!isCurrent()) return;
        lastReceived = Date.now();
        if (event.data instanceof ArrayBuffer) {
          term.write(new Uint8Array(event.data));
          return;
        }
        let control: { type?: string; resumed?: boolean; truncated?: boolean } | null = null;
        try { control = JSON.parse(event.data as string); } catch { /* older servers send raw text */ }
        if (control?.type === "attached") {
          term.reset();
          setSessionNotice(control.resumed
            ? control.truncated ? "기존 셸에 다시 연결했습니다. 최근 출력부터 복구했습니다." : "기존 셸과 이전 출력을 복구했습니다."
            : "새 셸이 시작되었습니다. 창을 떠나도 세션은 30분간 보관됩니다.");
          return;
        }
        if (control?.type === "heartbeat") return;
        if (control?.type === "ready" || !ready) {
          ready = true;
          connectingRef.current = false;
          retryCountRef.current = 0;
          setStatus("connected");
          setErrorMessage(null);
          term.focus();
        }
        if (control?.type !== "ready") term.write(event.data as string);
      };
      ws.onclose = (event) => {
        if (!isCurrent()) return;
        wsRef.current = null;
        connectingRef.current = false;
        if (heartbeatRef.current) clearInterval(heartbeatRef.current);
        heartbeatRef.current = null;
        const finished = ["idle timeout", "shell ended", "session expired", "replaced"].includes(event.reason);
        if (!finished && event.code !== 1013) {
          retry();
          return;
        }
        setStatus("closed");
        setErrorMessage(event.code === 1013
          ? "보관 중인 콘솔 세션이 너무 많습니다. 다른 콘솔에서 exit로 셸을 종료한 뒤 다시 연결해주세요."
          : event.reason === "replaced" ? "다른 연결에서 이 콘솔을 열었습니다."
          : "셸이 종료되었거나 보관 시간이 만료되었습니다. 다시 연결하면 새 셸이 시작됩니다.");
      };
      ws.onerror = () => {
        // onclose owns recovery so error + close do not schedule duplicate connections.
        if (isCurrent()) setErrorMessage("연결을 확인하고 있습니다…");
      };

      // 키 입력도 JSON 래핑 없이 원문 그대로 전송 (resize 제어 메시지만 예외)
      term.onData((data) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(data);
        }
      });
    } catch (err) {
      if (!isCurrent()) return;
      setErrorMessage(err instanceof Error ? err.message : "콘솔 연결에 실패했습니다.");
      retry();
    }
  }, [vmId, systemWorker, teardown]);

  useEffect(() => {
    if (!authenticated) return;
    connectRef.current = connect;
    const pause = () => {
      activeRef.current = false;
      disconnectTransport();
      setStatus("paused");
    };
    const resume = () => {
      if (document.visibilityState !== "visible" || !document.hasFocus()) return;
      activeRef.current = true;
      if (!wsRef.current && !connectingRef.current) {
        retryCountRef.current = 0;
        void connect();
      }
    };
    const visibility = () => document.visibilityState === "hidden" ? pause() : resume();
    const connectTimer = setTimeout(resume, 0);
    window.addEventListener("blur", pause);
    window.addEventListener("focus", resume);
    window.addEventListener("pagehide", pause);
    window.addEventListener("pageshow", resume);
    window.addEventListener("online", resume);
    document.addEventListener("visibilitychange", visibility);

    function handleResize() {
      const fitAddon = fitRef.current;
      const term = termRef.current;
      const ws = wsRef.current;
      if (!fitAddon || !term) return;
      fitAddon.fit();
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "resize", cols: term.cols, rows: term.rows }));
      }
    }
    window.addEventListener("resize", handleResize);
    const observer = new ResizeObserver(handleResize);
    if (containerRef.current) observer.observe(containerRef.current);

    return () => {
      clearTimeout(connectTimer);
      activeRef.current = false;
      window.removeEventListener("blur", pause);
      window.removeEventListener("focus", resume);
      window.removeEventListener("pagehide", pause);
      window.removeEventListener("pageshow", resume);
      window.removeEventListener("online", resume);
      document.removeEventListener("visibilitychange", visibility);
      window.removeEventListener("resize", handleResize);
      observer.disconnect();
      teardown();
    };
  }, [authenticated, connect, teardown, disconnectTransport]);

  if (!accessToken) return <PageLoader />;

  const statusTone = status === "connected" ? "ok" : "off";
  const statusLabel =
    status === "paused" ? "일시 중지" : status === "connecting" ? "연결 중" : status === "connected" ? "연결됨" : status === "closed" ? "연결 종료" : "오류";

  return (
    <div className="flex min-h-[380px] flex-col h-[calc(100dvh-130px)] lg:h-[calc(100dvh-100px)]">
      {!systemWorker && <InstanceSectionNav vmId={vmId} />}
      <InstanceToolbar>
        <div className="flex min-h-10 min-w-0 flex-wrap items-center gap-2.5 pl-4 pr-3.5">
          <button onClick={() => router.back()} className="flex h-7 w-7 items-center justify-center rounded-md text-muted-soft transition-colors hover:bg-white/[0.06] hover:text-muted" aria-label="뒤로가기">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-[15px] font-bold whitespace-nowrap">{systemWorker ? "Auto Preview Worker 콘솔" : "콘솔"}</h1>
          <StatusBadge tone={status === "error" ? "off" : statusTone} className={status === "error" ? "bg-danger/10 text-danger" : undefined}>
            {statusLabel}
          </StatusBadge>
        </div>
        <div className="ml-auto flex h-10 shrink-0 items-center">
          <button onClick={() => { retryCountRef.current = 0; void connect(); }} disabled={status === "connecting" || status === "paused"} aria-label="콘솔 다시 연결" title="기존 콘솔 다시 연결" className="flex h-10 w-10 shrink-0 items-center justify-center text-muted transition-colors hover:bg-white/[0.06] rounded-r-panel disabled:cursor-not-allowed disabled:opacity-40">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </InstanceToolbar>

      {sessionNotice && !errorMessage && (
        <p className="mb-2 px-1 text-xs text-muted-soft" role="status">{sessionNotice}</p>
      )}
      {errorMessage && (
        <div className="bg-danger/10 border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm">
          {errorMessage}
        </div>
      )}

      <div className="min-h-0 flex-1 rounded-panel overflow-hidden bg-[#0f172a] p-2">
        <div ref={containerRef} className="w-full h-full" />
      </div>
    </div>
  );
}
