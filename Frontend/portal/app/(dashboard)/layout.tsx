"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useEffect } from "react";

const NAV_ITEMS = [
  { href: "/instances", label: "인스턴스" },
  { href: "/ssh-keys", label: "SSH 키" },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { accessToken, isLoading, logout } = useAuth();

  useEffect(() => {
    if (!isLoading && !accessToken) {
      router.replace("/login");
    }
  }, [accessToken, isLoading, router]);

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

        <div className="px-3 mt-auto">
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
