"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

export default function RootPage() {
  const { accessToken, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;
    if (accessToken) {
      router.replace("/instances");
    } else {
      router.replace("/login");
    }
  }, [accessToken, isLoading, router]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/gamjabox-loader.svg" alt="GamjaBox" width={96} height={96} />
    </div>
  );
}
