"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useEffect, useState } from "react";
import type { UsageResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";

const NAV_ITEMS = [
  { href: "/instances", icon: "▣", label: "인스턴스" },
  { href: "/organizations", icon: "◫", label: "협업" },
  { href: "/ssh-keys", icon: "⌘", label: "SSH 키" },
  { href: "/settings", icon: "⚙", label: "설정" },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, user, isLoading, logout } = useAuth();
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
    <div className="flex h-screen overflow-hidden bg-background">
      <aside className="sticky top-0 flex h-screen w-[238px] shrink-0 flex-col gap-[22px] overflow-y-auto border-r border-line bg-panel px-5 pb-[18px] pt-[26px]">
        <div className="flex items-center gap-[11px] px-[5px]">
          <div
            className="grid h-9 w-9 place-items-center rounded-[11px] font-black text-white"
            style={{ backgroundImage: "linear-gradient(135deg, #12ce70, #08a34f)" }}
          >
            G
          </div>
          <div>
            <strong className="block">gamjabox</strong>
            <small className="mt-0.5 block text-muted-soft">Cloud Console</small>
          </div>
        </div>

        <nav className="grid gap-1.5">
          {NAV_ITEMS.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex w-full items-center gap-2.5 rounded-[10px] px-3 py-[11px] text-left text-sm font-bold ${
                  active ? "bg-soft text-brand-strong" : "text-[#445248] hover:bg-[#f2f6f3]"
                }`}
              >
                <span aria-hidden>{item.icon}</span>
                {item.label}
              </Link>
            );
          })}
        </nav>

        {usage && (
          <div className="mt-auto rounded-[14px] border border-line bg-panel p-4">
            <div className="flex items-center justify-between">
              <div>
                <span className="block text-[11px] font-extrabold tracking-[.11em] text-muted-soft">CURRENT PLAN</span>
                <strong className="text-base">{usage.planType}</strong>
              </div>
              <Badge>{usage.planType}</Badge>
            </div>

            <UsageBar label="인스턴스 (FREE)" current={usage.myFreeCount} max={usage.maxFreeVmCount} />
            <UsageBar label="인스턴스 (PRO)" current={usage.myProCount} max={usage.maxProVmCount} />
            <UsageBar label="vCPU 한도" current={usage.vCpuLimit} max={usage.vCpuLimit} suffix=" core" hideBar />
            <UsageBar label="RAM 한도" current={usage.ramGbLimit} max={usage.ramGbLimit} suffix=" GB" hideBar />

            <div className="mt-3 border-t border-[#edf1ee] pt-3">
              <p className="mb-2 text-[11px] font-extrabold tracking-[.05em] text-muted-soft">서버 전체 점유</p>
              <div className="flex items-center justify-between text-xs text-muted">
                <span>FREE</span>
                <span className="font-bold text-[#3f4c43]">{usage.systemFreeCount}대</span>
              </div>
              <div className="flex items-center justify-between text-xs text-muted">
                <span>PRO</span>
                <span className="font-bold text-[#3f4c43]">{usage.systemProCount}대</span>
              </div>
            </div>
          </div>
        )}

        <div className="flex items-center gap-2.5 px-[5px] py-2">
          <div className="grid h-[34px] w-[34px] place-items-center rounded-full bg-[#eef2ef] text-xs font-extrabold">
            {user?.email?.[0]?.toUpperCase() ?? "?"}
          </div>
          <div className="min-w-0 flex-1">
            <strong className="block truncate text-sm">{user?.email ?? ""}</strong>
            <button onClick={handleLogout} className="mt-0.5 block text-xs text-muted-soft hover:text-muted">
              로그아웃
            </button>
          </div>
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto px-[38px] pb-[60px] pt-[34px]">{children}</main>
    </div>
  );
}

function UsageBar({
  label,
  current,
  max,
  suffix = "",
  hideBar = false,
}: {
  label: string;
  current: number;
  max: number;
  suffix?: string;
  hideBar?: boolean;
}) {
  const percent = max > 0 ? Math.min(100, Math.round((current / max) * 100)) : 0;
  return (
    <div className="mt-3 first:mt-3">
      <div className="mb-[5px] flex items-center justify-between text-xs text-muted">
        <span>{label}</span>
        <strong className="text-[#17211b]">
          {current}
          {!hideBar && (
            <span className="text-muted-soft">
              {" "}
              / {max}
              {suffix}
            </span>
          )}
          {hideBar && suffix}
        </strong>
      </div>
      {!hideBar && (
        <div className="h-[5px] overflow-hidden rounded-full bg-[#edf1ee]">
          <span className="block h-full bg-brand" style={{ width: `${percent}%` }} />
        </div>
      )}
    </div>
  );
}
