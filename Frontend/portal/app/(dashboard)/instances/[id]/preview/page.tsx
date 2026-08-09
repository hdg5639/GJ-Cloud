"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { AutoPreviewWorkspace } from "@/components/auto-preview/AutoPreviewWorkspace";
import { PageLoader } from "@/components/ui/loader";

export default function InstanceAutoPreviewPage() {
  const vmId = useParams().id as string;

  return (
    <Suspense fallback={<PageLoader label="Auto Preview 준비 중" />}>
      <AutoPreviewWorkspace fixedVmId={vmId} />
    </Suspense>
  );
}
