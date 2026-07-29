"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewScenarioStageExecution,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/field";
import { BlueprintModalFrame } from "../blueprints/modals";
import {
  callCapability,
  extractArray,
  isPasswordLikeField,
  rowId,
} from "../api";
import type { PreviewCapability, PreviewRuntimeConfig } from "../types";
import {
  composeProductExperience,
  validateProductExperience,
  type ExperienceAction,
  type ExperienceOverlay,
  type ExperienceScreen,
  type ProductArchetype,
} from "./productExperience";
import {
  emptyExecution,
  runApiStage,
  type ScenarioState,
} from "./runtime";
import { ProductExperienceInspector } from "./ProductExperienceInspector";
import {
  selectProductExperienceTheme,
  type ProductExperienceTheme,
} from "./productTheme";

type Row = Record<string, unknown>;

const DAYS = ["월", "화", "수", "목", "금", "토", "일"];
const CALENDAR_DATES = Array.from({ length: 35 }, (_, index) => index - 2);

function textValue(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return "";
}

function titleOf(row: Row, index = 0): string {
  const keys = ["title", "name", "subject", "label", "summary", "email", "username"];
  for (const key of keys) {
    const value = textValue(row[key]);
    if (value) return value;
  }
  return `새로운 항목 ${index + 1}`;
}

function subtitleOf(row: Row): string {
  const keys = ["description", "content", "message", "category", "status", "type"];
  for (const key of keys) {
    const value = textValue(row[key]);
    if (value) return value;
  }
  return "자세한 내용을 확인하고 다음 작업을 이어갈 수 있습니다.";
}

function humanize(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replaceAll("_", " ")
    .replaceAll("-", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function parseValue(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed === "true") return true;
  if (trimmed === "false") return false;
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  return value;
}

function seedRows(archetype: ProductArchetype): Row[] {
  const common = [
    { id: "preview-1", name: "첫 번째 컬렉션", status: "진행 중", description: "최근 업데이트된 항목입니다." },
    { id: "preview-2", name: "함께 만드는 프로젝트", status: "새 소식", description: "팀과 공유된 새로운 활동이 있습니다." },
    { id: "preview-3", name: "저장한 아이디어", status: "보관됨", description: "나중에 다시 확인할 수 있습니다." },
    { id: "preview-4", name: "이번 주 추천", status: "추천", description: "취향과 활동을 바탕으로 골랐습니다." },
    { id: "preview-5", name: "새로운 시작", status: "초안", description: "작업을 이어서 완성해 보세요." },
    { id: "preview-6", name: "지난 활동", status: "완료", description: "정상적으로 마무리된 기록입니다." },
  ];
  if (archetype === "COMMERCE") {
    return [
      { id: "product-1", name: "Everyday Chair", category: "Living", price: "₩128,000", status: "오늘 출발" },
      { id: "product-2", name: "Soft Table Light", category: "Lighting", price: "₩64,000", status: "인기" },
      { id: "product-3", name: "Sunday Mug Set", category: "Kitchen", price: "₩32,000", status: "새 상품" },
      { id: "product-4", name: "Quiet Clock", category: "Object", price: "₩49,000", status: "추천" },
      { id: "product-5", name: "Linen Blanket", category: "Bedroom", price: "₩91,000", status: "재입고" },
      { id: "product-6", name: "Archive Shelf", category: "Storage", price: "₩175,000", status: "한정" },
    ];
  }
  if (archetype === "COMMUNITY") {
    return [
      { id: "post-1", name: "작은 팀에서 제품을 빠르게 만드는 법", author: "민서", status: "128개의 반응", description: "이번 주에 배운 시행착오를 정리해 봤어요." },
      { id: "post-2", name: "오늘 발견한 조용한 작업 공간", author: "도윤", status: "42개의 반응", description: "집중이 필요할 때 가기 좋은 곳을 공유합니다." },
      { id: "post-3", name: "사이드 프로젝트 첫 사용자 인터뷰", author: "하린", status: "89개의 반응", description: "예상과 달랐던 답변이 정말 많았습니다." },
    ];
  }
  if (archetype === "CONTENT") {
    return [
      { id: "draft-1", name: "좋은 제품 문장은 어디에서 오는가", status: "초안", updatedAt: "방금 전", description: "제품 언어와 사용자 경험에 관한 에세이" },
      { id: "draft-2", name: "여름호 인터뷰: 만드는 사람들", status: "검토 중", updatedAt: "어제", description: "세 명의 창작자와 나눈 긴 대화" },
      { id: "draft-3", name: "이번 주 큐레이션", status: "발행됨", updatedAt: "3일 전", description: "팀이 고른 새로운 영감과 도구" },
    ];
  }
  if (archetype === "BOOKING") {
    return [
      { id: "space-1", name: "성수 라운드 테이블", category: "6명", status: "예약 가능", price: "₩24,000 / 시간" },
      { id: "space-2", name: "한남 포커스 룸", category: "4명", status: "2자리 남음", price: "₩18,000 / 시간" },
      { id: "space-3", name: "을지로 스튜디오", category: "12명", status: "예약 가능", price: "₩45,000 / 시간" },
    ];
  }
  if (archetype === "MESSAGING") {
    return [
      { id: "thread-1", name: "제품 디자인 팀", message: "수정된 시안을 확인해 주세요.", status: "2분 전" },
      { id: "thread-2", name: "민서", message: "내일 미팅 시간을 옮겨도 될까요?", status: "18분 전" },
      { id: "thread-3", name: "새 고객 문의", message: "요금제에 관해 궁금한 점이 있어요.", status: "1시간 전" },
    ];
  }
  if (archetype === "FILES") {
    return [
      { id: "file-1", name: "Brand resources", type: "폴더", status: "12개 항목", updatedAt: "오늘" },
      { id: "file-2", name: "Product launch.pdf", type: "PDF", status: "4.8 MB", updatedAt: "어제" },
      { id: "file-3", name: "Interview notes", type: "문서", status: "공유됨", updatedAt: "월요일" },
      { id: "file-4", name: "Summer campaign", type: "폴더", status: "28개 항목", updatedAt: "지난주" },
    ];
  }
  if (archetype === "LEARNING") {
    return [
      { id: "course-1", name: "제품을 설명하는 글쓰기", category: "12개 레슨", status: "68% 완료", description: "짧고 분명한 제품 문장을 연습합니다." },
      { id: "course-2", name: "데이터로 질문하는 법", category: "8개 레슨", status: "32% 완료", description: "의사결정에 필요한 질문을 설계합니다." },
      { id: "course-3", name: "작은 팀의 리서치", category: "10개 레슨", status: "시작 전", description: "가볍지만 효과적인 리서치 방법을 배웁니다." },
    ];
  }
  return common;
}

function ProductActionButton({
  action,
  onClick,
}: {
  action: ExperienceAction;
  onClick: () => void;
}) {
  const style = action.tone === "PRIMARY"
    ? "border-[var(--px-accent)] bg-[var(--px-accent)] text-[var(--px-on-accent)] hover:border-[var(--px-accent-hover)] hover:bg-[var(--px-accent-hover)]"
    : action.tone === "DANGER"
      ? "border-[color-mix(in_srgb,var(--px-danger)_35%,transparent)] bg-[var(--px-danger-bg)] text-[var(--px-danger)] hover:border-[var(--px-danger)]"
      : "border-[var(--px-line)] bg-[var(--px-surface)] text-[var(--px-ink)] hover:bg-[var(--px-surface-soft)]";
  return (
    <button
      type="button"
      className={`inline-flex min-h-10 items-center gap-2 rounded-full border px-4 text-sm font-bold shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md ${style}`}
      onClick={onClick}
    >
      <span aria-hidden className="text-base leading-none">{action.icon}</span>
      {action.label}
    </button>
  );
}

function Card({
  row,
  index,
  selected,
  onSelect,
  visual = true,
}: {
  row: Row;
  index: number;
  selected: boolean;
  onSelect: () => void;
  visual?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`group overflow-hidden rounded-[22px] border bg-[var(--px-surface)] text-left shadow-[var(--px-shadow-sm)] transition-all duration-300 hover:-translate-y-1 hover:shadow-[var(--px-shadow-md)] ${
        selected ? "border-[var(--px-accent)] ring-2 ring-[color-mix(in_srgb,var(--px-accent)_14%,transparent)]" : "border-[var(--px-line)]"
      }`}
    >
      {visual && (
        <div
          className="h-32 border-b border-black/[0.04]"
          style={{
            background: [
              "linear-gradient(135deg,var(--px-visual-a),var(--px-visual-b))",
              "linear-gradient(135deg,var(--px-visual-c),var(--px-visual-d))",
              "linear-gradient(135deg,var(--px-visual-b),var(--px-visual-c))",
              "linear-gradient(135deg,var(--px-visual-d),var(--px-visual-a))",
            ][index % 4],
          }}
        >
          <div className="flex h-full items-end justify-between p-4">
            <span className="rounded-full bg-[color-mix(in_srgb,var(--px-surface)_82%,transparent)] px-2.5 py-1 text-[10px] font-black uppercase tracking-[.1em] text-[var(--px-ink)] backdrop-blur">
              {textValue(row.category) || textValue(row.type) || "Featured"}
            </span>
            <span className="translate-y-2 text-2xl opacity-0 transition-all group-hover:translate-y-0 group-hover:opacity-100">↗</span>
          </div>
        </div>
      )}
      <div className="p-4">
        <h3 className="line-clamp-1 text-[15px] font-black text-[var(--px-ink)]">{titleOf(row, index)}</h3>
        <p className="mt-1 line-clamp-2 min-h-10 text-xs leading-5 text-[var(--px-muted)]">{subtitleOf(row)}</p>
        <div className="mt-4 flex items-center justify-between gap-2">
          <span className="text-xs font-extrabold text-[var(--px-ink)]">
            {textValue(row.price) || textValue(row.author) || textValue(row.updatedAt) || "자세히 보기"}
          </span>
          <span className="rounded-full bg-[var(--px-tint)] px-2.5 py-1 text-[10px] font-bold text-[var(--px-accent)]">
            {textValue(row.status) || "활성"}
          </span>
        </div>
      </div>
    </button>
  );
}

