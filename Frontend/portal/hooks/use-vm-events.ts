"use client";

import { useEffect } from "react";
import type { VmStatusEvent } from "@/lib/types";
import { getExchangedToken } from "@/lib/api-client";

export function useVmEvents(
  accessToken: string,
  onEvent: (event: VmStatusEvent) => void
) {
  useEffect(() => {
    if (!accessToken) return;

    let es: EventSource | null = null;

    getExchangedToken(accessToken, "vm-service")
      .then((vmToken) => {
        const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/events/subscribe?token=${vmToken}`;
        es = new EventSource(url, { withCredentials: true });

        es.addEventListener("VM_STATUS_CHANGED", (e) => {
          onEvent(JSON.parse((e as MessageEvent).data));
        });

        es.onerror = () => es?.close();
      })
      .catch(() => {});

    return () => es?.close();
  }, [accessToken, onEvent]);
}
