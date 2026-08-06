"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { PageLoader } from "@/components/ui/loader";
import { StatusBadge } from "@/components/ui/badge";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";
import type { Terminal } from "@xterm/xterm";
import type { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";

type ConnectionStatus = "connecting" | "connected" | "closed" | "error";

export default function ConsolePage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const containerRef = useRef<HTMLDivElement>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const termRef = useRef<Terminal | null>(null);
  const fitRef = useRef<FitAddon | null>(null);

  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const teardown = useCallback(() => {
    wsRef.current?.close();
    wsRef.current = null;
    termRef.current?.dispose();
    termRef.current = null;
    fitRef.current = null;
    if (containerRef.current) containerRef.current.innerHTML = "";
  }, []);

  const connect = useCallback(async () => {
    if (!accessToken || !containerRef.current) return;
    teardown();
    setStatus("connecting");
    setErrorMessage(null);

    try {
      const [{ Terminal }, { FitAddon }] = await Promise.all([
        import("@xterm/xterm"),
        import("@xterm/addon-fit"),
      ]);

      const term = new Terminal({
        cursorBlink: true,
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
      const { ticket } = await api.ops.issueTerminalTicket(accessToken, vmId);
      const wsBase = process.env.NEXT_PUBLIC_OPS_API!.replace(/^http/, "ws");
      const ws = new WebSocket(`${wsBase}/ws/terminal/${vmId}?ticket=${ticket}`);
      wsRef.current = ws;

      ws.onopen = () => {
        setStatus("connected");
        // 서버는 접속 직후 80x24로 PTY를 고정하므로, 실제 터미널 크기로 즉시 맞춰줘야 함
        ws.send(JSON.stringify({ type: "resize", cols: term.cols, rows: term.rows }));
        term.focus();
      };
      // 서버는 JSON 래핑 없이 SSH stdout 원문을 그대로 TextMessage로 보냄
      ws.onmessage = (event) => {
        term.write(event.data as string);
      };
      ws.onclose = () => setStatus("closed");
      ws.onerror = () => {
        setStatus("error");
        setErrorMessage("연결 중 오류가 발생했습니다.");
      };

      // 키 입력도 JSON 래핑 없이 원문 그대로 전송 (resize 제어 메시지만 예외)
      term.onData((data) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(data);
        }
      });
    } catch (err) {
      console.error("콘솔 연결 실패:", err);
      setStatus("error");
      setErrorMessage(err instanceof Error ? err.message : "콘솔 연결에 실패했습니다.");
    }
  }, [accessToken, vmId, teardown]);

  useEffect(() => {
    connect();

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

    return () => {
      window.removeEventListener("resize", handleResize);
      teardown();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vmId, accessToken]);

  if (!accessToken) return <PageLoader />;

  const statusTone = status === "connected" ? "ok" : "off";
  const statusLabel =
    status === "connecting" ? "연결 중" : status === "connected" ? "연결됨" : status === "closed" ? "연결 종료" : "오류";

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
          <h1 className="text-[15px] font-bold whitespace-nowrap">콘솔</h1>
          <StatusBadge tone={status === "error" ? "off" : statusTone} className={status === "error" ? "bg-danger/10 text-danger" : undefined}>
            {statusLabel}
          </StatusBadge>
        </div>
        <div className="ml-auto flex h-10 shrink-0 items-center">
          <button onClick={connect} title="다시 연결" className="flex h-10 w-10 shrink-0 items-center justify-center text-muted transition-colors hover:bg-white/[0.06] rounded-r-panel">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {errorMessage && (
        <div className="bg-danger/10 border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm">
          {errorMessage}
        </div>
      )}

      <div className="flex-1 rounded-panel overflow-hidden bg-[#0f172a] p-2">
        <div ref={containerRef} className="w-full h-full" />
      </div>
    </div>
  );
}
