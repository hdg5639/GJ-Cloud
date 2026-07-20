"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { PageLoader } from "@/components/ui/loader";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3">
          <button onClick={() => router.back()} className="p-2 hover:bg-[#f2f6f3] rounded-lg transition-colors" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-bold">콘솔</h1>
          <StatusBadge tone={status === "error" ? "off" : statusTone} className={status === "error" ? "bg-[#fdf4f4] text-danger" : undefined}>
            {statusLabel}
          </StatusBadge>
        </div>
        {(status === "closed" || status === "error") && (
          <Button size="small" onClick={connect}>
            다시 연결
          </Button>
        )}
      </div>

      {errorMessage && (
        <div className="bg-[#fdf4f4] border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm">
          {errorMessage}
        </div>
      )}

      <div className="flex-1 rounded-panel overflow-hidden bg-[#0f172a] p-2">
        <div ref={containerRef} className="w-full h-full" />
      </div>
    </div>
  );
}
