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

  return null;
}
