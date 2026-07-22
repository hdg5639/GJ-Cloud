"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useEffect, useRef, useState } from "react";
import type { ProfileResponse, UsageResponse } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/components/ui/cn";
import { Avatar } from "@/components/ui/avatar";

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
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
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

  // 닉네임 온보딩 게이트 — 닉네임이 비어있으면(가입 직후) 대시보드 대신 온보딩으로 보낸다.
  // /onboarding은 (dashboard) 라우트 그룹 밖이라 이 레이아웃 자체를 타지 않으므로 리다이렉트 루프가 없다.
  useEffect(() => {
    if (!accessToken) return;
    api.user.profile(accessToken).then((p) => {
      setProfile(p);
      if (!p.nickname?.trim()) {
        router.replace("/onboarding");
      }
    }).catch(() => {});
  }, [accessToken, pathname, router]);

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
          // 접힐 땐 라벨 텍스트가 먼저 사라진 다음(딜레이 없이 빠르게) 패널이 줄어들고, 펼칠 땐 패널이
          // 먼저 넓어진 다음 텍스트가 나타나야 "텍스트가 잠깐 튀어보이는" 느낌이 없음 — 그래서 패널
          // 폭 트랜지션에 접힐 때만 딜레이를 줘서 텍스트(딜레이 없음)보다 살짝 늦게 시작하게 함.
          collapsed ? "lg:w-[76px] lg:px-3 lg:delay-[70ms]" : "lg:delay-0"
        )}
      >
        <div className="relative flex h-11 shrink-0 items-center">
          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
            title={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
            className="group relative hidden h-11 w-full overflow-hidden rounded-lg text-left outline-none transition-colors duration-300 hover:bg-white/[0.05] focus-visible:ring-2 focus-visible:ring-brand/60 lg:block"
          >
            {/* 워드마크/심볼 둘 다 버튼 중앙에 겹쳐 놓고 크로스페이드 — 이전엔 left-0 고정이라 접혔을 때
                심볼이 버튼 폭 중앙이 아니라 왼쪽에 붙어 있었고, 워드마크는 176px 폭 기준 실제 렌더 높이가
                43px 정도인데 컨테이너가 32px(h-8)이라 overflow-hidden에 하단이 잘렸음. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/gamjabox-wordmark-white.svg"
              alt="GamjaBox"
              className={cn(
                "absolute left-1/2 top-1/2 h-[43px] w-[176px] -translate-x-1/2 -translate-y-1/2 transition-[opacity,transform] duration-200",
                collapsed ? "scale-95 opacity-0" : "scale-100 opacity-100"
              )}
            />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/gamjabox-symbol.svg"
              alt="GamjaBox"
              className={cn(
                "absolute left-1/2 top-1/2 h-10 w-10 -translate-x-1/2 -translate-y-1/2 transition-[opacity,transform] duration-200",
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
                  "flex w-full items-center gap-2.5 overflow-hidden rounded-[10px] px-3 py-[11px] text-left text-sm font-bold transition-[background-color,color,padding,gap] duration-200",
                  // 접혔을 때 라벨 span의 max-width가 0이 돼도 gap은 그대로 남아 아이콘이 중앙에서
                  // gap의 절반만큼 왼쪽으로 밀려 보였음 — gap도 함께 0으로 줄여야 진짜 중앙정렬됨
                  collapsed && "lg:justify-center lg:gap-0 lg:px-0",
                  active ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
                )}
              >
                <span
                  aria-hidden
                  className={cn("shrink-0 transition-[font-size] duration-200", collapsed && "lg:text-xl")}
                >
                  {item.icon}
                </span>
                <span
                  className={cn(
                    "overflow-hidden whitespace-nowrap transition-[max-width] duration-200",
                    // 글자가 안 보이게(투명해지게) 먼저 되고 나서 박스(max-width)가 닫혀야 "잘려나가는"
                    // 느낌 없이 자연스러움 — 그래서 바깥쪽(폭)과 안쪽(투명도)의 딜레이를 서로 다르게 둠.
                    collapsed ? "lg:max-w-0 lg:delay-[70ms]" : "max-w-[160px] lg:delay-0"
                  )}
                >
                  <span
                    className={cn(
                      "inline-block transition-[opacity,transform] duration-100",
                      // 펼칠 땐 사이드바 패널(300ms)이 거의 다 넓어진 뒤에야 글자가 나타나야 함 —
                      // 딜레이가 짧으면 패널이 절반쯤 열렸을 때 글자가 먼저 튀어나온 것처럼 보임
                      collapsed
                        ? "lg:-translate-x-2 lg:opacity-0 lg:delay-0"
                        : "translate-x-0 opacity-100 duration-[120ms] lg:delay-[280ms]"
                    )}
                  >
                    {item.label}
                  </span>
                </span>
              </Link>
            );
          })}
        </nav>

        <div className="flex-1" />

        {usage && (
          // 바깥 래퍼는 접힘/펼침에 따른 높이(max-height)만 담당 — 콘텐츠가 있던 자리가 자연스럽게
          // 접히고 펼쳐지게 함. 안쪽 카드는 접힐 땐 빠르게 사라지고(트랜지션), 펼칠 땐 사이드바 패널이
          // 거의 다 넓어진 뒤(280ms)에야 dashboard-page 등에서 쓰는 것과 같은 panel-enter 키프레임으로
          // 살짝 아래에서 튀어오르듯 나타남 — 그냥 즉시 hidden 처리하던 것보다 제품다운 느낌을 주기 위함.
          <div
            className={cn(
              "overflow-hidden transition-[max-height] duration-200",
              collapsed ? "lg:max-h-0 lg:delay-[70ms]" : "max-h-[420px] lg:delay-0"
            )}
          >
            <div
              className={cn(
                "rounded-[14px] border border-line bg-white/[0.02] p-4",
                collapsed
                  ? "transition-[opacity,transform] duration-100 lg:-translate-y-1 lg:scale-95 lg:opacity-0 lg:delay-0"
                  : "lg:[animation:panel-enter_380ms_cubic-bezier(0.16,1,0.3,1)_280ms_both]"
              )}
            >
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
          </div>
        )}

        <div
          ref={profileMenuRef}
          className={cn(
            "relative flex items-center gap-2.5 px-[5px] py-2 transition-[gap] duration-150",
            // gap이 남아있으면 접혔을 때 아바타가 gap 절반만큼 중앙에서 밀려 보임(네비 아이콘과 동일한 이슈)
            collapsed && "lg:justify-center lg:gap-0"
          )}
        >
          <button
            type="button"
            onClick={() => collapsed && setProfileMenuOpen((prev) => !prev)}
            aria-label={collapsed ? "계정 메뉴 열기" : undefined}
            aria-expanded={collapsed ? profileMenuOpen : undefined}
            className={cn(
              "grid shrink-0 place-items-center rounded-full transition-[transform,box-shadow] duration-200",
              collapsed && "lg:hover:scale-105 lg:focus-visible:ring-2 lg:focus-visible:ring-brand/60",
              collapsed && profileMenuOpen && "lg:ring-2 lg:ring-brand/40"
            )}
            title={collapsed ? (profile?.nickname ?? user?.email ?? undefined) : undefined}
          >
            <Avatar nickname={profile?.nickname} email={user?.email} profileImageUrl={profile?.profileImageUrl} sizePx={34} />
          </button>
          <div
            className={cn(
              "overflow-hidden whitespace-nowrap transition-[max-width] duration-200",
              collapsed ? "lg:max-w-0 lg:delay-[70ms]" : "max-w-[150px] flex-1 lg:delay-0"
            )}
          >
            <div
              className={cn(
                "transition-opacity duration-100",
                // 닉네임/로그아웃도 네비 라벨과 동일하게, 패널이 거의 다 넓어진 뒤에 나타나야 함
                collapsed ? "lg:opacity-0 lg:delay-0" : "opacity-100 duration-[120ms] lg:delay-[280ms]"
              )}
            >
              <strong className="block truncate text-sm">{profile?.nickname ?? user?.email ?? ""}</strong>
              <button onClick={handleLogout} className="mt-0.5 block text-xs text-muted-soft hover:text-muted">
                로그아웃
              </button>
            </div>
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
