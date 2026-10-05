"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { cn } from "./cn";

// 개요와 모든 하위 화면에서 동일한 순서와 선택 상태를 사용하는 이동 메뉴.
const SECTIONS = [
  {
    key: "console",
    label: "콘솔",
    href: (id: string) => `/instances/${id}/console`,
    icon: (
      <>
        <polyline points="4 17 10 11 4 5" />
        <line x1="12" y1="19" x2="20" y2="19" />
      </>
    ),
  },
  {
    key: "files",
    label: "파일",
    href: (id: string) => `/instances/${id}/files`,
    icon: <path d="M3 5a2 2 0 012-2h4l2 2h8a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V5z" />,
  },
  {
    key: "docker",
    label: "Docker",
    href: (id: string) => `/instances/${id}/docker`,
    icon: (
      <>
        <rect x="2" y="7" width="20" height="14" rx="2" />
        <path d="M6 7V5a2 2 0 012-2h8a2 2 0 012 2v2" />
        <line x1="8" y1="12" x2="16" y2="12" />
      </>
    ),
  },
  {
    key: "deployments",
    label: "배포",
    href: (id: string) => `/instances/${id}/deployments`,
    icon: (
      <>
        <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
        <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
        <line x1="12" y1="22.08" x2="12" y2="12" />
      </>
    ),
  },
  {
    key: "preview",
    label: "Auto Preview",
    href: (id: string) => `/instances/${id}/preview`,
    icon: (
      <>
        <circle cx="12" cy="12" r="3" />
        <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
      </>
    ),
  },
  {
    key: "backups",
    label: "백업",
    href: (id: string) => `/instances/${id}/backups`,
    icon: (
      <>
        <ellipse cx="12" cy="5" rx="9" ry="3" />
        <path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5" />
        <path d="M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3" />
      </>
    ),
  },
  {
    key: "metrics",
    label: "성능",
    href: (id: string) => `/instances/${id}/metrics`,
    icon: (
      <>
        <rect x="18" y="3" width="4" height="18" rx="1" />
        <rect x="10" y="8" width="4" height="13" rx="1" />
        <rect x="2" y="13" width="4" height="8" rx="1" />
      </>
    ),
  },
] as const;

export function InstanceSectionNav({ vmId, vmStatus }: { vmId: string; vmStatus?: string }) {
  const pathname = usePathname();
  const navRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const nav = navRef.current;
    const active = nav?.querySelector<HTMLElement>('[aria-current="page"]');
    if (!nav || !active) return;
    const bounds = nav.getBoundingClientRect();
    const item = active.getBoundingClientRect();
    if (item.left < bounds.left) nav.scrollLeft -= bounds.left - item.left + 6;
    else if (item.right > bounds.right) nav.scrollLeft += item.right - bounds.right + 6;
  }, [pathname]);

  return (
    <nav ref={navRef} aria-label="인스턴스 메뉴" className="mb-3 flex min-w-0 shrink-0 items-center gap-1 overflow-x-auto rounded-panel border border-line bg-panel p-1.5">
      <Link
        href={`/instances/${vmId}`}
        aria-current={pathname === `/instances/${vmId}` ? "page" : undefined}
        className={cn(
          "flex shrink-0 items-center gap-1.5 rounded-md px-3 h-8 text-[13px] font-bold whitespace-nowrap transition-colors",
          pathname === `/instances/${vmId}` ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
        )}
      >
        개요
      </Link>
      {SECTIONS.map((section) => {
        const href = section.href(vmId);
        const active = pathname === href || pathname.startsWith(`${href}/`);
        const disabled = vmStatus !== undefined && vmStatus !== "RUNNING" && section.key !== "metrics";
        const className = cn(
          "flex h-8 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md px-3 text-[13px] font-bold transition-colors focus-visible:outline-2 focus-visible:outline-brand",
          active ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
        );
        if (disabled) return (
          <span key={section.key} aria-disabled="true" title="VM이 실행 중일 때 이용할 수 있어요" className={cn(className, "cursor-not-allowed opacity-40")}>
            <svg className="h-[14px] w-[14px] shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>{section.icon}</svg>
            {section.label}
          </span>
        );
        return (
          <Link
            key={section.key}
            href={href}
            aria-current={active ? "page" : undefined}
            className={className}
          >
            <svg className="w-[14px] h-[14px] shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24" aria-hidden>
              {section.icon}
            </svg>
            {section.label}
          </Link>
        );
      })}
    </nav>
  );
}
