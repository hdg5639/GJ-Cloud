"use client";

import { useEffect, useRef } from "react";
import type { VmStatusEvent } from "@/lib/types";
import { getExchangedToken } from "@/lib/api-client";

const MAX_RETRIES = 5;

export function useVmEvents(
  accessToken: string,
  onEvent: (event: VmStatusEvent) => void,
  enabled = true
) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!accessToken || !enabled) return;

    let es: EventSource | null = null;
    let closed = false;
    let retries = 0;

    function connect() {
      if (closed) return;

      getExchangedToken(accessToken, "vm-service")
        .then((vmToken) => {
          if (closed) return;

          const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/events/subscribe?token=${vmToken}`;
          es = new EventSource(url);

          es.addEventListener("VM_STATUS_CHANGED", (e) => {
            retries = 0;
            onEventRef.current(JSON.parse((e as MessageEvent).data));
          });

          es.onerror = () => {
            es?.close();
            es = null;
            if (!closed && retries < MAX_RETRIES) {
              retries++;
              setTimeout(connect, 5000);
            }
          };
        })
        .catch(() => {
          if (!closed && retries < MAX_RETRIES) {
            retries++;
            setTimeout(connect, 5000);
          }
        });
    }

    connect();

    return () => {
      closed = true;
      es?.close();
    };
  }, [accessToken, enabled]);
}
