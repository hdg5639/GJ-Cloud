"use client";

import { useEffect, useRef, useCallback } from "react";
import type { VmStatusEvent } from "@/lib/types";
import { api } from "@/lib/api-client";

const MAX_RETRIES = 5;
const AUTO_CLOSE_MS = 99_000;

export function useVmEvents(
  accessToken: string,
  onEvent: (event: VmStatusEvent) => void,
  enabled = true,
  onRefreshToken?: () => Promise<string | null>
): { reconnect: () => void } {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const accessTokenRef = useRef(accessToken);
  accessTokenRef.current = accessToken;

  const onRefreshRef = useRef(onRefreshToken);
  onRefreshRef.current = onRefreshToken;

  const reconnectRef = useRef<() => void>(() => {});

  useEffect(() => {
    if (!accessToken || !enabled) return;

    let es: EventSource | null = null;
    let closed = false;
    let retries = 0;
    let autoCloseTimer: ReturnType<typeof setTimeout> | null = null;

    function closeEs() {
      if (autoCloseTimer) { clearTimeout(autoCloseTimer); autoCloseTimer = null; }
      es?.close();
      es = null;
    }

    async function connect() {
      if (closed) return;

      try {
        const currentToken = accessTokenRef.current;
        const { ticket } = await api.vm.issueEventsTicket(currentToken);
        if (closed) return;

        const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/events/subscribe?ticket=${ticket}`;
        es = new EventSource(url);

        autoCloseTimer = setTimeout(() => {
          es?.close();
          es = null;
          autoCloseTimer = null;
        }, AUTO_CLOSE_MS);

        es.addEventListener("VM_STATUS_CHANGED", (e) => {
          retries = 0;
          onEventRef.current(JSON.parse((e as MessageEvent).data));
        });

        es.onerror = async () => {
          closeEs();
          if (closed || retries >= MAX_RETRIES) return;
          retries++;

          if (onRefreshRef.current) {
            const newToken = await onRefreshRef.current().catch(() => null);
            if (!closed) setTimeout(connect, newToken ? 1000 : 5000);
          } else {
            if (!closed) setTimeout(connect, 5000);
          }
        };
      } catch {
        if (closed || retries >= MAX_RETRIES) return;
        retries++;
        setTimeout(connect, 5000);
      }
    }

    reconnectRef.current = () => {
      closeEs();
      retries = 0;
      connect();
    };

    connect();

    return () => {
      closed = true;
      reconnectRef.current = () => {};
      closeEs();
    };
  }, [accessToken, enabled]);

  const reconnect = useCallback(() => reconnectRef.current(), []);

  return { reconnect };
}
