"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useEffect, useRef, useState } from "react";
import type { UsageResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/components/ui/cn";

const NAV_ITEMS = [
  { href: "/instances", icon: "▣", label: "인스턴스" },
  { href: "/organizations", icon: "◫", label: "협업" },
  { href: "/ssh-keys", icon: "⌘", label: "SSH 키" },
  { href: "/settings", icon: "⚙", label: "설정" },
];

const SIDEBAR_COLLAPSED_KEY = "gj-sidebar-collapsed";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, user, isLoading, logout } = useAuth();
  const [usage, setUsage] = useState<UsageResponse | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const profileMenuRef = useRef<HTMLDivElement>(null);
  // 데스크톱 전용 설정이라 새로고침해도 유지되게 로컬 저장에서 읽어옴 — 이 레이아웃은 isLoading이 풀리기 전까지
  // 항상 null을 렌더링하므로(아래 return null) 서버/클라이언트 첫 렌더 결과물이 갈릴 일이 없어 하이드레이션 문제 없음
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window === "undefined") return false;
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1";
  });

  function toggleCollapsed() {
    setProfileMenuOpen(false);
    setCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
      return next;
    });
  }

  useEffect(() => {
    // 내비게이션 완료 시 모바일 드로어와 임시 계정 메뉴 상태를 함께 정리한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDrawerOpen(false);
    setProfileMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!profileMenuOpen) return;
    function closeProfileMenu(event: MouseEvent) {
      if (!profileMenuRef.current?.contains(event.target as Node)) setProfileMenuOpen(false);
    }
    function closeProfileMenuOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setProfileMenuOpen(false);
    }
    document.addEventListener("mousedown", closeProfileMenu);
    window.addEventListener("keydown", closeProfileMenuOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeProfileMenu);
      window.removeEventListener("keydown", closeProfileMenuOnEscape);
    };
  }, [profileMenuOpen]);

  useEffect(() => {
    if (!isLoading && !accessToken) {
      router.replace("/login");
    }
  }, [accessToken, isLoading, router]);

  // pathname을 의존성에 넣어 페이지 이동마다 재조회 — VM 생성 후 목록/상세로 이동할 때 이 레이아웃
  // 자체는 그대로 유지(리마운트 안 됨)돼서, 안 그러면 새로고침 전까진 개수가 그대로 남아있었음.
  useEffect(() => {
    if (!accessToken) return;
    api.user.usage(accessToken).then(setUsage).catch(() => {});
  }, [accessToken, pathname]);

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
      <div
        className={cn(
          "fixed inset-0 z-40 bg-black/0 opacity-0 backdrop-blur-none transition-[background-color,opacity,backdrop-filter] duration-300 lg:hidden",
          drawerOpen ? "pointer-events-auto bg-black/60 opacity-100 backdrop-blur-[3px]" : "pointer-events-none"
        )}
        onClick={() => setDrawerOpen(false)}
        aria-hidden
      />

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-[238px] shrink-0 -translate-x-full flex-col gap-[22px] overflow-y-auto overflow-x-hidden border-r border-line bg-panel px-5 pb-[18px] pt-[26px] will-change-[width,transform] transition-[width,transform,padding] duration-300 [transition-timing-function:cubic-bezier(.22,1,.36,1)] lg:static lg:translate-x-0",
          drawerOpen && "translate-x-0",
          collapsed && "lg:w-[76px] lg:px-3"
        )}
      >
        <div className="relative flex h-8 shrink-0 items-center">
          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
            title={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
            className={cn(
              "group relative hidden h-8 overflow-hidden rounded-lg text-left outline-none transition-[width,background-color] duration-300 hover:bg-white/[0.05] focus-visible:ring-2 focus-visible:ring-brand/60 lg:block",
              collapsed ? "w-8" : "w-[176px]"
            )}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/gamjabox-wordmark-white.svg"
              alt="GamjaBox"
              className={cn(
                "absolute left-0 top-0 h-auto w-[176px] origin-left transition-[opacity,transform] duration-200",
                collapsed && "scale-95 opacity-0"
              )}
            />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/gamjabox-symbol.svg"
              alt="GamjaBox"
              className={cn(
                "absolute left-0 top-0 h-8 w-8 transition-[opacity,transform] duration-200",
                collapsed ? "scale-100 opacity-100" : "scale-75 opacity-0"
              )}
            />
          </button>
          {/* 모바일 드로어에서는 로고가 접기 상태와 무관하게 워드마크로 보임 */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/gamjabox-wordmark-white.svg" alt="GamjaBox" className="h-auto w-[176px] lg:hidden" />
        </div>

        <nav className="grid gap-1.5">
          {NAV_ITEMS.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setDrawerOpen(false)}
                title={collapsed ? item.label : undefined}
                className={cn(
                  "flex w-full items-center gap-2.5 overflow-hidden rounded-[10px] px-3 py-[11px] text-left text-sm font-bold transition-[background-color,color,padding] duration-200",
                  collapsed && "lg:justify-center lg:px-0",
                  active ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
                )}
              >
                <span aria-hidden className="shrink-0">
                  {item.icon}
                </span>
                <span
                  className={cn(
                    "overflow-hidden whitespace-nowrap transition-[max-width,opacity,transform] duration-300",
                    collapsed ? "lg:max-w-0 lg:-translate-x-2 lg:opacity-0" : "max-w-[160px] translate-x-0 opacity-100"
                  )}
                >
                  {item.label}
                </span>
              </Link>
            );
          })}
        </nav>

        <div className="flex-1" />

        {usage && (
          <div className={cn("rounded-[14px] border border-line bg-white/[0.02] p-4", collapsed && "lg:hidden")}>
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

        <div ref={profileMenuRef} className={cn("relative flex items-center gap-2.5 px-[5px] py-2", collapsed && "lg:justify-center")}>
          <button
            type="button"
            onClick={() => collapsed && setProfileMenuOpen((prev) => !prev)}
            aria-label={collapsed ? "계정 메뉴 열기" : undefined}
            aria-expanded={collapsed ? profileMenuOpen : undefined}
            className={cn(
              "grid h-[34px] w-[34px] shrink-0 place-items-center rounded-full bg-white/[0.06] text-xs font-extrabold transition-[background-color,transform,box-shadow] duration-200",
              collapsed && "lg:hover:scale-105 lg:hover:bg-white/[0.1] lg:focus-visible:ring-2 lg:focus-visible:ring-brand/60",
              collapsed && profileMenuOpen && "lg:bg-white/[0.1] lg:ring-2 lg:ring-brand/40"
            )}
            title={collapsed ? (user?.email ?? undefined) : undefined}
          >
            {user?.email?.[0]?.toUpperCase() ?? "?"}
          </button>
          <div className={cn("min-w-0 flex-1 overflow-hidden", collapsed && "lg:hidden")}>
            <strong className="block truncate text-sm">{user?.email ?? ""}</strong>
            <button onClick={handleLogout} className="mt-0.5 block text-xs text-muted-soft hover:text-muted">
              로그아웃
            </button>
          </div>
          <div
            className={cn(
              "pointer-events-none absolute bottom-[calc(100%+8px)] left-1/2 z-10 hidden w-[62px] -translate-x-1/2 translate-y-2 scale-95 opacity-0 transition-[opacity,transform] duration-200 [transition-timing-function:cubic-bezier(.16,1,.3,1)] lg:block",
              collapsed && profileMenuOpen && "pointer-events-auto translate-y-0 scale-100 opacity-100"
            )}
          >
            <button
              type="button"
              onClick={handleLogout}
              className="w-full rounded-lg border border-line-strong bg-[#1a1e25] px-2 py-2 text-[11px] font-bold text-danger shadow-xl shadow-black/40 transition-colors hover:bg-danger/10"
            >
              로그아웃
            </button>
            <span className="absolute -bottom-1 left-1/2 h-2 w-2 -translate-x-1/2 rotate-45 border-b border-r border-line-strong bg-[#1a1e25]" />
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

        <main className="flex-1 overflow-y-auto px-5 pb-12 pt-6 sm:px-[38px] sm:pb-[60px] sm:pt-[34px]">
          <div key={pathname} className="dashboard-page">
            {children}
          </div>
        </main>
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