function HomeScreen({ rows, onSelect }: { rows: Row[]; onSelect: (row: Row) => void }) {
  return (
    <div className="space-y-8">
      <section className="grid gap-4 md:grid-cols-[1.35fr_.65fr]">
        <button
          type="button"
          className="group min-h-72 overflow-hidden rounded-[28px] bg-[var(--px-hero)] p-7 text-left text-[var(--px-hero-ink)] shadow-xl"
          onClick={() => onSelect(rows[0])}
        >
          <p className="text-xs font-black uppercase tracking-[.18em] text-[var(--px-hero-muted)]">For you</p>
          <h2 className="mt-12 max-w-lg text-3xl font-black leading-tight md:text-4xl">
            오늘의 흐름을<br />가볍게 시작해 보세요.
          </h2>
          <p className="mt-4 max-w-md text-sm leading-6 text-[color-mix(in_srgb,var(--px-hero-ink)_62%,transparent)]">{subtitleOf(rows[0])}</p>
          <span className="mt-8 inline-flex items-center gap-2 text-sm font-bold text-[var(--px-hero-muted)]">
            이어서 보기 <span className="transition-transform group-hover:translate-x-1">→</span>
          </span>
        </button>
        <div className="grid gap-4">
          <div className="rounded-[24px] border border-[var(--px-line)] bg-[var(--px-tint)] p-6">
            <p className="text-xs font-bold text-[var(--px-muted)]">이번 주 활동</p>
            <strong className="mt-3 block text-4xl font-black text-[var(--px-ink)]">{Math.max(rows.length, 6)}</strong>
            <p className="mt-2 text-xs text-[var(--px-muted)]">지난주보다 활발하게 진행 중이에요.</p>
          </div>
          <div className="rounded-[24px] border border-[var(--px-line)] bg-[var(--px-secondary-tint)] p-6">
            <p className="text-xs font-bold text-[var(--px-muted)]">다음 할 일</p>
            <strong className="mt-3 block text-lg font-black text-[var(--px-ink)]">{titleOf(rows[1], 1)}</strong>
            <p className="mt-2 text-xs text-[var(--px-muted)]">필요한 작업을 이어서 완료하세요.</p>
          </div>
        </div>
      </section>
      <section>
        <div className="mb-4 flex items-end justify-between">
          <div>
            <p className="text-xs font-bold text-[var(--px-muted)]">최근 항목</p>
            <h2 className="mt-1 text-xl font-black text-[var(--px-ink)]">다시 이어서 하기</h2>
          </div>
          <button className="text-xs font-bold text-[var(--px-accent)]" type="button">모두 보기 →</button>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {rows.slice(0, 3).map((row, index) => (
            <Card key={rowId(row)} row={row} index={index} selected={false} onSelect={() => onSelect(row)} />
          ))}
        </div>
      </section>
    </div>
  );
}

