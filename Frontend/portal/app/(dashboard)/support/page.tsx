import { Suspense } from "react";
import { PageLoader } from "@/components/ui/loader";
import { SupportCenter } from "@/components/support/SupportCenter";

export default function SupportPage() {
  return (
    <Suspense fallback={<PageLoader />}>
      <SupportCenter />
    </Suspense>
  );
}
