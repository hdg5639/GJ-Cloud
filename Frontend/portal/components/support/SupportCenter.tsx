"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { SupportInquiry, SupportInquiryCategory } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Pager } from "@/components/ui/pager";

const CATEGORIES: Array<{ value: SupportInquiryCategory; label: string; description: string }> = [
  { value: "DOCS", label: "사용 설명서", description: "설명서에 없거나 보완이 필요한 내용" },
  { value: "TECHNICAL", label: "기술 문제", description: "VM, 배포, 콘솔 등 기능 사용 중 발생한 문제" },
  { value: "ACCOUNT", label: "계정·권한", description: "로그인, 프로필, 조직과 권한 관련 문의" },
  { value: "BILLING", label: "플랜", description: "FREE·PRO 플랜과 변경 요청 관련 문의" },
  { value: "OTHER", label: "기타", description: "위 분류에 포함되지 않는 문의" },
];

const STATUS_LABEL = { OPEN: "접수됨", ANSWERED: "답변 완료", CLOSED: "종료됨" } as const;
const STATUS_CLASS = {
  OPEN: "border-amber-400/20 bg-amber-400/10 text-amber-200",
  ANSWERED: "border-brand/25 bg-brand/10 text-brand-strong",
  CLOSED: "border-line bg-white/[0.035] text-muted",
} as const;

