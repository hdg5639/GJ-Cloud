import { Suspense } from "react";
import { PageLoader } from "@/components/ui/loader";
import GithubCallbackClient from "./github-callback-client";

export default function GithubCallbackPage() {
  return (
    <Suspense fallback={<PageLoader label="GitHub 저장소 연결 중" />}>
      <GithubCallbackClient />
    </Suspense>
  );
}
