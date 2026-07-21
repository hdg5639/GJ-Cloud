"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "./cn";

// instances/[id]/** 서브라우트(콘솔/파일/Docker/배포/백업/성능)는 각자 "뒤로가기"만 있고
// 서로 옆으로 이동할 방법이 없었음 — 개요 페이지의 툴바(측정 기반 반응형 collapse)를 그대로
// 복제하기엔 과해서, 가벼운 가로 스크롤 탭바만 별도로 둠. 개요 페이지 자체는 이미 툴바가
// 같은 링크를 다 제공하므로 이 컴포넌트를 쓰지 않음.
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

export function InstanceSectionNav({ vmId }: { vmId: string }) {
  const pathname = usePathname();

  return (
    <nav className="mb-5 flex items-center gap-1 overflow-x-auto rounded-panel border border-line bg-panel p-1.5">
      <Link
        href={`/instances/${vmId}`}
        className={cn(
          "flex shrink-0 items-center gap-1.5 rounded-md px-3 h-8 text-[13px] font-bold whitespace-nowrap transition-colors",
          pathname === `/instances/${vmId}` ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
        )}
      >
        개요
      </Link>
      {SECTIONS.map((section) => {
        const href = section.href(vmId);
        const active = pathname.startsWith(href);
        return (
          <Link
            key={section.key}
            href={href}
            className={cn(
              "flex shrink-0 items-center gap-1.5 rounded-md px-3 h-8 text-[13px] font-bold whitespace-nowrap transition-colors",
              active ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
            )}
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
