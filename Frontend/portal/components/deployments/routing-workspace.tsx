"use client";

import { useState } from "react";
import type { ComposeRouterPlanResult, ComposeRouterRouteOverride, ComposePreparationResult } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input, Textarea, Select } from "@/components/ui/field";

export function RoutingWorkspace({ plan, services, overrides, ports, excluded, disabled, onOverride, onPort, onExpose, onApply, onCaddyApply }: {
  plan: ComposeRouterPlanResult | null;
  services: ComposePreparationResult["services"];
  overrides: Record<string, ComposeRouterRouteOverride>;
  ports: Record<string, number>;
  excluded: string[];
  disabled: boolean;
  onOverride: (name: string, value: ComposeRouterRouteOverride) => void;
  onPort: (name: string, port: number) => void;
  onExpose: (name: string, exposed: boolean) => void;
  onApply: () => void;
  onCaddyApply: (code: string) => Promise<void>;
}) {
  const [selected, setSelected] = useState<string | null>(null);
  const apps = services.filter(service => service.name !== "gamjabox-router");
  const current = apps.find(service => service.name === selected);
  const route = plan?.routes.find(route => route.serviceName === selected);
  const override = selected ? overrides[selected] : undefined;
  const height = Math.max(240, apps.length * 80 + 32);
  const makeRoot = (name: string) => {
    plan?.routes.filter(route => route.root && route.serviceName !== name).forEach(route =>
      onOverride(route.serviceName, { mode: "PREFIX", routePath: "/" + route.serviceName, stripPrefix: false, customSubdomain: null }));
    onOverride(name, { mode: "PREFIX", routePath: "/", stripPrefix: false, customSubdomain: null });
    onExpose(name, true);
    setSelected(name);
  };
  return (
    <section aria-label="서비스 라우팅 구성도" className="min-w-0 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-bold">서비스 연결</h3>
          <p className="mt-1 text-xs text-muted">앱을 선택해 포트와 경로를 수정하세요. 진입점에서 앱으로 끌어 기본 연결을 선택할 수도 있습니다.</p>
        </div>
        <Button type="button" onClick={onApply} disabled={disabled || !apps.length}>연결 설정 적용</Button>
      </div>
      <div className="overflow-x-auto rounded-md border border-line bg-white/[0.015]">
        <div className="relative min-w-[640px]" style={{ height }}>
          <svg aria-hidden="true" className="absolute inset-0 h-full w-full" viewBox={"0 0 640 " + height} preserveAspectRatio="none">
            <path d={"M 170 " + height/2 + " H 240"} fill="none" stroke="currentColor" className="text-brand" strokeWidth="2" />
            {apps.map((app, index) => {
              const exposed = !excluded.includes(app.name) && plan?.routes.some(route => route.serviceName === app.name);
              return <path key={app.name} d={"M 380 " + height/2 + " C 420 " + height/2 + ", 400 " + (index*80+52) + ", 448 " + (index*80+52)} fill="none" stroke="currentColor" strokeWidth={selected === app.name ? 2.5 : 1.5} strokeDasharray={exposed ? undefined : "5 5"} className={exposed ? "text-brand" : "text-line-strong"} />;
            })}
          </svg>
          <div draggable={!disabled} onDragStart={event => event.dataTransfer.setData("text/gamjabox-route", "gateway")} className="absolute left-[3%] top-1/2 w-[23%] -translate-y-1/2 rounded-md border border-line-strong bg-panel p-3">
            <p className="text-xs font-bold">공개 진입점</p>
            <p className="mt-1 text-[11px] text-muted">도메인 / 경로</p>
            <p className="mt-1 text-[11px] text-muted-soft">앱으로 드래그</p>
          </div>
          <div className="absolute left-[37.5%] top-1/2 w-[22%] -translate-y-1/2 rounded-md border border-brand/40 bg-panel p-3">
            <p className="text-xs font-bold">{plan?.routerConfig ? "Caddy" : "연결 방식"}</p>
            <p className="mt-1 text-[11px] text-muted">{plan?.routerHostPort ? "호스트 :" + plan.routerHostPort : "포트·라우터 분석 대기"}</p>
          </div>
          {apps.map((app, index) => {
            const connection = plan?.routes.find(route => route.serviceName === app.name);
            return <button key={app.name} type="button" disabled={disabled} aria-pressed={selected === app.name}
              onClick={() => setSelected(app.name)} onDragOver={event => event.preventDefault()}
              onDrop={event => { event.preventDefault(); if (!disabled && event.dataTransfer.getData("text/gamjabox-route") === "gateway") makeRoot(app.name); }}
              className={"absolute left-[70%] w-[27%] rounded-md border bg-panel p-3 text-left transition-colors focus-visible:outline-2 focus-visible:outline-brand " + (selected === app.name ? "border-brand" : "border-line hover:border-line-strong")}
              style={{ top: index*80+16 }}>
              <span className="block truncate text-xs font-bold">{app.name}</span>
              <span className="mt-1 block truncate text-[11px] text-muted">{excluded.includes(app.name) || !connection ? "내부 서비스" : connection.mode === "DOMAIN" ? connection.customSubdomain + ".…" : connection.routePath} · 내부 :{ports[app.name] ?? connection?.containerPort ?? app.containerPorts[0] ?? "확인 필요"}</span>
            </button>;
          })}
        </div>
      </div>
      {current && <fieldset disabled={disabled} className="grid min-w-0 grid-cols-1 gap-3 rounded-md border border-line p-3 sm:grid-cols-2">
        <legend className="px-1 text-xs font-bold">{current.name} 연결 설정</legend>
        <label className="flex items-center gap-2 text-xs sm:col-span-2"><input type="checkbox" checked={!excluded.includes(current.name) && Boolean(route || override)} onChange={e => onExpose(current.name, e.target.checked)} />외부 연결에 포함</label>
        <label className="text-xs text-muted">컨테이너 내부 포트
          <Input aria-label={current.name + " 내부 포트"} type="number" min={1} max={65535} value={ports[current.name] ?? route?.containerPort ?? current.containerPorts[0] ?? ""} onChange={e => onPort(current.name, Number(e.target.value))} className="mt-1 w-full" />
        </label>
        <label className="text-xs text-muted">연결 방식
          <Select aria-label={current.name + " 연결 방식"} value={override?.mode ?? route?.mode ?? "PREFIX"} className="mt-1 w-full" onChange={e => onOverride(current.name, { mode: e.target.value as "PREFIX" | "DOMAIN", routePath: override?.routePath ?? route?.routePath ?? "/" + current.name, stripPrefix: override?.stripPrefix ?? false, customSubdomain: override?.customSubdomain ?? route?.customSubdomain ?? "" })}>
            <option value="PREFIX">공용 주소의 경로</option><option value="DOMAIN">전용 도메인</option>
          </Select>
        </label>
        {(override?.mode ?? route?.mode) !== "DOMAIN" && <>
          <label className="text-xs text-muted">외부 경로
            <Input aria-label={current.name + " 외부 경로"} value={override?.routePath ?? route?.routePath ?? "/" + current.name} className="mt-1 w-full" onChange={e => onOverride(current.name, { mode: "PREFIX", routePath: e.target.value, stripPrefix: override?.stripPrefix ?? route?.stripPrefix ?? false, customSubdomain: null })} />
          </label>
          <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={override?.stripPrefix ?? route?.stripPrefix ?? false} onChange={e => onOverride(current.name, { mode: "PREFIX", routePath: override?.routePath ?? route?.routePath ?? "/" + current.name, stripPrefix: e.target.checked, customSubdomain: null })} />앱에 전달할 때 경로 접두사 제거</label>
          <Button type="button" onClick={() => makeRoot(current.name)}>이 앱을 기본 / 연결로 선택</Button>
        </>}
        {(override?.mode ?? route?.mode) === "DOMAIN" && <p className="text-xs text-muted-soft sm:col-span-2">전용 서브도메인은 아래 공개 주소 설정에서 입력하고 가용성을 확인하세요.</p>}
      </fieldset>}
      {plan?.routerConfig && <CaddyEditor key={plan.routerConfig} code={plan.routerConfig} disabled={disabled} onApply={onCaddyApply} />}
      <p className="text-[11px] text-muted-soft">구성도는 현재 계획과 편집값입니다. 설정 적용 후 최종 Compose로 검증합니다. 임의로 작성한 Caddy 코드는 자동 구성도를 재생성하지 않습니다.</p>
    </section>
  );
}

function CaddyEditor({ code, disabled, onApply }: { code: string; disabled: boolean; onApply: (code: string) => Promise<void> }) {
  const [value, setValue] = useState(code);
  const [applying, setApplying] = useState(false);
  return <details className="rounded-md border border-line p-3">
    <summary className="cursor-pointer text-xs font-bold">Caddyfile 코드 편집</summary>
    <Textarea aria-label="Caddyfile 코드" value={value} disabled={disabled} onChange={e => setValue(e.target.value)} rows={12} spellCheck={false} className="my-3 w-full font-mono text-xs" />
    <Button type="button" disabled={disabled || applying || value === code} onClick={async () => { setApplying(true); try { await onApply(value); } finally { setApplying(false); } }}>이 Caddyfile 적용</Button>
  </details>;
}
