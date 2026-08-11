/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DocsAdminStats, DocsArticleSummary, DocsArticleStatus } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { buttonClass, Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/field";
import { Modal } from "@/components/ui/modal";
import { toAdminDocsImageUrl } from "@/components/docs/admin-image-url";
import { Pager } from "@/components/ui/pager";

type StatusFilter = "ALL" | DocsArticleStatus;

export default function AdminDocsPage() {
  const { accessToken } = useAuth();
  const [articles, setArticles] = useState<DocsArticleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [stats, setStats] = useState<DocsAdminStats>({ total: 0, published: 0, drafts: 0, categories: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<DocsArticleSummary | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.admin.docs.stats(accessToken)
      .then(setStats)
      .catch((cause) => setError(cause instanceof Error ? cause.message : "문서 목록을 불러오지 못했습니다."))
  }, [accessToken, reloadKey]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    api.admin.docs.listPage(accessToken, {
      query: debouncedQuery,
      status: status === "ALL" ? undefined : status,
      page,
      size: 20,
    })
      .then((data) => {
        if (cancelled) return;
        setArticles(data.content);
        setTotalPages(data.totalPages);
        setError(null);
      })
      .catch((cause) => {
        if (!cancelled) setError(cause instanceof Error ? cause.message : "문서 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => { cancelled = true; };
  }, [accessToken, debouncedQuery, page, reloadKey, status]);

  async function handleDelete() {
    if (!accessToken || !deleteTarget) return;
    setDeleting(true);
    try {
      await api.admin.docs.delete(accessToken, deleteTarget.id);
      if (articles.length === 1 && page > 1) setPage((current) => current - 1);
      setReloadKey((current) => current + 1);
      setDeleteTarget(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "문서를 삭제하지 못했습니다.");
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <PageLoader />;

  return (
    <div className="mx-auto max-w-[1280px]">
      <header className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div>
          <p className="text-[10px] font-extrabold uppercase tracking-[.15em] text-muted">Content workspace</p>
          <h1 className="mt-2 text-3xl font-black tracking-[-.035em] text-[#162019]">사용 설명서 관리</h1>
          <p className="mt-2 text-sm text-muted">사용자 포털에 노출할 기능별·페이지별 가이드를 작성하고 발행합니다.</p>
        </div>
        <Link href="/docs/new" className={buttonClass({ variant: "primary", className: "shadow-lg shadow-[#b9e6c4]/35" })}>＋ 새 문서 작성</Link>
      </header>

      <section className="mt-7 grid gap-3 sm:grid-cols-4">
        {[
          ["전체 문서", stats.total, "▤"],
          ["발행됨", stats.published, "●"],
          ["초안", stats.drafts, "◌"],
          ["카테고리", stats.categories, "◇"],
        ].map(([label, value, icon]) => (
          <div key={String(label)} className="rounded-[16px] border border-[#dce5de] bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between"><span className="text-xs font-bold text-muted">{label}</span><span className="text-[#77a482]">{icon}</span></div>
            <strong className="mt-3 block text-2xl font-black text-[#172019]">{value}</strong>
          </div>
        ))}
      </section>

      <section className="mt-6 overflow-hidden rounded-[18px] border border-[#dce5de] bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-[#e4ebe6] p-4 sm:flex-row">
          <div className="relative min-w-0 flex-1"><span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-soft">⌕</span><Input value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="제목, 카테고리, 태그 검색" className="border-[#d7e0d9] bg-[#fbfdfb] pl-9 text-[#253029]" /></div>
          <Select value={status} onChange={(event) => { setStatus(event.target.value as StatusFilter); setPage(1); }} className="w-full border-[#d7e0d9] bg-[#fbfdfb] text-[#3d4941] sm:w-40"><option value="ALL">전체 상태</option><option value="PUBLISHED">발행됨</option><option value="DRAFT">초안</option></Select>
        </div>

        {error && <div className="border-b border-[#f0cccc] bg-[#fff5f5] px-5 py-3 text-sm text-danger">{error}</div>}

        {articles.length > 0 ? (
          <div className="divide-y divide-[#edf1ee]">
            {articles.map((article) => (
              <article key={article.id} className="group grid gap-4 p-4 transition hover:bg-[#f9fcfa] sm:grid-cols-[108px_minmax(0,1fr)_auto] sm:items-center">
                <div className="aspect-[16/10] overflow-hidden rounded-[11px] border border-[#e0e7e2] bg-[linear-gradient(135deg,#edf8f0,#f8fbf9)]">
                  {article.coverImageUrl ? <img src={toAdminDocsImageUrl(article.coverImageUrl)} alt="" className="h-full w-full object-cover" /> : <div className="grid h-full place-items-center text-xl text-[#90b298]">▤</div>}
                </div>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2"><span className={`rounded-full px-2 py-1 text-[9px] font-extrabold ${article.status === "PUBLISHED" ? "bg-[#e7f7eb] text-[#28753c]" : "bg-[#f0f2f0] text-muted"}`}>{article.status === "PUBLISHED" ? "발행됨" : "초안"}</span>{article.featured && <span className="rounded-full bg-[#fff4d8] px-2 py-1 text-[9px] font-extrabold text-[#9a6b0d]">추천</span>}<span className="text-[10px] font-bold text-muted">{article.category}</span></div>
                  <h2 className="mt-2 truncate text-base font-extrabold text-[#1d2821] group-hover:text-[#347c45]">{article.title}</h2>
                  <p className="mt-1 line-clamp-1 text-xs text-muted">{article.summary}</p>
                  <div className="mt-2 flex flex-wrap gap-3 text-[10px] text-muted-soft"><span>{new Date(article.updatedAt).toLocaleString("ko-KR")} 수정</span><span>조회 {article.viewCount.toLocaleString("ko-KR")}</span><span>/{article.slug}</span></div>
                </div>
                <div className="flex items-center justify-end gap-2">
                  <Link href={`/docs/${article.id}`} className={buttonClass({ size: "small", className: "border-[#d7e0d9] bg-white text-[#435047] hover:bg-[#f2f7f3]" })}>편집</Link>
                  <button type="button" onClick={() => setDeleteTarget(article)} className="grid h-[34px] w-[34px] place-items-center rounded-[9px] border border-[#ecdada] text-xs text-danger hover:bg-[#fff1f1]" aria-label={`${article.title} 삭제`}>×</button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="grid min-h-72 place-items-center text-center"><div><span className="text-3xl text-[#9eaaa1]">▤</span><h2 className="mt-3 font-extrabold text-[#27322b]">표시할 문서가 없습니다</h2><p className="mt-1 text-sm text-muted">필터를 바꾸거나 첫 문서를 작성해 보세요.</p></div></div>
        )}
      </section>

      <Pager page={page} totalPages={totalPages} onChange={setPage} />

      <Modal open={Boolean(deleteTarget)} onClose={() => !deleting && setDeleteTarget(null)}>
        <section className="mx-auto w-full max-w-md overflow-hidden rounded-[18px] border border-[#eadada] bg-white text-[#1e2822] shadow-2xl">
          <div className="p-6"><div className="grid h-11 w-11 place-items-center rounded-full bg-[#fff0f0] text-xl text-danger">!</div><h2 className="mt-5 text-xl font-black">문서를 삭제할까요?</h2><p className="mt-2 text-sm leading-6 text-muted"><strong className="text-[#263129]">{deleteTarget?.title}</strong> 문서가 사용자 포털과 관리자 목록에서 완전히 삭제됩니다.</p></div>
          <footer className="flex justify-end gap-2 border-t border-[#e6ebe7] bg-[#fafcfa] p-4"><Button onClick={() => setDeleteTarget(null)} disabled={deleting} className="border-[#d7e0d9] bg-white text-[#465249]">취소</Button><Button variant="danger-solid" onClick={handleDelete} disabled={deleting}>{deleting ? "삭제 중..." : "문서 삭제"}</Button></footer>
        </section>
      </Modal>
    </div>
  );
}
