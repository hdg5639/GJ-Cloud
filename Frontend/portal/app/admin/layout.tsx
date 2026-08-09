"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { useEffect, useState } from "react";

const NAV_ITEMS = [
  { href: "/", icon: "▣", label: "대시보드", exact: true },
  { href: "/users", icon: "◫", label: "사용자 관리" },
  { href: "/vms", icon: "⌘", label: "VM 관리" },
  { href: "/system-infrastructure", icon: "◇", label: "시스템 인프라" },
  { href: "/upgrade-requests", icon: "⚙", label: "플랜 변경 요청" },
  { href: "/support-inquiries", icon: "?", label: "사용자 문의" },
  { href: "/docs", icon: "▤", label: "사용 설명서 관리" },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, user, isLoading, logout } = useAuth();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const isLoginPage = pathname.endsWith("/login");

  useEffect(() => {
    if (!isLoginPage && !isLoading && !accessToken) {
      router.replace("/login");
    }
  }, [isLoginPage, accessToken, isLoading, router]);

  if (isLoginPage) return <>{children}</>;

  if (isLoading) return null;

  async function handleLogout() {
    logout();
    router.push("/login");
  }

  return (
    <div className="flex h-dvh min-w-0 overflow-hidden bg-background">
      {drawerOpen && (
        <button
          type="button"
          aria-label="관리자 메뉴 닫기"
          onClick={() => setDrawerOpen(false)}
          className="fixed inset-0 z-40 bg-black/35 backdrop-blur-[2px] lg:hidden"
        />
      )}

      <aside className={`fixed inset-y-0 left-0 z-50 flex h-dvh w-[min(278px,86vw)] shrink-0 flex-col gap-[22px] overflow-y-auto border-r border-line bg-panel px-5 pb-[18px] pt-[22px] shadow-2xl transition-transform duration-300 ease-out lg:static lg:w-[238px] lg:translate-x-0 lg:pt-[26px] lg:shadow-none ${drawerOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-center px-[5px]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/controlbox-wordmark.svg" alt="ControlBox" className="h-auto w-[180px] lg:w-[190px]" />
          <button type="button" onClick={() => setDrawerOpen(false)} aria-label="관리자 메뉴 닫기" className="ml-auto grid h-9 w-9 place-items-center rounded-[9px] text-lg text-muted hover:bg-[#f2f6f3] lg:hidden">×</button>
        </div>

        <nav className="grid gap-1.5">
          {NAV_ITEMS.map((item) => {
            const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setDrawerOpen(false)}
                className={`flex w-full items-center gap-2.5 rounded-[10px] px-3 py-[11px] text-left text-sm font-bold ${
                  active ? "bg-[#fdecec] text-danger" : "text-[#445248] hover:bg-[#f2f6f3]"
                }`}
              >
                <span aria-hidden>{item.icon}</span>
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto flex items-center gap-2.5 px-[5px] py-2">
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

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-14 shrink-0 items-center border-b border-line bg-panel/95 px-4 backdrop-blur-xl lg:hidden">
          <button type="button" onClick={() => setDrawerOpen(true)} aria-label="관리자 메뉴 열기" className="grid h-9 w-9 place-items-center rounded-[9px] border border-line bg-background text-lg text-foreground">☰</button>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/controlbox-wordmark.svg" alt="ControlBox" className="ml-3 h-auto w-[136px]" />
          <span className="ml-auto rounded-full bg-[#fdecec] px-2.5 py-1 text-[9px] font-extrabold uppercase tracking-[.08em] text-danger">Admin</span>
        </header>
        <main className="min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-4 pb-14 pt-5 sm:px-7 sm:pb-[60px] sm:pt-7 lg:px-[38px] lg:pt-[34px]">{children}</main>
      </div>
    </div>
  );
}
