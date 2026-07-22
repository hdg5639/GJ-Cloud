"use client";

import { ReactNode, useEffect, useState } from "react";
import { cn } from "./cn";

// 랜딩 페이지(gamjabox-landing)의 다크 톤 비주얼 언어를 로그인/회원가입에도 가져오기 위한 레이아웃.
// 대시보드와 테마를 통일하기 위해 .theme-dark를 걸어 우측 폼 패널도 다크로 렌더링함
// (좌측 다크 패널은 이미 하드코딩 다크라 상관없음 — 이제 양쪽이 같은 톤이라 경계 그라데이션도 불필요).
// 왼쪽 다크 패널은 lg 미만에서 숨김 — 폼만 보이는 기존 모바일 경험은 그대로 유지.
export function AuthSplitLayout({
  visual,
  children,
  introPhase = "done",
}: {
  visual: ReactNode;
  children: ReactNode;
  introPhase?: BrandIntroPhase;
}) {
  return (
    <div className="theme-dark flex min-h-screen bg-background">
      <div className="relative hidden w-[44%] max-w-[560px] shrink-0 overflow-hidden bg-[#07080b] lg:flex lg:flex-col">
        {visual}
      </div>
      <div className="flex flex-1 items-center justify-center px-6 py-14">
        <div className={cn("w-full max-w-[380px]", introPhase !== "done" && "auth-reveal-form")}>{children}</div>
      </div>
    </div>
  );
}

function AuthVisualBackdrop() {
  return (
    <>
      <div
        aria-hidden
        className="pointer-events-none absolute -right-24 top-16 h-[380px] w-[380px] rounded-full bg-[#74ff5b] opacity-[0.14] blur-[100px]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -left-32 bottom-10 h-[320px] w-[320px] rounded-full bg-[#00b66c] opacity-[0.10] blur-[100px]"
      />
    </>
  );
}

function VisualWordmark() {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img src="/gamjabox-wordmark-white.svg" alt="GamjaBox" className="h-auto w-[168px]" />
  );
}

// 인트로 애니메이션이 최종적으로 정착하는 모습(컬러 아이콘 + 흰색/그린 텍스트)과 완전히 동일한
// 정적 버전 — AnimatedVisualWordmark의 "done"(이미 재생됨) 상태에서 사용. "playing" 단계는
// 텍스트를 max-width 애니메이션으로 한 글자씩 드러내야 해서 실제 DOM 텍스트를 쓸 수밖에 없는데,
// 그 텍스트에도 워드마크 SVG와 동일한 폰트(Manrope)·색 분리(Gamja 흰색/Box 그린)를 적용해뒀기
// 때문에 애니메이션이 끝나는 순간 폰트나 색이 바뀌어 보이지 않는다.
function AssembledWordmark() {
  return (
    <div className="flex items-center gap-2.5">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/gamjabox-symbol.svg" alt="GamjaBox" className="h-9 w-9 shrink-0" />
      <span
        className="whitespace-nowrap text-2xl font-extrabold tracking-tighter"
        style={{ fontFamily: "var(--font-manrope)" }}
      >
        <span className="text-[#f4f7f1]">Gamja</span>
        <span className="text-[#08B85B]">Box</span>
      </span>
    </div>
  );
}

const BRAND_INTRO_KEY = "gb-brand-intro-played";
const BRAND_INTRO_MS = 2600;
const BRAND_INTRO_DURATION = `${BRAND_INTRO_MS / 1000}s`;

export type BrandIntroPhase = "pending" | "playing" | "done";

