import { ReactNode } from "react";

export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="w-[360px] rounded-panel border border-line bg-panel p-8">{children}</div>
    </div>
  );
}

export function AuthBrand({ admin = false }: { admin?: boolean }) {
  if (!admin) {
    return (
      <div className="mb-7">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/gamjabox-wordmark.svg" alt="GamjaBox" className="h-auto w-[200px]" />
      </div>
    );
  }
  return (
    <div className="mb-7">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/controlbox-wordmark.svg" alt="ControlBox" className="h-auto w-[244px]" />
    </div>
  );
}
