"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { useEffect } from "react";

const NAV_ITEMS = [
  { href: "/panel", label: "대시보드", exact: true },
  { href: "/panel/users", label: "사용자 관리" },
  { href: "/panel/vms", label: "VM 관리" },
  { href: "/panel/upgrade-requests", label: "플랜 변경 요청" },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, user, isLoading, logout } = useAuth();

  useEffect(() => {
    if (!isLoading && !accessToken) {
      router.replace("/panel/login");
    }
  }, [accessToken, isLoading, router]);

  if (isLoading) return null;

  async function handleLogout() {
    logout();
    router.push("/panel/login");
  }

  return (
    <div className="flex min-h-screen bg-gray-950">
      <aside className="w-[220px] bg-gray-900 border-r border-gray-800 py-5 flex flex-col">
        <div className="flex items-center gap-2 px-5 mb-6">
          <div className="w-6 h-6 rounded-md bg-red-500 flex items-center justify-center">
            <span className="text-white text-xs font-bold">A</span>
          </div>
          <div>
            <span className="font-semibold text-sm text-white">gamjabox</span>
            <span className="block text-[10px] text-red-400 font-medium tracking-widest uppercase">admin</span>
          </div>
        </div>

        <nav className="px-3 flex flex-col gap-0.5 flex-1">
          {NAV_ITEMS.map((item) => {
            const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-md text-sm ${
                  active
                    ? "bg-red-500/10 text-red-400 font-medium"
                    : "text-gray-400 hover:bg-gray-800 hover:text-gray-200"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        {user && (
          <div className="px-4 mb-2">
            <p className="text-[11px] text-gray-500 truncate">{user.email}</p>
          </div>
        )}
        <div className="px-3">
          <button
            onClick={handleLogout}
            className="w-full flex items-center px-3 py-2 rounded-md text-sm text-gray-500 hover:bg-gray-800 hover:text-gray-300"
          >
            로그아웃
          </button>
        </div>
      </aside>

      <main className="flex-1 p-6 overflow-auto text-gray-100">{children}</main>
    </div>
  );
}
