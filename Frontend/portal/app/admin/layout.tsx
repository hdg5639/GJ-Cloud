"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { useEffect } from "react";

const NAV_ITEMS = [
  { href: "/", icon: "▣", label: "대시보드", exact: true },
  { href: "/users", icon: "◫", label: "사용자 관리" },
  { href: "/vms", icon: "⌘", label: "VM 관리" },
  { href: "/upgrade-requests", icon: "⚙", label: "플랜 변경 요청" },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, user, isLoading, logout } = useAuth();
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
    <div className="flex h-screen overflow-hidden bg-background">
      <aside className="sticky top-0 flex h-screen w-[238px] shrink-0 flex-col gap-[22px] overflow-y-auto border-r border-line bg-panel px-5 pb-[18px] pt-[26px]">
        <div className="flex items-center px-[5px]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/controlbox-wordmark.svg" alt="ControlBox" className="h-auto w-[190px]" />
        </div>

        <nav className="grid gap-1.5">
          {NAV_ITEMS.map((item) => {
            const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
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

      <main className="flex-1 overflow-y-auto px-[38px] pb-[60px] pt-[34px]">{children}</main>
    </div>
  );
}