// 로그인 화면 전용 인트로 타이밍 — 세션당 한 번만 재생. 로고(아이콘+텍스트)와 나머지 콘텐츠(좌측
// 소개 문구/우측 로그인 폼)가 같은 타임라인을 공유해야 해서 상위(login 페이지)에서 한 번만
// 계산해 AuthMarketingPanel/AuthSplitLayout 양쪽에 내려준다 — 각자 따로 세션스토리지를 체크하면
// 먼저 실행되는 쪽만 "playing"을 보고 나머지는 "done"으로 어긋나버림.
// 재생이 끝나면 "playing"에 영영 머무르지 않고 자동으로 "done"으로 전환한다 — 그래야 인트로가
// 끝난 직후의 레이아웃(임시로 추가된 wrapper 요소들 포함)이 새로고침 후의 "done" 레이아웃과
// 완전히 같은 구조로 수렴해서, 두 상태 사이에 위치가 미묘하게 어긋나는 문제가 생기지 않는다.
export function useBrandIntroPhase(): BrandIntroPhase {
  const [phase, setPhase] = useState<BrandIntroPhase>("pending");

  useEffect(() => {
    if (sessionStorage.getItem(BRAND_INTRO_KEY)) {
      setPhase("done");
      return;
    }
    sessionStorage.setItem(BRAND_INTRO_KEY, "1");
    setPhase("playing");
    const t = setTimeout(() => setPhase("done"), BRAND_INTRO_MS + 100);
    return () => clearTimeout(t);
  }, []);

  return phase;
}

// 아이콘만 화면 중앙에 확대되어 뜬 채로 잠깐 멈췄다가, 그 자리에서 살짝 옆으로 비켜서며
// "GamjaBox" 텍스트가 좌→우로 드러나 완전한 워드마크를 이루고, 그 다음에 전체가 좌상단
// 제자리로 이동. 이미 재생됐으면 처음부터 정착된 상태로 렌더링.
function AnimatedVisualWordmark({ phase }: { phase: BrandIntroPhase }) {
  if (phase === "pending") {
    // 세션 체크 끝나기 전 첫 페인트 — 인트로가 재생될 경우와 동일한 시작 상태를 보여줘서 깜빡임 없앰
    return (
      <div className="relative h-9 w-[168px]">
        <div
          className="fixed z-50 flex items-center gap-2.5 opacity-0"
          style={{ left: "50%", top: "50%", transform: "translate(-50%, -50%) scale(3.2)" }}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/gamjabox-symbol.svg" alt="" className="h-9 w-9 shrink-0" />
        </div>
      </div>
    );
  }

  if (phase === "done") {
    return <AssembledWordmark />;
  }

  return (
    <div className="relative h-9 w-[168px]">
      <div
        className="brand-intro-icon fixed z-50 flex items-center gap-2.5"
        style={{ animation: `brand-intro-icon ${BRAND_INTRO_DURATION} cubic-bezier(.22,1,.36,1) forwards` }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/gamjabox-symbol.svg" alt="GamjaBox" className="h-9 w-9 shrink-0" />
        <span
          className="brand-intro-text inline-block overflow-hidden whitespace-nowrap text-2xl font-extrabold tracking-tighter"
          style={{
            animation: `brand-intro-text ${BRAND_INTRO_DURATION} cubic-bezier(.22,1,.36,1) forwards`,
            fontFamily: "var(--font-manrope)",
          }}
        >
          <span className="text-[#f4f7f1]">Gamja</span>
          <span className="text-[#08B85B]">Box</span>
        </span>
      </div>
    </div>
  );
}

function VisualFooter() {
  return (
    <p className="relative z-10 text-[11px] text-[#6e776f]">
      © 2026 GamjaBox · Self-hosted cloud, built from the ground up.
    </p>
  );
}

function IconServer() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="6" rx="1.5" />
      <rect x="3" y="14" width="18" height="6" rx="1.5" />
      <circle cx="7" cy="7" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="7" cy="17" r="0.9" fill="currentColor" stroke="none" />
    </svg>
  );
}

function IconShield() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  );
}

function IconDeploy() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 19V5" />
      <path d="M6 11l6-6 6 6" />
      <path d="M4 19h16" />
    </svg>
  );
}

function IconPulse() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 12h4l2 7 4-14 2 7h6" />
    </svg>
  );
}

