import { Suspense } from "react";
import { AutoPreviewWorkspace } from "@/components/auto-preview/AutoPreviewWorkspace";
import { PageLoader } from "@/components/ui/loader";

export default function StandaloneAutoPreviewPage() {
  return (
    <Suspense fallback={<PageLoader label="Auto Preview 준비 중" />}>
      <AutoPreviewWorkspace />
    </Suspense>
  );
}
