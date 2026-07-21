"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useEffect, useState } from "react";
import type { UsageResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/components/ui/cn";

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
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    if (!isLoading && !accessToken) {
      router.replace("/login");
    }
  }, [accessToken, isLoading, router]);

  useEffect(() => {
    if (!accessToken) return;
    api.user.usage(accessToken).then(setUsage).catch(() => {});
  }, [accessToken]);

  // Modal은 createPortal로 document.body에 직접 붙기 때문에 이 레이아웃 안쪽에
  // .theme-dark를 걸어봤자 소용없음 — body 자체에도 걸어줘야 포탈된 콘텐츠까지 다크 토큰을 상속받음.
  useEffect(() => {
    document.body.classList.add("theme-dark");
    return () => document.body.classList.remove("theme-dark");
  }, []);

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
    <div className="theme-dark flex h-screen overflow-hidden bg-background">
      {/* 모바일 드로어 백드롭 — lg 이상에서는 사이드바가 항상 고정 표시되므로 불필요 */}
      {drawerOpen && (
        <div className="fixed inset-0 z-40 bg-black/60 lg:hidden" onClick={() => setDrawerOpen(false)} aria-hidden />
      )}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-[238px] shrink-0 -translate-x-full flex-col gap-[22px] overflow-y-auto border-r border-line bg-panel px-5 pb-[18px] pt-[26px] transition-transform duration-200 lg:static lg:translate-x-0",
          drawerOpen && "translate-x-0"
        )}
      >
        <div className="flex items-center px-[5px]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/gamjabox-wordmark-white.svg" alt="GamjaBox" className="h-auto w-[176px]" />
        </div>

        <nav className="grid gap-1.5">
          {NAV_ITEMS.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setDrawerOpen(false)}
                className={`flex w-full items-center gap-2.5 rounded-[10px] px-3 py-[11px] text-left text-sm font-bold ${
                  active ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
                }`}
              >
                <span aria-hidden>{item.icon}</span>
                {item.label}
              </Link>
            );
          })}
        </nav>

        {usage && (
          <div className="mt-auto rounded-[14px] border border-line bg-white/[0.02] p-4">
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

            <div className="mt-3 border-t border-line pt-3">
              <p className="mb-2 text-[11px] font-extrabold tracking-[.05em] text-muted-soft">서버 전체 점유</p>
              <div className="flex items-center justify-between text-xs text-muted">
                <span>FREE</span>
                <span className="font-bold text-foreground">{usage.systemFreeCount}대</span>
              </div>
              <div className="flex items-center justify-between text-xs text-muted">
                <span>PRO</span>
                <span className="font-bold text-foreground">{usage.systemProCount}대</span>
              </div>
            </div>
          </div>
        )}

        <div className="flex items-center gap-2.5 px-[5px] py-2">
          <div className="grid h-[34px] w-[34px] place-items-center rounded-full bg-white/[0.06] text-xs font-extrabold">
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

      <div className="flex min-w-0 flex-1 flex-col">
        {/* lg 미만에서만 보이는 모바일 탑바 — 햄버거로 사이드바 드로어를 연다 */}
        <header className="flex h-14 shrink-0 items-center gap-3 border-b border-line bg-panel px-4 lg:hidden">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label="메뉴 열기"
            className="grid h-9 w-9 place-items-center rounded-lg text-foreground hover:bg-white/[0.06]"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/gamjabox-wordmark-white.svg" alt="GamjaBox" className="h-auto w-[132px]" />
        </header>

        <main className="flex-1 overflow-y-auto px-5 pb-12 pt-6 sm:px-[38px] sm:pb-[60px] sm:pt-[34px]">{children}</main>
      </div>
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
        <strong className="text-foreground">
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
        <div className="h-[5px] overflow-hidden rounded-full bg-white/[0.08]">
          <span className="block h-full bg-brand" style={{ width: `${percent}%` }} />
        </div>
      )}
    </div>
  );
}
