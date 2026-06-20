"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useEffect, useState } from "react";
import type { UsageResponse } from "@/lib/types";

const NAV_ITEMS = [
  { href: "/instances", label: "인스턴스" },
  { href: "/ssh-keys", label: "SSH 키" },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, isLoading, logout } = useAuth();
  const [usage, setUsage] = useState<UsageResponse | null>(null);

  useEffect(() => {
    if (!isLoading && !accessToken) {
      router.replace("/login");
    }
  }, [accessToken, isLoading, router]);

  useEffect(() => {
    if (!accessToken) return;
    api.user.usage(accessToken).then(setUsage).catch(() => {});
  }, [accessToken]);

  async function handleLogout() {
    if (!accessToken) return;
    try {
      await api.auth.logout(accessToken);
    } finally {
      logout();
      router.push("/login");
    }
  }

  if (isLoading) return null;

  return (
    <div className="flex min-h-screen">
      <aside className="w-[200px] bg-gray-50 border-r border-gray-200 py-5 flex flex-col">
        <div className="flex items-center gap-2 px-5 mb-6">
          <div className="w-6 h-6 rounded-md bg-[#03C75A] flex items-center justify-center">
            <span className="text-white text-xs font-medium">G</span>
          </div>
          <span className="font-medium text-sm text-gray-900">gamjabox</span>
        </div>

        <nav className="px-3 flex flex-col gap-0.5 flex-1">
          {NAV_ITEMS.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm ${
                  active ? "bg-[#03C75A]/10 text-[#03C75A] font-medium" : "text-gray-600 hover:bg-gray-100"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* 플랜/사용량 */}
        {usage && (
          <div className="mx-3 mb-3 px-3 py-3 bg-white border border-gray-200 rounded-lg">
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[11px] text-gray-500">플랜</span>
              <span className="text-[11px] font-medium text-gray-900">{usage.planType}</span>
            </div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[11px] text-gray-500">인스턴스</span>
              <span className="text-[11px] font-medium text-gray-900">{usage.currentVmCount} / {usage.maxVmCount}</span>
            </div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[11px] text-gray-500">vCPU 한도</span>
              <span className="text-[11px] font-medium text-gray-900">{usage.vCpuLimit} core</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] text-gray-500">RAM 한도</span>
              <span className="text-[11px] font-medium text-gray-900">{usage.ramGbLimit}GB</span>
            </div>
          </div>
        )}

        <div className="px-3">
          <button
            onClick={handleLogout}
            className="w-full flex items-center px-3 py-2 rounded-md text-sm text-gray-500 hover:bg-gray-100"
          >
            로그아웃
          </button>
        </div>
      </aside>

      <main className="flex-1 p-6 overflow-auto">{children}</main>
    </div>
  );
}
