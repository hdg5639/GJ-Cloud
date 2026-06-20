"use client";

import { useEffect, useRef } from "react";
import type { VmStatusEvent } from "@/lib/types";
import { getExchangedToken } from "@/lib/api-client";

export function useVmEvents(
  accessToken: string,
  onEvent: (event: VmStatusEvent) => void
) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!accessToken) return;

    let es: EventSource | null = null;
    let closed = false;

    function connect() {
      if (closed) return;

      getExchangedToken(accessToken, "vm-service")
        .then((vmToken) => {
          if (closed) return;

          const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/events/subscribe?token=${vmToken}`;
          es = new EventSource(url);

          es.addEventListener("VM_STATUS_CHANGED", (e) => {
            onEventRef.current(JSON.parse((e as MessageEvent).data));
          });

          es.onerror = () => {
            es?.close();
            es = null;
            if (!closed) {
              setTimeout(connect, 5000);
            }
          };
        })
        .catch(() => {
          if (!closed) {
            setTimeout(connect, 5000);
          }
        });
    }

    connect();

    return () => {
      closed = true;
      es?.close();
    };
  }, [accessToken]);
}