export function SupportCenter() {
  const searchParams = useSearchParams();
  const { accessToken } = useAuth();
  const sourceArticleSlug = searchParams.get("articleSlug")?.slice(0, 120) ?? "";
  const sourceArticleTitle = searchParams.get("articleTitle")?.slice(0, 180) ?? "";
  const requestedCategory = searchParams.get("category");
  const initialCategory = CATEGORIES.some((item) => item.value === requestedCategory)
    ? requestedCategory as SupportInquiryCategory
    : "TECHNICAL";
  const defaultTitle = sourceArticleTitle ? `설명서 보완 요청: ${sourceArticleTitle}`.slice(0, 120) : "";

  const [category, setCategory] = useState<SupportInquiryCategory>(initialCategory);
  const [title, setTitle] = useState(defaultTitle);
  const [content, setContent] = useState("");
  const [inquiries, setInquiries] = useState<SupportInquiry[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [closingId, setClosingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.support.list(accessToken, page, 10)
      .then((data) => {
        setError(null);
        setInquiries(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "문의 내역을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [accessToken, page]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!accessToken || submitting) return;
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      const created = await api.support.create(accessToken, {
        category,
        title: title.trim(),
        content: content.trim(),
        ...(sourceArticleSlug ? { sourceArticleSlug } : {}),
        ...(sourceArticleTitle ? { sourceArticleTitle } : {}),
      });
      setPage(1);
      setInquiries((current) => [created, ...current].slice(0, 10));
      setTotalElements(totalElements + 1);
      setTotalPages(Math.ceil((totalElements + 1) / 10));
      setTitle(defaultTitle);
      setContent("");
      setNotice("문의가 접수되었습니다. 답변은 아래 문의 내역에서 확인할 수 있습니다.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "문의를 접수하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleClose(inquiryId: string) {
    if (!accessToken || closingId) return;
    setClosingId(inquiryId);
    setError(null);
    try {
      const updated = await api.support.close(accessToken, inquiryId);
      setInquiries((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "문의를 종료하지 못했습니다.");
    } finally {
      setClosingId(null);
    }
  }

  function changePage(nextPage: number) {
    setLoading(true);
    setPage(nextPage);
  }

  return (
    <div className="mx-auto max-w-[1180px] pb-10">
      <header className="relative overflow-hidden rounded-[26px] border border-line bg-[radial-gradient(circle_at_86%_18%,rgba(122,224,147,.14),transparent_32%),linear-gradient(145deg,#151b17,#0d100e)] px-5 py-8 sm:px-9 sm:py-10">
        <p className="text-[10px] font-extrabold uppercase tracking-[.15em] text-brand-strong">GamjaBox support</p>
        <h1 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">무엇을 도와드릴까요?</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-muted">기능 사용 중 막힌 부분이나 설명서에서 찾지 못한 내용을 남겨주세요. 관리자가 확인한 답변은 이 페이지의 문의 내역에 표시됩니다.</p>
      </header>

      <div className="mt-6 grid min-w-0 gap-5 xl:grid-cols-[minmax(0,440px)_minmax(0,1fr)] xl:items-start">
        <section className="min-w-0 rounded-[20px] border border-line bg-panel p-5 shadow-xl shadow-black/10 sm:p-6 xl:sticky xl:top-5">
          <div className="flex items-start justify-between gap-4">
            <div><h2 className="text-lg font-black">새 문의</h2><p className="mt-1 text-xs leading-5 text-muted">비밀번호, 토큰, 개인키 같은 비밀값은 입력하지 마세요.</p></div>
            <span className="shrink-0 rounded-full border border-line bg-white/[0.035] px-2.5 py-1 text-[10px] font-bold text-muted">보통 순서대로 확인</span>
          </div>

          {sourceArticleSlug && (
            <div className="mt-5 rounded-[13px] border border-brand/20 bg-brand/[0.07] p-3.5">
              <p className="text-[10px] font-extrabold uppercase tracking-[.12em] text-brand-strong">연결된 설명서</p>
              <Link href={`/docs/${sourceArticleSlug}`} className="mt-1.5 block break-words text-sm font-extrabold hover:underline">{sourceArticleTitle || sourceArticleSlug}</Link>
            </div>
          )}

          <form onSubmit={handleSubmit} className="mt-5">
            <Field label="문의 유형" htmlFor="support-category">
              <Select id="support-category" value={category} onChange={(event) => setCategory(event.target.value as SupportInquiryCategory)}>
                {CATEGORIES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
              </Select>
            </Field>
            <p className="-mt-2 mb-4 text-[11px] leading-5 text-muted-soft">{CATEGORIES.find((item) => item.value === category)?.description}</p>
            <Field label={<span className="flex justify-between gap-3"><span>제목</span><span className="font-normal text-muted-soft">{title.length}/120</span></span>} htmlFor="support-title">
              <Input id="support-title" required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="문의 내용을 한 줄로 요약해 주세요" />
            </Field>
            <Field label={<span className="flex justify-between gap-3"><span>내용</span><span className="font-normal text-muted-soft">{content.length}/4000</span></span>} htmlFor="support-content">
              <Textarea id="support-content" required maxLength={4000} rows={8} value={content} onChange={(event) => setContent(event.target.value)} placeholder="어느 화면에서 무엇을 하다가 막혔는지, 기대한 결과와 실제 결과를 함께 적어주세요." className="min-h-44 resize-y" />
            </Field>
            <Button type="submit" variant="primary" disabled={submitting || !title.trim() || !content.trim()} className="w-full">
              {submitting ? "접수 중..." : "문의 접수"}
            </Button>
          </form>

          <div aria-live="polite" className="mt-4">
            {notice && <p className="rounded-[11px] border border-brand/25 bg-brand/10 p-3 text-xs leading-5 text-brand-strong">{notice}</p>}
            {error && <p className="rounded-[11px] border border-danger-soft bg-danger/10 p-3 text-xs leading-5 text-danger">{error}</p>}
          </div>
        </section>

        <section className="min-w-0 overflow-hidden rounded-[20px] border border-line bg-panel shadow-xl shadow-black/10">
          <div className="flex items-end justify-between gap-4 border-b border-line px-5 py-5 sm:px-6">
            <div><h2 className="text-lg font-black">내 문의</h2><p className="mt-1 text-xs text-muted">접수 내용과 관리자 답변을 확인할 수 있습니다.</p></div>
            <span className="text-xs font-bold text-muted-soft">총 {totalElements}건</span>
          </div>

          {loading ? (
            <div className="grid min-h-64 place-items-center text-sm text-muted">문의 내역을 불러오는 중...</div>
          ) : inquiries.length === 0 ? (
            <div className="grid min-h-64 place-items-center px-5 text-center"><div><span className="text-3xl text-muted-soft">?</span><h3 className="mt-3 text-sm font-extrabold">아직 접수한 문의가 없습니다</h3><p className="mt-1 text-xs leading-5 text-muted">왼쪽 양식에서 첫 문의를 남겨보세요.</p></div></div>
          ) : (
            <div className="divide-y divide-line">
              {inquiries.map((inquiry) => (
                <article key={inquiry.id} className="min-w-0 p-5 sm:p-6">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={`rounded-full border px-2 py-1 text-[9px] font-extrabold ${STATUS_CLASS[inquiry.status]}`}>{STATUS_LABEL[inquiry.status]}</span>
                        <span className="text-[10px] font-bold text-muted-soft">{CATEGORIES.find((item) => item.value === inquiry.category)?.label}</span>
                      </div>
                      <h3 className="mt-2 break-words text-base font-extrabold">{inquiry.title}</h3>
                    </div>
                    <time className="shrink-0 text-[10px] text-muted-soft">{new Date(inquiry.createdAt).toLocaleString("ko-KR")}</time>
                  </div>
                  {inquiry.sourceArticleSlug && <Link href={`/docs/${inquiry.sourceArticleSlug}`} className="mt-3 inline-flex max-w-full break-all text-[11px] font-bold text-brand-strong hover:underline">연결 문서: {inquiry.sourceArticleTitle || inquiry.sourceArticleSlug}</Link>}
                  <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-muted">{inquiry.content}</p>
                  {inquiry.response && (
                    <div className="mt-4 rounded-[14px] border border-brand/20 bg-brand/[0.065] p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2"><strong className="text-xs text-brand-strong">관리자 답변</strong>{inquiry.respondedAt && <time className="text-[10px] text-muted-soft">{new Date(inquiry.respondedAt).toLocaleString("ko-KR")}</time>}</div>
                      <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-foreground">{inquiry.response}</p>
                    </div>
                  )}
                  {inquiry.status !== "CLOSED" && <div className="mt-4 flex justify-end"><Button type="button" size="small" onClick={() => handleClose(inquiry.id)} disabled={closingId === inquiry.id}>{closingId === inquiry.id ? "종료 중..." : "문의 종료"}</Button></div>}
                </article>
              ))}
            </div>
          )}
          <Pager page={page} totalPages={totalPages} onChange={changePage} />
        </section>
      </div>
    </div>
  );
}