function CalendarScreen({ rows, onSelect }: { rows: Row[]; onSelect: (row: Row) => void }) {
  return (
    <section className="overflow-hidden rounded-[26px] border border-[var(--px-line)] bg-[var(--px-surface)] shadow-sm">
      <div className="flex items-center justify-between border-b border-[var(--px-line)] px-6 py-5">
        <div>
          <p className="text-xs font-bold text-[var(--px-muted)]">2026년</p>
          <h2 className="mt-1 text-xl font-black text-[var(--px-ink)]">7월</h2>
        </div>
        <div className="flex gap-2">
          <button type="button" className="grid h-9 w-9 place-items-center rounded-full border border-[var(--px-line)]">‹</button>
          <button type="button" className="grid h-9 w-9 place-items-center rounded-full border border-[var(--px-line)]">›</button>
        </div>
      </div>
      <div className="grid grid-cols-7 border-b border-[var(--px-line)] bg-[var(--px-surface-soft)]">
        {DAYS.map((day) => <div key={day} className="px-2 py-3 text-center text-[11px] font-black text-[var(--px-muted)]">{day}</div>)}
      </div>
      <div className="grid grid-cols-7">
        {CALENDAR_DATES.map((date, index) => {
          const row = rows[index % rows.length];
          const active = index === 17;
          return (
            <button
              type="button"
              key={`${date}-${index}`}
              onClick={() => onSelect(row)}
              className={`min-h-24 border-b border-r border-[var(--px-line)] p-2 text-left transition-colors hover:bg-[var(--px-surface-soft)] ${
                date < 1 || date > 31 ? "text-[var(--px-subtle)]" : "text-[var(--px-ink)]"
              }`}
            >
              <span className={`grid h-6 w-6 place-items-center rounded-full text-[11px] font-bold ${active ? "bg-[var(--px-accent)] text-[var(--px-on-accent)]" : ""}`}>
                {date < 1 ? 30 + date : date > 31 ? date - 31 : date}
              </span>
              {index % 6 === 1 && (
                <span className="mt-2 block truncate rounded-md bg-[var(--px-tint)] px-2 py-1 text-[9px] font-bold text-[var(--px-accent)]">
                  {titleOf(row, index)}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}

function FeedScreen({ rows, onSelect }: { rows: Row[]; onSelect: (row: Row) => void }) {
  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="rounded-[22px] border border-[var(--px-line)] bg-[var(--px-surface)] p-4 shadow-sm">
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-full bg-[var(--px-tint-strong)] text-sm font-black text-[var(--px-accent)]">나</span>
          <button type="button" className="h-11 flex-1 rounded-full bg-[var(--px-surface-soft)] px-5 text-left text-sm text-[var(--px-subtle)]">
            무슨 생각을 하고 있나요?
          </button>
        </div>
      </div>
      {rows.map((row, index) => (
        <article key={rowId(row)} className="rounded-[24px] border border-[var(--px-line)] bg-[var(--px-surface)] p-5 shadow-sm">
          <button type="button" onClick={() => onSelect(row)} className="w-full text-left">
            <div className="flex items-center gap-3">
              <span className="grid h-11 w-11 place-items-center rounded-full bg-[var(--px-secondary-tint)] font-black text-[var(--px-secondary)]">
                {textValue(row.author).slice(0, 1) || titleOf(row, index).slice(0, 1)}
              </span>
              <div>
                <strong className="text-sm text-[var(--px-ink)]">{textValue(row.author) || "새로운 이웃"}</strong>
                <p className="text-[11px] text-[var(--px-subtle)]">{index + 2}시간 전 · 모두에게 공개</p>
              </div>
            </div>
            <h3 className="mt-5 text-lg font-black text-[var(--px-ink)]">{titleOf(row, index)}</h3>
            <p className="mt-2 text-sm leading-6 text-[var(--px-muted)]">{subtitleOf(row)}</p>
            <div className="mt-5 h-48 rounded-[18px]" style={{ background: index % 2 ? "linear-gradient(135deg,var(--px-visual-c),var(--px-visual-d))" : "linear-gradient(135deg,var(--px-visual-a),var(--px-visual-b))" }} />
          </button>
          <div className="mt-4 flex items-center gap-5 border-t border-[var(--px-line)] pt-4 text-xs font-bold text-[var(--px-muted)]">
            <button type="button">♡ 좋아요</button>
            <button type="button">◯ 댓글</button>
            <button type="button">↗ 공유</button>
          </div>
        </article>
      ))}
    </div>
  );
}

function InboxScreen({ rows, selected, onSelect }: { rows: Row[]; selected: Row | null; onSelect: (row: Row) => void }) {
  const active = selected ?? rows[0];
  return (
    <section className="grid min-h-[560px] overflow-hidden rounded-[26px] border border-[var(--px-line)] bg-[var(--px-surface)] shadow-sm md:grid-cols-[320px_1fr]">
      <div className="border-r border-[var(--px-line)]">
        <div className="border-b border-[var(--px-line)] p-4">
          <Input className="border-0 bg-[var(--px-surface-soft)] text-[var(--px-ink)]" placeholder="대화 검색" />
        </div>
        <div className="divide-y divide-[var(--px-line)]">
          {rows.map((row, index) => (
            <button
              type="button"
              key={rowId(row)}
              onClick={() => onSelect(row)}
              className={`flex w-full items-start gap-3 p-4 text-left transition-colors ${
                rowId(active) === rowId(row) ? "bg-[var(--px-tint)]" : "hover:bg-[var(--px-surface-soft)]"
              }`}
            >
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[var(--px-secondary-tint)] text-xs font-black text-[var(--px-secondary)]">
                {titleOf(row, index).slice(0, 1)}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex justify-between gap-2">
                  <strong className="truncate text-sm text-[var(--px-ink)]">{titleOf(row, index)}</strong>
                  <span className="shrink-0 text-[9px] text-[var(--px-subtle)]">{textValue(row.status)}</span>
                </div>
                <p className="mt-1 truncate text-xs text-[var(--px-muted)]">{textValue(row.message) || subtitleOf(row)}</p>
              </div>
            </button>
          ))}
        </div>
      </div>
      <div className="flex min-w-0 flex-col">
        <header className="flex items-center justify-between border-b border-[var(--px-line)] px-6 py-4">
          <div>
            <strong className="text-sm text-[var(--px-ink)]">{titleOf(active)}</strong>
            <p className="text-[10px] text-[var(--px-muted)]">지금 대화 가능</p>
          </div>
          <button type="button" className="grid h-9 w-9 place-items-center rounded-full border border-[var(--px-line)]">•••</button>
        </header>
        <div className="flex flex-1 flex-col justify-end gap-3 bg-[var(--px-surface-soft)] p-6">
          <div className="max-w-[72%] rounded-[18px_18px_18px_4px] bg-[var(--px-surface)] p-4 text-sm leading-6 text-[var(--px-muted)] shadow-sm">
            {textValue(active.message) || subtitleOf(active)}
          </div>
          <div className="ml-auto max-w-[72%] rounded-[18px_18px_4px_18px] bg-[var(--px-accent)] p-4 text-sm leading-6 text-[var(--px-on-accent)]">
            확인했어요. 조금 더 자세한 내용을 알려드릴게요.
          </div>
        </div>
        <div className="border-t border-[var(--px-line)] bg-[var(--px-surface)] p-4">
          <div className="flex items-end gap-2 rounded-[18px] bg-[var(--px-surface-soft)] p-2 pl-4">
            <textarea className="min-h-10 flex-1 resize-none bg-transparent py-2 text-sm text-[var(--px-ink)] outline-none" placeholder="메시지 입력" />
            <button type="button" className="grid h-10 w-10 place-items-center rounded-full bg-[var(--px-accent)] text-[var(--px-on-accent)]">↑</button>
          </div>
        </div>
      </div>
    </section>
  );
}

function EditorScreen() {
  return (
    <section className="grid min-h-[580px] overflow-hidden rounded-[26px] border border-[var(--px-line)] bg-[var(--px-surface)] shadow-sm lg:grid-cols-[1fr_280px]">
      <div className="p-7 md:p-10">
        <div className="mx-auto max-w-3xl">
          <input
            defaultValue="제목 없는 이야기"
            className="w-full bg-transparent text-3xl font-black text-[var(--px-ink)] outline-none placeholder:text-[var(--px-subtle)]"
          />
          <div className="mt-5 flex items-center gap-3 border-b border-[var(--px-line)] pb-5 text-xs font-bold text-[var(--px-muted)]">
            <button type="button">B</button><button type="button" className="italic">I</button>
            <span className="h-4 w-px bg-[var(--px-line-strong)]" />
            <button type="button">링크</button><button type="button">이미지</button><button type="button">인용</button>
          </div>
          <textarea
            className="mt-7 min-h-[390px] w-full resize-none bg-transparent text-base leading-8 text-[var(--px-muted)] outline-none"
            placeholder="당신의 이야기를 시작하세요..."
          />
        </div>
      </div>
      <aside className="border-l border-[var(--px-line)] bg-[var(--px-surface-soft)] p-5">
        <p className="text-[10px] font-black uppercase tracking-[.15em] text-[var(--px-subtle)]">Document</p>
        <div className="mt-5 space-y-5">
          <Field label="상태"><Input defaultValue="초안" className="bg-[var(--px-surface)] text-[var(--px-ink)]" /></Field>
          <Field label="카테고리"><Input placeholder="카테고리 선택" className="bg-[var(--px-surface)] text-[var(--px-ink)]" /></Field>
          <Field label="요약"><Textarea placeholder="독자에게 보일 짧은 설명" className="bg-[var(--px-surface)] text-[var(--px-ink)]" /></Field>
        </div>
      </aside>
    </section>
  );
}

function FilesScreen({ rows, onSelect }: { rows: Row[]; onSelect: (row: Row) => void }) {
  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-xs font-bold text-[var(--px-muted)]">
          <span>내 파일</span><span>/</span><span className="text-[var(--px-ink)]">모든 항목</span>
        </div>
        <div className="flex rounded-full border border-[var(--px-line)] bg-[var(--px-surface)] p-1 text-xs font-bold">
          <button type="button" className="rounded-full bg-[var(--px-tint)] px-3 py-1.5">격자</button>
          <button type="button" className="px-3 py-1.5 text-[var(--px-muted)]">목록</button>
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {rows.map((row, index) => (
          <button
            key={rowId(row)}
            type="button"
            onClick={() => onSelect(row)}
            className="rounded-[20px] border border-[var(--px-line)] bg-[var(--px-surface)] p-5 text-left shadow-sm transition-all hover:-translate-y-1 hover:shadow-md"
          >
            <span className={`grid h-12 w-12 place-items-center rounded-[14px] text-xl ${index % 2 ? "bg-[var(--px-secondary-tint)] text-[var(--px-secondary)]" : "bg-[var(--px-tint)] text-[var(--px-accent)]"}`}>
              {textValue(row.type) === "폴더" ? "▰" : "◇"}
            </span>
            <strong className="mt-5 block truncate text-sm text-[var(--px-ink)]">{titleOf(row, index)}</strong>
            <p className="mt-1 text-[10px] text-[var(--px-subtle)]">{textValue(row.status) || "최근 업데이트"}</p>
          </button>
        ))}
      </div>
    </div>
  );
}

function BookingScreen({ rows, onSelect }: { rows: Row[]; onSelect: (row: Row) => void }) {
  const times = ["09:00", "10:30", "12:00", "13:30", "15:00", "16:30", "18:00"];
  return (
    <div className="grid gap-5 lg:grid-cols-[.75fr_1.25fr]">
      <div className="rounded-[24px] border border-[var(--px-line)] bg-[var(--px-surface)] p-5 shadow-sm">
        <p className="text-xs font-black text-[var(--px-muted)]">공간</p>
        <div className="mt-4 space-y-2">
          {rows.slice(0, 4).map((row, index) => (
            <button key={rowId(row)} type="button" onClick={() => onSelect(row)} className={`w-full rounded-[16px] border p-4 text-left ${index === 0 ? "border-[var(--px-accent)] bg-[var(--px-tint)]" : "border-[var(--px-line)]"}`}>
              <strong className="text-sm text-[var(--px-ink)]">{titleOf(row, index)}</strong>
              <p className="mt-1 text-[10px] text-[var(--px-muted)]">{textValue(row.category) || textValue(row.status)}</p>
            </button>
          ))}
        </div>
      </div>
      <div className="rounded-[24px] border border-[var(--px-line)] bg-[var(--px-surface)] p-5 shadow-sm">
        <div className="flex items-center justify-between">
          <div><p className="text-xs text-[var(--px-muted)]">7월 30일</p><h3 className="mt-1 text-lg font-black text-[var(--px-ink)]">가능한 시간</h3></div>
          <div className="flex gap-1">{["30", "31", "1"].map((date, index) => <button key={date} type="button" className={`grid h-10 w-10 place-items-center rounded-full text-xs font-bold ${index === 0 ? "bg-[var(--px-accent)] text-[var(--px-on-accent)]" : "bg-[var(--px-surface-soft)] text-[var(--px-muted)]"}`}>{date}</button>)}</div>
        </div>
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {times.map((time, index) => (
            <button key={time} type="button" className={`rounded-[14px] border px-4 py-4 text-sm font-bold transition-colors ${index === 3 ? "border-[var(--px-accent)] bg-[var(--px-accent)] text-[var(--px-on-accent)]" : "border-[var(--px-line)] text-[var(--px-ink)] hover:bg-[var(--px-surface-soft)]"}`}>{time}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

function ProfileScreen({ rows, onSelect }: { rows: Row[]; onSelect: (row: Row) => void }) {
  return (
    <div className="space-y-5">
      <section className="rounded-[28px] border border-[var(--px-line)] bg-[var(--px-surface)] p-7 shadow-sm">
        <div className="flex flex-wrap items-center gap-5">
          <span className="grid h-20 w-20 place-items-center rounded-full bg-gradient-to-br from-[var(--px-visual-a)] to-[var(--px-visual-b)] text-2xl font-black text-[var(--px-accent)]">ME</span>
          <div className="flex-1">
            <h2 className="text-2xl font-black text-[var(--px-ink)]">나의 공간</h2>
            <p className="mt-1 text-sm text-[var(--px-muted)]">내 활동과 저장된 기록을 한곳에서 확인하세요.</p>
          </div>
          <button type="button" className="rounded-full border border-[var(--px-line)] px-4 py-2 text-xs font-bold text-[var(--px-muted)]">프로필 편집</button>
        </div>
        <div className="mt-7 grid grid-cols-3 gap-3 border-t border-[var(--px-line)] pt-6 text-center">
          {[["활동", rows.length], ["완료", Math.max(2, rows.length - 1)], ["저장됨", Math.max(3, rows.length + 2)]].map(([label, value]) => (
            <div key={String(label)}><strong className="block text-xl text-[var(--px-ink)]">{value}</strong><span className="text-[10px] font-bold text-[var(--px-subtle)]">{label}</span></div>
          ))}
        </div>
      </section>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {rows.slice(0, 3).map((row, index) => <Card key={rowId(row)} row={row} index={index} selected={false} onSelect={() => onSelect(row)} visual={false} />)}
      </div>
    </div>
  );
}

function ScreenContent({
  screen,
  rows,
  selected,
  onSelect,
}: {
  screen: ExperienceScreen;
  rows: Row[];
  selected: Row | null;
  onSelect: (row: Row) => void;
}) {
  if (screen.kind === "HOME") return <HomeScreen rows={rows} onSelect={onSelect} />;
  if (screen.kind === "CALENDAR") return <CalendarScreen rows={rows} onSelect={onSelect} />;
  if (screen.kind === "FEED") return <FeedScreen rows={rows} onSelect={onSelect} />;
  if (screen.kind === "INBOX") return <InboxScreen rows={rows} selected={selected} onSelect={onSelect} />;
  if (screen.kind === "EDITOR") return <EditorScreen />;
  if (screen.kind === "FILES") return <FilesScreen rows={rows} onSelect={onSelect} />;
  if (screen.kind === "BOOKING") return <BookingScreen rows={rows} onSelect={onSelect} />;
  if (screen.kind === "PROFILE") return <ProfileScreen rows={rows} onSelect={onSelect} />;
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {rows.map((row, index) => (
        <Card
          key={rowId(row)}
          row={row}
          index={index}
          selected={selected ? rowId(selected) === rowId(row) : false}
          onSelect={() => onSelect(row)}
        />
      ))}
    </div>
  );
}

function DetailOverlay({
  row,
  open,
  actions,
  theme,
  onClose,
  onAction,
}: {
  row: Row | null;
  open: boolean;
  actions: ExperienceAction[];
  theme: ProductExperienceTheme;
  onClose: () => void;
  onAction: (action: ExperienceAction) => void;
}) {
  if (!row) return null;
  const entries = Object.entries(row).filter(([, value]) => value !== null && value !== undefined).slice(0, 12);
  return (
    <BlueprintModalFrame
      open={open}
      onClose={onClose}
      title={titleOf(row)}
      description={subtitleOf(row)}
      eyebrow="Details"
      size="lg"
      style={theme.style}
      themeId={theme.blueprintThemeId}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        {entries.map(([key, value]) => (
          <div key={key} className="rounded-[14px] border border-line bg-panel p-4">
            <p className="text-[10px] font-black uppercase tracking-[.1em] text-muted-soft">{humanize(key)}</p>
            <p className="mt-2 break-words text-sm font-semibold">
              {typeof value === "object" ? JSON.stringify(value) : String(value)}
            </p>
          </div>
        ))}
      </div>
      {actions.length > 0 && (
        <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-5">
          {actions.map((action) => (
            <Button
              key={action.id}
              variant={action.tone === "DANGER" ? "danger" : action.tone === "PRIMARY" ? "primary" : "secondary"}
              onClick={() => onAction(action)}
            >
              {action.label}
            </Button>
          ))}
        </div>
      )}
    </BlueprintModalFrame>
  );
}

function overlayFields(
  overlay: ExperienceOverlay,
  scenario: PreviewCompiledScenario,
  capabilities: PreviewCapability[]
): string[] {
  const stages = scenario.stages.filter((stage) => overlay.stageIds.includes(stage.id));
  const local = stages.flatMap((stage) => [...stage.outputs, ...stage.inputs]);
  const capabilityFields = stages.flatMap((stage) =>
    capabilities.find((capability) => capability.id === stage.capabilityId)?.fields ?? []
  );
  return Array.from(new Set([...local, ...capabilityFields]))
    .filter((field) => !/^(id|createdAt|updatedAt|token|accessToken)$/i.test(field))
    .slice(0, 8);
}

function ActionOverlay({
  action,
  overlay,
  scenario,
  capabilities,
  draft,
  selected,
  busy,
  error,
  theme,
  onDraft,
  onClose,
  onContinue,
  onExecute,
}: {
  action: ExperienceAction;
  overlay: ExperienceOverlay;
  scenario: PreviewCompiledScenario;
  capabilities: PreviewCapability[];
  draft: Record<string, string>;
  selected: Row | null;
  busy: boolean;
  error: string | null;
  theme: ProductExperienceTheme;
  onDraft: (field: string, value: string) => void;
  onClose: () => void;
  onContinue: () => void;
  onExecute: () => void;
}) {
  const fields = overlayFields(overlay, scenario, capabilities);
  const isForm = overlay.kind === "FORM_MODAL";
  const isDanger = overlay.kind === "DANGER_CONFIRM";
  const isReview = overlay.kind === "REVIEW_MODAL";
  const isProgress = overlay.kind === "PROGRESS_MODAL";
  const isResult = overlay.kind === "RESULT_TOAST";
  const isDetail = overlay.kind === "DETAIL_DRAWER";

  function submit(event: FormEvent) {
    event.preventDefault();
    onContinue();
  }

  let content: ReactNode;
  if (isForm) {
    content = (
      <form id={`experience-form-${overlay.id}`} onSubmit={submit}>
        {fields.length === 0 ? (
          <p className="rounded-[14px] border border-line bg-panel p-4 text-sm text-muted">
            추가 입력 없이 다음 단계로 진행할 수 있습니다.
          </p>
        ) : fields.map((field) => (
          <Field key={field} label={humanize(field)} htmlFor={`${overlay.id}-${field}`}>
            {/(description|content|message|reason|note|body)/i.test(field) ? (
              <Textarea id={`${overlay.id}-${field}`} value={draft[field] ?? ""} onChange={(event) => onDraft(field, event.target.value)} />
            ) : (
              <Input id={`${overlay.id}-${field}`} type={isPasswordLikeField(field) ? "password" : "text"} value={draft[field] ?? ""} onChange={(event) => onDraft(field, event.target.value)} />
            )}
          </Field>
        ))}
      </form>
    );
  } else if (isProgress) {
    content = (
      <div className="rounded-[16px] border border-line bg-panel p-5">
        <div className="flex items-center gap-4">
          <span className={`h-10 w-10 rounded-full border-[3px] border-line border-t-brand ${busy ? "animate-spin" : ""}`} />
          <div>
            <strong className="text-sm">{error ? "작업을 완료하지 못했습니다" : busy ? "변경 사항을 반영하고 있어요" : "실행할 준비가 되었습니다"}</strong>
            <p className={`mt-1 text-xs leading-5 ${error ? "text-danger" : "text-muted-soft"}`}>
              {error ?? (busy ? "완료될 때까지 이 화면에서 상태를 확인할 수 있습니다." : "아래 버튼을 눌러 작업을 시작하세요.")}
            </p>
          </div>
        </div>
      </div>
    );
  } else if (isResult) {
    content = (
      <div className="flex items-start gap-4 rounded-[16px] border border-brand/30 bg-brand/10 p-5">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-brand text-xl font-black text-[var(--px-on-accent)]">✓</span>
        <div><strong className="text-sm">요청한 작업을 완료했습니다.</strong><p className="mt-1 text-xs leading-5 text-muted">화면 데이터에도 최신 결과를 반영했습니다.</p></div>
      </div>
    );
  } else if (isDanger) {
    content = (
      <div className="space-y-4">
        <div className="rounded-[15px] border border-danger/30 bg-danger/10 p-4 text-sm leading-6 text-danger">
          이 작업은 데이터에 중요한 변경을 만들 수 있습니다. 대상과 내용을 다시 확인해 주세요.
        </div>
        <div className="rounded-[14px] border border-line bg-panel p-4">
          <p className="text-[10px] font-black uppercase tracking-[.1em] text-muted-soft">선택한 대상</p>
          <strong className="mt-2 block text-sm">{selected ? titleOf(selected) : "현재 선택된 항목"}</strong>
        </div>
      </div>
    );
  } else if (isReview) {
    content = (
      <div className="space-y-3">
        {Object.entries(draft).length > 0 && Object.entries(draft).map(([key, value]) => (
          <div key={key} className="flex items-start justify-between gap-4 rounded-[13px] border border-line bg-panel px-4 py-3">
            <span className="text-xs font-bold text-muted">{humanize(key)}</span>
            <span className="max-w-[65%] break-words text-right text-xs font-semibold">{isPasswordLikeField(key) ? "••••••" : value}</span>
          </div>
        ))}
        {Object.keys(draft).length === 0 && <p className="rounded-[14px] border border-line bg-panel p-4 text-sm text-muted">선택한 항목과 현재 설정으로 작업을 진행합니다.</p>}
      </div>
    );
  } else if (isDetail) {
    content = selected ? (
      <div className="grid gap-3 sm:grid-cols-2">
        {Object.entries(selected).slice(0, 10).map(([key, value]) => (
          <div key={key} className="rounded-[13px] border border-line bg-panel p-4">
            <p className="text-[10px] font-black uppercase tracking-[.1em] text-muted-soft">{humanize(key)}</p>
            <p className="mt-2 break-words text-sm font-semibold">{typeof value === "object" ? JSON.stringify(value) : String(value)}</p>
          </div>
        ))}
      </div>
    ) : <p className="rounded-[14px] border border-line bg-panel p-4 text-sm text-muted">목록에서 확인할 항목을 먼저 선택해 주세요.</p>;
  } else {
    content = null;
  }

  const footer = isForm ? (
    <><Button onClick={onClose}>취소</Button><Button type="submit" form={`experience-form-${overlay.id}`} variant="primary">계속</Button></>
  ) : isProgress ? (
    <><Button onClick={onClose}>닫기</Button><Button variant="primary" disabled={busy} onClick={onExecute}>{error ? "다시 시도" : busy ? "처리 중..." : "실행"}</Button></>
  ) : isResult ? (
    <Button variant="primary" onClick={onClose}>완료</Button>
  ) : (
    <><Button onClick={onClose}>취소</Button><Button variant={isDanger ? "danger-solid" : "primary"} onClick={isDetail ? onExecute : onContinue}>{isDanger ? "확인하고 진행" : isDetail ? "최신 정보 확인" : "계속"}</Button></>
  );

  return (
    <BlueprintModalFrame
      open
      onClose={onClose}
      title={action.label}
      description={overlay.title}
      eyebrow={isDanger ? "Please confirm" : undefined}
      size={isDetail ? "lg" : "md"}
      footer={footer}
      style={theme.style}
      themeId={theme.blueprintThemeId}
    >
      {content}
    </BlueprintModalFrame>
  );
}

export function ProductExperienceRuntime({
  scenarios,
  capabilities,
  config,
}: {
  scenarios: PreviewCompiledScenario[];
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
}) {
  const graph = useMemo(
    () => composeProductExperience(scenarios, capabilities),
    [scenarios, capabilities]
  );
  const theme = useMemo(
    () => selectProductExperienceTheme(graph.archetype, scenarios, capabilities),
    [graph.archetype, scenarios, capabilities]
  );
  const validationErrors = useMemo(
    () => validateProductExperience(graph, scenarios),
    [graph, scenarios]
  );
  const [activeScreenId, setActiveScreenId] = useState(() => {
    if (typeof window === "undefined") return graph.defaultScreenId;
    const requested = new URLSearchParams(window.location.search).get("experience");
    return graph.screens.some((screen) => screen.id === requested) ? requested! : graph.defaultScreenId;
  });
  const [rowsByCapability, setRowsByCapability] = useState<Record<string, Row[]>>({});
  const [loadingCollections, setLoadingCollections] = useState(false);
  const [selected, setSelected] = useState<Row | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [activeActionId, setActiveActionId] = useState<string | null>(null);
  const [lastActionId, setLastActionId] = useState<string | null>(null);
  const [overlayIndex, setOverlayIndex] = useState(0);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [scenarioState, setScenarioState] = useState<ScenarioState>({});
  const stateRef = useRef<ScenarioState>({});
  const [executions, setExecutions] = useState<Record<string, PreviewScenarioStageExecution>>({});
  const [executionTimeline, setExecutionTimeline] = useState<PreviewScenarioStageExecution[]>([]);
  const [currentStageId, setCurrentStageId] = useState<string | null>(null);
  const [rawBodyDrafts, setRawBodyDrafts] = useState<Record<string, string>>({});
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const listCapabilities = useMemo(
    () => capabilities.filter((capability) => capability.type === "LIST" && capability.risk === "SAFE"),
    [capabilities]
  );

  async function loadCollections(signal?: AbortSignal) {
    if (!config.apiBaseUrl.trim() || listCapabilities.length === 0) return;
    setLoadingCollections(true);
    try {
      const settled = await Promise.allSettled(
        listCapabilities.map(async (capability) => {
          const response = await callCapability(config, capability, { signal });
          return [capability.id, extractArray(response, capability.collectionPath)] as const;
        })
      );
      const next = Object.fromEntries(
        settled
          .filter((result): result is PromiseFulfilledResult<readonly [string, Row[]]> => result.status === "fulfilled")
          .map((result) => result.value)
          .filter(([, rows]) => rows.length > 0)
      );
      if (!signal?.aborted) setRowsByCapability((current) => ({ ...current, ...next }));
    } finally {
      if (!signal?.aborted) setLoadingCollections(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => void loadCollections(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
    // URL이나 목록 capability 구성이 달라졌을 때만 초기 데이터를 다시 읽는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.apiBaseUrl, listCapabilities]);

  useEffect(() => {
    const syncScreenFromHistory = () => {
      const requested = new URLSearchParams(window.location.search).get("experience");
      if (requested && graph.screens.some((screen) => screen.id === requested)) {
        setActiveScreenId(requested);
      }
    };
    window.addEventListener("popstate", syncScreenFromHistory);
    return () => window.removeEventListener("popstate", syncScreenFromHistory);
  }, [graph.screens]);

  const activeScreen = graph.screens.find((screen) => screen.id === activeScreenId)
    ?? graph.screens[0];
  const liveRows = activeScreen?.capabilityIds.flatMap((capabilityId) => rowsByCapability[capabilityId] ?? []) ?? [];
  const rows = liveRows.length > 0 ? liveRows : seedRows(graph.archetype);
  const screenActions = graph.actions.filter((action) => action.screenId === activeScreen?.id);
  const activeAction = graph.actions.find((action) => action.id === activeActionId) ?? null;
  const activeScenario = scenarios.find((scenario) => scenario.id === activeAction?.scenarioId) ?? null;
  const inspectedAction = activeAction
    ?? graph.actions.find((action) => action.id === lastActionId)
    ?? null;
  const inspectedScenario = scenarios.find((scenario) => scenario.id === inspectedAction?.scenarioId) ?? null;
  const actionOverlays = activeAction
    ? activeAction.overlayIds
      .map((id) => graph.overlays.find((overlay) => overlay.id === id))
      .filter((overlay): overlay is ExperienceOverlay => Boolean(overlay))
    : [];
  const activeOverlay = actionOverlays[overlayIndex] ?? null;

  function navigateScreen(screenId: string) {
    if (screenId === activeScreenId) return;
    const query = new URLSearchParams(window.location.search);
    query.set("experience", screenId);
    window.history.pushState(null, "", `${window.location.pathname}?${query.toString()}`);
    setActiveScreenId(screenId);
    setDetailOpen(false);
  }

  function selectRow(row: Row, open = true) {
    setSelected(row);
    const next = { ...stateRef.current, selectedId: rowId(row), selectedRecord: row };
    stateRef.current = next;
    setScenarioState(next);
    if (open && activeScreen.kind !== "INBOX") setDetailOpen(true);
  }

  function openAction(action: ExperienceAction) {
    setDetailOpen(false);
    setActiveActionId(action.id);
    setLastActionId(action.id);
    setOverlayIndex(0);
    setDraft({});
    setActionError(null);
  }

  function closeAction() {
    abortRef.current?.abort();
    setActiveActionId(null);
    setOverlayIndex(0);
    setDraft({});
    setActionError(null);
    setBusy(false);
  }

  function recordExecution(execution: PreviewScenarioStageExecution) {
    setExecutions((current) => ({ ...current, [execution.stageId]: execution }));
    if (execution.status !== "IDLE") {
      setExecutionTimeline((current) => [...current, execution].slice(-80));
    }
  }

  function saveLocalStages(stages: PreviewCompiledScenarioStage[]) {
    const parsed = Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, parseValue(value)]));
    const next = { ...stateRef.current, ...parsed };
    for (const stage of stages) {
      for (const output of stage.outputs) {
        if (next[output] === undefined && parsed[output] !== undefined) next[output] = parsed[output];
      }
    }
    stateRef.current = next;
    setScenarioState(next);
  }

  function advanceOverlay() {
    if (!activeOverlay || !activeScenario) return;
    const stages = activeScenario.stages.filter((stage) => activeOverlay.stageIds.includes(stage.id));
    if (activeOverlay.kind === "FORM_MODAL") saveLocalStages(stages);
    if (overlayIndex < actionOverlays.length - 1) {
      setOverlayIndex((index) => index + 1);
      setActionError(null);
      return;
    }
    void executeAction();
  }

  async function executeAction(startStageId?: string) {
    const executionAction = activeAction ?? inspectedAction;
    const executionScenario = activeScenario ?? inspectedScenario;
    if (!executionAction || !executionScenario || busy) return;
    setBusy(true);
    setActionError(null);
    const controller = new AbortController();
    abortRef.current = controller;
    let nextState = { ...stateRef.current };
    if (selected) {
      nextState.selectedId = rowId(selected);
      nextState.selectedRecord = selected;
    }
    nextState = {
      ...nextState,
      ...Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, parseValue(value)])),
    };
    let reachedStart = !startStageId;
    let failedStage: PreviewCompiledScenarioStage | null = null;
    let failureRecorded = false;
    try {
      for (const stage of executionScenario.stages) {
        if (!reachedStart) {
          reachedStart = stage.id === startStageId;
          if (!reachedStart) continue;
        }
        setCurrentStageId(stage.id);
        failedStage = stage;
        failureRecorded = false;
        if (controller.signal.aborted) throw new Error("작업을 취소했습니다.");
        if (stage.role === "PREPARE" || stage.role === "CONFIGURE" || stage.role === "SELECT_CONTEXT" || stage.role === "REVIEW") {
          recordExecution({ ...emptyExecution(stage), status: "SUCCESS", durationMs: 0, completedAt: Date.now() });
          continue;
        }
        if (stage.role === "SELECT") {
          if (!nextState.selectedId) {
            const firstRow = rows[0];
            nextState.selectedId = rowId(firstRow);
            nextState.selectedRecord = firstRow;
          }
          stateRef.current = nextState;
          setScenarioState({ ...nextState });
          recordExecution({
            ...emptyExecution(stage),
            status: "SUCCESS",
            extractedOutputs: { selectedId: nextState.selectedId },
            durationMs: 0,
            completedAt: Date.now(),
          });
          continue;
        }
        if (stage.role === "COMPLETE" || !stage.capabilityId) {
          recordExecution({ ...emptyExecution(stage), status: "SUCCESS", durationMs: 0, completedAt: Date.now() });
          continue;
        }
        const capability = capabilities.find((candidate) => candidate.id === stage.capabilityId);
        if (!capability) throw new Error("연결된 API 작업을 찾지 못했습니다.");
        let requestOverride;
        const rawBody = rawBodyDrafts[stage.id];
        if (rawBody?.trim()) {
          const parsed = JSON.parse(rawBody);
          if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            throw new Error("원시 요청 본문은 JSON 객체여야 합니다.");
          }
          requestOverride = { body: parsed as Record<string, unknown> };
        }
        recordExecution({ ...emptyExecution(stage), status: "RUNNING", method: capability.method, startedAt: Date.now() });
        let result = await runApiStage({
          stage,
          capability,
          state: nextState,
          config,
          signal: controller.signal,
          requestOverride,
        });
        recordExecution(result.execution);
        failureRecorded = result.execution.status !== "SUCCESS";
        if (stage.role === "TRACK" && result.execution.status === "FAILED") {
          for (let attempt = 0; attempt < 3 && result.execution.status === "FAILED"; attempt += 1) {
            await new Promise<void>((resolve) => window.setTimeout(resolve, 1500));
            recordExecution({ ...emptyExecution(stage), status: "RUNNING", method: capability.method, startedAt: Date.now() });
            result = await runApiStage({
              stage,
              capability,
              state: nextState,
              config,
              signal: controller.signal,
              requestOverride,
            });
            recordExecution(result.execution);
            failureRecorded = result.execution.status !== "SUCCESS";
          }
        }
        if (result.execution.status !== "SUCCESS") {
          throw new Error(result.execution.error ?? `${stage.intent} 작업에 실패했습니다.`);
        }
        nextState = result.nextState;
        stateRef.current = nextState;
        setScenarioState({ ...nextState });
        const collection = extractArray(result.execution.response, capability.collectionPath);
        if (collection.length > 0) {
          setRowsByCapability((current) => ({ ...current, [capability.id]: collection }));
        }
        if (stage.role === "AUTHENTICATE" && typeof nextState.authToken === "string") {
          config.onAuthTokenChange(nextState.authToken);
        }
      }
      stateRef.current = nextState;
      setScenarioState(nextState);
      await loadCollections(controller.signal);
      const executionOverlays = executionAction.overlayIds
        .map((id) => graph.overlays.find((overlay) => overlay.id === id))
        .filter((overlay): overlay is ExperienceOverlay => Boolean(overlay));
      const resultIndex = executionOverlays.findIndex((overlay) => overlay.kind === "RESULT_TOAST");
      if (activeAction && resultIndex >= 0) setOverlayIndex(resultIndex);
      else {
        if (activeAction) closeAction();
        setToast(`${executionAction.label} 작업을 완료했습니다.`);
      }
    } catch (cause) {
      if (!controller.signal.aborted) {
        const message = cause instanceof Error ? cause.message : "작업을 완료하지 못했습니다.";
        setActionError(message);
        if (failedStage && !failureRecorded) {
          recordExecution({
            ...emptyExecution(failedStage),
            status: "FAILED",
            error: message,
            completedAt: Date.now(),
          });
        }
        const progressIndex = actionOverlays.findIndex((overlay) => overlay.kind === "PROGRESS_MODAL");
        if (progressIndex >= 0) setOverlayIndex(progressIndex);
      }
    } finally {
      setBusy(false);
      abortRef.current = null;
    }
  }

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  if (!activeScreen) return null;

  return (
    <section
      className="relative overflow-hidden rounded-[30px] border border-[var(--px-line)] bg-[var(--px-bg)] text-[var(--px-ink)] shadow-[var(--px-shadow-lg)]"
      style={theme.style}
      data-product-theme={theme.id}
      data-blueprint-theme={theme.blueprintThemeId}
    >
      <header className="sticky top-0 z-20 border-b border-[var(--px-line)] bg-[color-mix(in_srgb,var(--px-surface)_95%,transparent)] px-5 backdrop-blur-xl md:px-8">
        <div className="mx-auto flex min-h-[72px] max-w-[1380px] items-center gap-6">
          <button
            type="button"
            className="flex shrink-0 items-center gap-3"
            onClick={() => navigateScreen(graph.defaultScreenId)}
          >
            <span className="grid h-9 w-9 place-items-center rounded-[12px] bg-[var(--px-accent)] text-sm font-black text-[var(--px-on-accent)]">
              {graph.productName.slice(0, 1)}
            </span>
            <strong className="hidden text-base font-black tracking-[-.02em] text-[var(--px-ink)] sm:block">{graph.productName}</strong>
          </button>
          <nav className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto py-2" aria-label="서비스 메뉴">
            {graph.screens.map((screen) => (
              <button
                type="button"
                key={screen.id}
                onClick={() => navigateScreen(screen.id)}
                className={`shrink-0 rounded-full px-3.5 py-2 text-xs font-bold transition-colors ${
                  activeScreen.id === screen.id ? "bg-[var(--px-tint)] text-[var(--px-accent)]" : "text-[var(--px-muted)] hover:bg-[var(--px-surface-soft)] hover:text-[var(--px-ink)]"
                }`}
              >
                {screen.label}
              </button>
            ))}
          </nav>
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => setInspectorOpen(true)}
              className="inline-flex rounded-full border border-[var(--px-line)] px-3 py-2 text-[10px] font-bold text-[var(--px-muted)]"
            >
              테스트 Inspector
            </button>
            <button type="button" className="grid h-9 w-9 place-items-center rounded-full border border-[var(--px-line)] bg-[var(--px-surface)] text-xs">⌕</button>
            <button type="button" className="grid h-9 w-9 place-items-center rounded-full bg-[var(--px-tint-strong)] text-xs font-black text-[var(--px-accent)]">ME</button>
          </div>
        </div>
      </header>

      <main className="mx-auto min-h-[680px] max-w-[1380px] px-5 py-8 md:px-8 md:py-10">
        <div className="mb-8 flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="text-xs font-bold text-[var(--px-muted)]">{activeScreen.label}</p>
            <h1 className="mt-2 text-3xl font-black tracking-[-.035em] text-[var(--px-ink)] md:text-4xl">{activeScreen.title}</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--px-muted)]">{activeScreen.description}</p>
          </div>
          <div className="flex max-w-3xl flex-wrap justify-end gap-2">
            {screenActions.map((action) => (
              <ProductActionButton key={action.id} action={action} onClick={() => openAction(action)} />
            ))}
            <button
              type="button"
              disabled={loadingCollections}
              onClick={() => void loadCollections()}
              className="grid h-10 w-10 place-items-center rounded-full border border-[var(--px-line)] bg-[var(--px-surface)] text-sm text-[var(--px-muted)] shadow-sm disabled:opacity-50"
              aria-label="새로고침"
            >
              {loadingCollections ? "…" : "↻"}
            </button>
          </div>
        </div>

        {validationErrors.length > 0 && (
          <div className="mb-5 rounded-[16px] border border-[color-mix(in_srgb,var(--px-danger)_35%,transparent)] bg-[var(--px-danger-bg)] p-4 text-xs font-bold text-[var(--px-danger)]">
            화면 구성 검증 실패: {validationErrors.join("; ")}
          </div>
        )}

        <ScreenContent
          screen={activeScreen}
          rows={rows}
          selected={selected}
          onSelect={selectRow}
        />
      </main>

      <footer className="border-t border-[var(--px-line)] bg-[color-mix(in_srgb,var(--px-surface)_62%,transparent)] px-8 py-5">
        <div className="mx-auto flex max-w-[1380px] flex-wrap items-center justify-between gap-3 text-[10px] font-bold text-[var(--px-subtle)]">
          <span>{graph.productName} · 모든 변경 사항이 자동으로 저장됩니다.</span>
          <span>개인정보 · 이용약관 · 도움말</span>
        </div>
      </footer>

      <DetailOverlay
        row={selected}
        open={detailOpen}
        actions={screenActions}
        theme={theme}
        onClose={() => setDetailOpen(false)}
        onAction={openAction}
      />

      {activeAction && activeScenario && activeOverlay && (
        <ActionOverlay
          action={activeAction}
          overlay={activeOverlay}
          scenario={activeScenario}
          capabilities={capabilities}
          draft={draft}
          selected={selected}
          busy={busy}
          error={actionError}
          theme={theme}
          onDraft={(field, value) => setDraft((current) => ({ ...current, [field]: value }))}
          onClose={closeAction}
          onContinue={advanceOverlay}
          onExecute={() => void executeAction()}
        />
      )}

      <ProductExperienceInspector
        open={inspectorOpen}
        screen={activeScreen}
        action={inspectedAction}
        scenario={inspectedScenario}
        scenarioState={scenarioState}
        executions={executions}
        timeline={executionTimeline}
        currentStageId={currentStageId}
        running={busy}
        selectedRecord={selected}
        rawBodyDrafts={rawBodyDrafts}
        theme={theme}
        onRawBodyChange={(stageId, value) => setRawBodyDrafts((current) => ({ ...current, [stageId]: value }))}
        onRetry={(stageId) => void executeAction(stageId)}
        onCancel={() => abortRef.current?.abort()}
        onClose={() => setInspectorOpen(false)}
      />

      {toast && (
        <div className="fixed bottom-6 left-1/2 z-[220] flex -translate-x-1/2 items-center gap-3 rounded-full border border-[var(--px-line-strong)] bg-[var(--px-hero)] px-5 py-3 text-sm font-bold text-[var(--px-hero-ink)] shadow-2xl">
          <span className="grid h-6 w-6 place-items-center rounded-full bg-[var(--px-success)] text-xs text-[var(--px-on-accent)]">✓</span>
          {toast}
        </div>
      )}

      <span className="sr-only">{Object.keys(scenarioState).length}개의 사용자 흐름 상태가 유지되고 있습니다.</span>
    </section>
  );
}
