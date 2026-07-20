import { ReactNode } from "react";

export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="w-[360px] rounded-panel border border-line bg-panel p-8">{children}</div>
    </div>
  );
}

export function AuthBrand({ admin = false }: { admin?: boolean }) {
  return (
    <div className="mb-7 flex items-center gap-[11px]">
      <div
        className="grid h-9 w-9 place-items-center rounded-[11px] text-sm font-black text-white"
        style={admin ? { background: "var(--danger)" } : { backgroundImage: "linear-gradient(135deg, #12ce70, #08a34f)" }}
      >
        {admin ? "A" : "G"}
      </div>
      <div>
        <strong className="block text-base">gamjabox</strong>
        {admin && <small className="block text-[10px] font-bold uppercase tracking-widest text-danger">admin</small>}
      </div>
    </div>
  );
}
