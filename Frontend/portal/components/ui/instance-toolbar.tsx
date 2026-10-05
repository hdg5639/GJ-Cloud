import type { ReactNode } from "react";

// 화면 이동은 InstanceSectionNav, 해당 화면의 상태와 작업은 이 툴바에서 표시한다.
export function InstanceToolbar({ children }: { children: ReactNode }) {
  return (
    <header className="mb-3 flex min-w-0 shrink-0 flex-wrap items-center gap-y-1 rounded-panel border border-line bg-panel [&>div]:max-w-full">
      {children}
    </header>
  );
}
