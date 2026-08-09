"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { SupportInquiry, SupportInquiryCategory, SupportInquiryStatus } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Select, Textarea } from "@/components/ui/field";
import { PageLoader } from "@/components/ui/loader";
import { Pager } from "@/components/ui/pager";

type StatusFilter = SupportInquiryStatus | "ALL";

const STATUS_LABEL = { OPEN: "접수됨", ANSWERED: "답변 완료", CLOSED: "종료됨" } as const;
const STATUS_CLASS = {
  OPEN: "bg-[#fff4d8] text-[#93650b]",
  ANSWERED: "bg-[#e7f7eb] text-[#28753c]",
  CLOSED: "bg-[#eef1ef] text-muted",
} as const;
const CATEGORY_LABEL: Record<SupportInquiryCategory, string> = {
  DOCS: "사용 설명서",
  TECHNICAL: "기술 문제",
  ACCOUNT: "계정·권한",
  BILLING: "플랜",
  OTHER: "기타",
};

export default function AdminSupportInquiriesPage() {
  const { accessToken } = useAuth();
  const [inquiries, setInquiries] = useState<SupportInquiry[]>([]);
  const [status, setStatus] = useState<StatusFilter>("OPEN");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [responses, setResponses] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (!accessToken) return;
    api.admin.support.list(accessToken, status, page, 20)
      .then((data) => {
        setError(null);
        setInquiries(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setResponses((current) => {
          const next = { ...current };
          data.content.forEach((inquiry) => {
            if (next[inquiry.id] === undefined) next[inquiry.id] = inquiry.response ?? "";
          });
          return next;
        });
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "문의 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [accessToken, page, refreshKey, status]);

  function changeStatus(nextStatus: StatusFilter) {
    setLoading(true);
    setStatus(nextStatus);
    setPage(1);
  }

  function changePage(nextPage: number) {
    setLoading(true);
    setPage(nextPage);
  }

  async function updateInquiry(inquiry: SupportInquiry, nextStatus: SupportInquiryStatus) {
    if (!accessToken || actionLoading) return;
    const response = responses[inquiry.id]?.trim() ?? "";
    if (nextStatus === "ANSWERED" && !response) {
      setError("답변 내용을 입력해 주세요.");
      return;
    }
    setActionLoading(inquiry.id);
    setError(null);
    try {
      await api.admin.support.update(accessToken, inquiry.id, {
        status: nextStatus,
        ...(nextStatus === "ANSWERED" ? { response } : {}),
      });
      setLoading(true);
      setRefreshKey((current) => current + 1);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "문의를 처리하지 못했습니다.");
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <div className="mx-auto max-w-[1280px]">
      <header className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div>
          <p className="text-[10px] font-extrabold uppercase tracking-[.15em] text-muted">Support inbox</p>
          <h1 className="mt-2 text-3xl font-black tracking-[-.035em] text-[#162019]">사용자 문의</h1>
          <p className="mt-2 text-sm text-muted">사용자가 남긴 문의를 확인하고 답변과 처리 상태를 관리합니다.</p>
        </div>
        <div className="rounded-[14px] border border-[#dce5de] bg-white px-4 py-3 text-right shadow-sm"><span className="block text-[10px] font-bold text-muted">현재 필터</span><strong className="mt-1 block text-xl text-[#1c2820]">{totalElements.toLocaleString("ko-KR")}건</strong></div>
      </header>

      <section className="mt-7 overflow-hidden rounded-[18px] border border-[#dce5de] bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-[#e4ebe6] p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="hidden min-w-0 flex-wrap gap-2 sm:flex">
            {(["OPEN", "ANSWERED", "CLOSED", "ALL"] as StatusFilter[]).map((item) => (
              <button key={item} type="button" onClick={() => changeStatus(item)} className={`rounded-full border px-3.5 py-2 text-xs font-extrabold transition ${status === item ? "border-[#73c687] bg-[#e9f8ed] text-[#26733a]" : "border-[#dce4de] bg-[#fbfdfb] text-muted hover:bg-[#f2f7f3]"}`}>{item === "ALL" ? "전체" : STATUS_LABEL[item]}</button>
            ))}
          </div>
          <Select aria-label="문의 상태 필터" value={status} onChange={(event) => changeStatus(event.target.value as StatusFilter)} className="w-full border-[#d7e0d9] bg-[#fbfdfb] text-[#3d4941] sm:hidden"><option value="OPEN">접수됨</option><option value="ANSWERED">답변 완료</option><option value="CLOSED">종료됨</option><option value="ALL">전체</option></Select>
        </div>

        {error && <div aria-live="polite" className="border-b border-[#f0cccc] bg-[#fff5f5] px-5 py-3 text-sm text-danger">{error}</div>}

        {loading ? (
          <PageLoader />
        ) : inquiries.length === 0 ? (
          <div className="grid min-h-72 place-items-center px-5 text-center"><div><span className="text-3xl text-[#9eaaa1]">?</span><h2 className="mt-3 font-extrabold text-[#27322b]">표시할 문의가 없습니다</h2><p className="mt-1 text-sm text-muted">다른 처리 상태를 선택해 보세요.</p></div></div>
        ) : (
          <div className="divide-y divide-[#e9eeea]">
            {inquiries.map((inquiry) => {
              const busy = actionLoading === inquiry.id;
              return (
                <article key={inquiry.id} className="grid min-w-0 gap-5 p-4 sm:p-6 xl:grid-cols-[minmax(0,1fr)_minmax(320px,440px)]">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-2 py-1 text-[9px] font-extrabold ${STATUS_CLASS[inquiry.status]}`}>{STATUS_LABEL[inquiry.status]}</span>
                      <span className="text-[10px] font-bold text-muted">{CATEGORY_LABEL[inquiry.category]}</span>
                      <time className="text-[10px] text-muted-soft">{new Date(inquiry.createdAt).toLocaleString("ko-KR")}</time>
                    </div>
                    <h2 className="mt-3 break-words text-lg font-black text-[#1b2720]">{inquiry.title}</h2>
                    <div className="mt-2 flex min-w-0 flex-wrap gap-x-4 gap-y-1 text-[11px] text-muted"><span className="break-all">{inquiry.requesterEmail}</span><span className="break-all">사용자 ID: {inquiry.userId}</span></div>
                    {inquiry.sourceArticleSlug && <p className="mt-3 max-w-full break-all text-[11px] font-bold text-[#347c45]">연결 문서: {inquiry.sourceArticleTitle || inquiry.sourceArticleSlug} · /docs/{inquiry.sourceArticleSlug}</p>}
                    <div className="mt-4 rounded-[13px] border border-[#e1e7e2] bg-[#fafcfa] p-4"><p className="whitespace-pre-wrap break-words text-sm leading-6 text-[#4d5a52]">{inquiry.content}</p></div>
                    {inquiry.respondedAt && <p className="mt-3 text-[10px] text-muted-soft">마지막 답변: {new Date(inquiry.respondedAt).toLocaleString("ko-KR")} · {inquiry.respondedBy}</p>}
                  </div>

                  <div className="min-w-0 rounded-[14px] border border-[#dfe6e1] bg-[#fbfdfb] p-4">
                    <label htmlFor={`response-${inquiry.id}`} className="flex items-center justify-between gap-3 text-xs font-extrabold text-[#455148]"><span>관리자 답변</span><span className="font-normal text-muted-soft">{(responses[inquiry.id] ?? "").length}/4000</span></label>
                    <Textarea id={`response-${inquiry.id}`} maxLength={4000} rows={7} value={responses[inquiry.id] ?? ""} onChange={(event) => setResponses((current) => ({ ...current, [inquiry.id]: event.target.value }))} placeholder="사용자에게 표시할 답변을 입력하세요." className="mt-2 min-h-36 resize-y border-[#d7e0d9] bg-white text-[#2d3931] placeholder:text-muted-soft" />
                    <div className="mt-3 flex flex-wrap justify-end gap-2">
                      {inquiry.status !== "OPEN" && <Button type="button" size="small" onClick={() => updateInquiry(inquiry, "OPEN")} disabled={busy} className="border-[#d7e0d9] bg-white text-[#536057] hover:bg-[#f1f6f2]">다시 열기</Button>}
                      {inquiry.status !== "CLOSED" && <Button type="button" size="small" onClick={() => updateInquiry(inquiry, "CLOSED")} disabled={busy} className="border-[#d7e0d9] bg-white text-[#536057] hover:bg-[#f1f6f2]">처리 종료</Button>}
                      <Button type="button" size="small" variant="primary" onClick={() => updateInquiry(inquiry, "ANSWERED")} disabled={busy || !(responses[inquiry.id] ?? "").trim()} className="shadow-sm">{busy ? "저장 중..." : inquiry.status === "ANSWERED" ? "답변 수정" : "답변 완료"}</Button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
        <Pager page={page} totalPages={totalPages} onChange={changePage} />
      </section>
    </div>
  );
}
