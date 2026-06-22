"use client";

import { useEffect, useRef } from "react";
import type { VmStatusEvent } from "@/lib/types";
import { getExchangedToken, invalidateExchangeCache } from "@/lib/api-client";

const MAX_RETRIES = 5;

export function useVmEvents(
  accessToken: string,
  onEvent: (event: VmStatusEvent) => void,
  enabled = true,
  onRefreshToken?: () => Promise<string | null>
) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const accessTokenRef = useRef(accessToken);
  accessTokenRef.current = accessToken;

  const onRefreshRef = useRef(onRefreshToken);
  onRefreshRef.current = onRefreshToken;

  useEffect(() => {
    if (!accessToken || !enabled) return;

    let es: EventSource | null = null;
    let closed = false;
    let retries = 0;

    async function connect() {
      if (closed) return;

      try {
        const currentToken = accessTokenRef.current;
        const vmToken = await getExchangedToken(currentToken, "vm-service");
        if (closed) return;

        const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/events/subscribe?token=${vmToken}`;
        es = new EventSource(url);

        es.addEventListener("VM_STATUS_CHANGED", (e) => {
          retries = 0;
          onEventRef.current(JSON.parse((e as MessageEvent).data));
        });

        es.onerror = async () => {
          es?.close();
          es = null;
          if (closed || retries >= MAX_RETRIES) return;
          retries++;

          // 만료된 캐시 날리고, 필요 시 access token 갱신 시도
          const tok = accessTokenRef.current;
          invalidateExchangeCache(tok);

          if (onRefreshRef.current) {
            const newToken = await onRefreshRef.current().catch(() => null);
            // 새 토큰이 있으면 accessTokenRef는 useAuth가 state 갱신 → 다음 effect에서 반영
            // 짧은 delay 후 재연결
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

    connect();

    return () => {
      closed = true;
      es?.close();
    };
  }, [accessToken, enabled]);
}