const MARKETING_SLIDES: Array<{ icon: ReactNode; title: string; desc: string }> = [
  {
    icon: <IconServer />,
    title: "Proxmox 기반 VM 프로비저닝",
    desc: "템플릿 선택부터 네트워크 구성까지, 클릭 몇 번이면 서버가 준비됩니다.",
  },
  {
    icon: <IconShield />,
    title: "Zero Trust 보안 접속",
    desc: "별도 VPN 없이 브라우저에서 바로 SSH 터미널과 파일 관리에 접근합니다.",
  },
  {
    icon: <IconDeploy />,
    title: "원클릭 배포",
    desc: "Docker Compose를 올리면 빌드부터 헬스체크까지 자동으로 이어집니다.",
  },
  {
    icon: <IconPulse />,
    title: "실시간 운영 관찰",
    desc: "인스턴스 상태, 로그, 포트, 도메인을 한 화면에서 실시간으로 확인합니다.",
  },
];

const SLIDE_INTERVAL_MS = 3600;

function useRotatingIndex(length: number, intervalMs: number) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setIndex((prev) => (prev + 1) % length);
    }, intervalMs);
    return () => clearInterval(timer);
  }, [length, intervalMs]);

  return index;
}

export function AuthMarketingPanel({ introPhase = "done" }: { introPhase?: BrandIntroPhase }) {
  const activeIndex = useRotatingIndex(MARKETING_SLIDES.length, SLIDE_INTERVAL_MS);

  return (
    <div className="relative flex h-full flex-col justify-between p-10">
      {/* done 상태에선 contents로 완전히 레이아웃에서 빠져서(추가 flex item이 되지 않음) 원래
          구조와 100% 동일하게 유지 — 그래야 인트로 재생 직후와 새로고침 후의 위치가 어긋나지 않음 */}
      <div className={introPhase !== "done" ? "auth-reveal" : "contents"}>
        <AuthVisualBackdrop />
      </div>

      <div className="relative z-10">
        <AnimatedVisualWordmark phase={introPhase} />
      </div>

      <div className={cn("relative z-10", introPhase !== "done" && "auth-reveal")}>
        <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-[#baff4a]/20 bg-[#baff4a]/[0.06] px-3 py-1.5 text-[11px] font-extrabold uppercase tracking-[0.16em] text-[#baff4a]">
          <span className="h-[6px] w-[6px] rounded-full bg-[#baff4a] shadow-[0_0_0_4px_rgba(186,255,74,0.12),0_0_14px_rgba(186,255,74,0.8)]" />
          SELF-HOSTED CLOUD PLATFORM
        </div>

        <h2 className="mb-4 text-[34px] font-extrabold leading-[1.08] tracking-[-0.03em] text-[#f4f7f1]">
          Your cloud.
          <br />
          <span className="bg-gradient-to-r from-[#eaffc8] via-[#baff4a] to-[#63ffad] bg-clip-text text-transparent">
            Under your control.
          </span>
        </h2>
        <p className="mb-10 max-w-[360px] text-sm leading-relaxed text-[#9aa39a]">
          VM 생성부터 SSH, 파일 관리, Docker 배포까지 — 인프라 전체를 하나의 콘솔에서 다룹니다.
        </p>

        <div className="mb-3 flex items-center gap-2 text-[10px] font-extrabold uppercase tracking-[0.14em] text-[#5f665f]">
          <span>0{activeIndex + 1}</span>
          <span className="h-px flex-1 bg-white/10" />
          <span>0{MARKETING_SLIDES.length}</span>
        </div>

        <div className="relative h-[124px] max-w-[360px] overflow-hidden">
          {(() => {
            const slide = MARKETING_SLIDES[activeIndex];
            return (
              <div key={activeIndex} className="absolute inset-0 [animation:auth-slide-in_0.5s_ease-out]">
                <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-[#baff4a]/10 text-[#baff4a] [&_svg]:h-5 [&_svg]:w-5">
                  {slide.icon}
                </div>
                <p className="mb-1.5 text-[15px] font-bold text-[#e7ebe6]">{slide.title}</p>
                <p className="text-[12.5px] leading-relaxed text-[#8f978f]">{slide.desc}</p>
              </div>
            );
          })()}
        </div>

        <div className="mt-2 flex gap-1.5">
          {MARKETING_SLIDES.map((slide, i) => (
            <span
              key={slide.title}
              className={cn(
                "h-1 rounded-full transition-all duration-500",
                i === activeIndex ? "w-6 bg-[#baff4a]" : "w-1.5 bg-white/15"
              )}
            />
          ))}
        </div>
      </div>

      <div className={introPhase !== "done" ? "auth-reveal" : "contents"}>
        <VisualFooter />
      </div>
    </div>
  );
}

type StepState = "done" | "active" | "pending";

const SIGNUP_STEPS: Array<{ n: 1 | 2 | 3; title: string; desc: string }> = [
  { n: 1, title: "정보 입력", desc: "이메일과 비밀번호를 등록합니다" },
  { n: 2, title: "이메일 인증", desc: "받은 코드로 계정을 확인합니다" },
  { n: 3, title: "시작하기", desc: "바로 인스턴스를 생성할 수 있어요" },
];

export function AuthStepsPanel({ currentStep }: { currentStep: 1 | 2 | 3 }) {
  return (
    <div className="relative flex h-full flex-col justify-between p-10">
      <AuthVisualBackdrop />

      <div className="relative z-10">
        <VisualWordmark />
      </div>

      <div className="relative z-10">
        <h2 className="mb-2 text-[26px] font-extrabold leading-[1.15] tracking-[-0.03em] text-[#f4f7f1]">
          몇 걸음이면
          <br />
          <span className="bg-gradient-to-r from-[#eaffc8] via-[#baff4a] to-[#63ffad] bg-clip-text text-transparent">
            시작할 수 있어요.
          </span>
        </h2>
        <p className="mb-9 text-sm text-[#9aa39a]">가입부터 첫 인스턴스 생성까지, 3단계면 충분합니다.</p>

        <div>
          {SIGNUP_STEPS.map((step, i) => {
            const state: StepState = step.n < currentStep ? "done" : step.n === currentStep ? "active" : "pending";
            return (
              <div key={step.n}>
                <div
                  className={cn(
                    "flex items-center gap-3.5 rounded-xl border px-4 py-3.5 transition-colors",
                    state === "done" && "border-[#baff4a]/20 bg-[#baff4a]/[0.03]",
                    state === "active" && "border-[#baff4a]/40 shadow-[0_0_30px_rgba(186,255,74,0.08)]",
                    state === "pending" && "border-white/[0.07]"
                  )}
                >
                  <span
                    className={cn(
                      "flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-[11px] font-extrabold",
                      state === "done" && "bg-[#baff4a]/10 text-[#baff4a]",
                      state === "active" && "bg-[#baff4a] text-[#0b0e08]",
                      state === "pending" && "bg-white/[0.05] text-[#5f6862]"
                    )}
                  >
                    {state === "done" ? "✓" : step.n}
                  </span>
                  <div>
                    <p className={cn("text-[13px] font-bold", state === "pending" ? "text-[#6b746d]" : "text-[#e7ebe6]")}>
                      {step.title}
                    </p>
                    <p className="text-[11px] text-[#727b74]">{step.desc}</p>
                  </div>
                </div>
                {i < SIGNUP_STEPS.length - 1 && (
                  <div
                    className={cn("ml-[34px] h-5 w-px", state === "done" ? "bg-[#baff4a]/35" : "bg-white/10")}
                    aria-hidden
                  />
                )}
              </div>
            );
          })}
        </div>
      </div>

      <VisualFooter />
    </div>
  );
}
