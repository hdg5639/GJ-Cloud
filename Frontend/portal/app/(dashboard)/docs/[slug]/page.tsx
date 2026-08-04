/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { use, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DocsArticle, DocsArticleSummary } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { MarkdownRenderer } from "@/components/docs/MarkdownRenderer";
import { extractMarkdownHeadings } from "@/components/docs/markdown";

export default function DocsArticlePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const { accessToken } = useAuth();
  const [article, setArticle] = useState<DocsArticle | null>(null);
  const [articles, setArticles] = useState<DocsArticleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([api.docs.get(accessToken, slug), api.docs.list(accessToken)])
      .then(([articleData, listData]) => {
        setArticle(articleData);
        setArticles(listData);
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "문서를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [accessToken, slug]);

  const headings = useMemo(() => extractMarkdownHeadings(article?.content ?? ""), [article?.content]);
  const sameCategory = articles.filter((item) => item.category === article?.category);

  if (loading) return <PageLoader />;
  if (!article || error) {
    return <div className="mx-auto max-w-2xl rounded-[18px] border border-line bg-panel p-8 text-center"><h1 className="text-xl font-black">문서를 열 수 없습니다</h1><p className="mt-2 text-sm text-muted">{error ?? "삭제되었거나 아직 발행되지 않은 문서입니다."}</p><Link href="/docs" className="mt-5 inline-flex rounded-[10px] bg-brand px-4 py-2 text-sm font-bold text-black">설명서로 돌아가기</Link></div>;
  }

  return (
    <div className="mx-auto max-w-[1440px]">
      <div className="mb-5 flex min-w-0 items-center gap-2 overflow-hidden text-xs text-muted-soft">
        <Link href="/docs" className="shrink-0 font-bold hover:text-brand-strong">사용 설명서</Link><span className="shrink-0">/</span><span className="shrink-0">{article.category}</span><span className="shrink-0">/</span><span className="min-w-0 truncate text-muted">{article.title}</span>
      </div>

      <div className="grid items-start gap-7 xl:grid-cols-[230px_minmax(0,820px)_190px] xl:justify-center">
        <aside className="hidden xl:sticky xl:top-5 xl:block">
          <Link href="/docs" className="mb-5 flex items-center gap-2 text-xs font-extrabold text-muted hover:text-foreground">← 모든 설명서</Link>
          <p className="mb-2 px-3 text-[10px] font-extrabold uppercase tracking-[.13em] text-muted-soft">{article.category}</p>
          <nav className="grid gap-1">
            {sameCategory.map((item) => (
              <Link key={item.id} href={`/docs/${item.slug}`} className={`rounded-[10px] px-3 py-2.5 text-xs font-bold leading-5 ${item.id === article.id ? "bg-brand/10 text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"}`}>{item.title}</Link>
            ))}
          </nav>
        </aside>

        <main className="min-w-0 overflow-hidden rounded-[22px] border border-line bg-panel shadow-xl shadow-black/10">
          {article.coverImageUrl && <div className="max-h-[430px] overflow-hidden border-b border-line bg-black/10"><img src={article.coverImageUrl} alt="" className="h-full max-h-[430px] w-full object-cover" /></div>}
          <div className="px-5 py-8 sm:px-10 sm:py-11 lg:px-14">
            <div className="flex flex-wrap items-center gap-2 text-[10px] font-extrabold tracking-[.1em] text-brand-strong"><span>{article.category}</span>{article.tags.map((tag) => <span key={tag} className="rounded-full border border-line bg-white/[0.035] px-2 py-1 font-bold tracking-normal text-muted">#{tag}</span>)}</div>
            <h1 className="mt-5 break-words text-3xl font-black leading-[1.15] tracking-[-.045em] sm:text-5xl">{article.title}</h1>
            <p className="mt-5 break-words text-base leading-7 text-muted">{article.summary}</p>
            <div className="mt-7 flex flex-wrap items-center gap-x-5 gap-y-2 border-b border-line pb-7 text-[11px] text-muted-soft">
              <span>{new Date(article.updatedAt).toLocaleDateString("ko-KR")} 업데이트</span>
              <span>조회 {article.viewCount.toLocaleString("ko-KR")}</span>
              <span>약 {Math.max(1, Math.ceil(article.content.length / 900))}분</span>
            </div>
            <MarkdownRenderer content={article.content} className="pt-4" />
            <div className="mt-14 rounded-[16px] border border-line bg-white/[0.025] p-5 text-center"><p className="text-sm font-extrabold">원하는 답을 찾지 못했나요?</p><p className="mt-1 text-xs text-muted">찾지 못한 내용이나 보완이 필요한 부분을 문의로 남겨주세요.</p><Link href={{ pathname: "/support", query: { category: "DOCS", articleSlug: article.slug, articleTitle: article.title } }} className="mt-4 inline-flex text-xs font-extrabold text-brand-strong hover:underline">이 설명서에 대해 문의하기 →</Link></div>
          </div>
        </main>

        <aside className="hidden xl:sticky xl:top-5 xl:block">
          <p className="mb-3 text-[10px] font-extrabold uppercase tracking-[.13em] text-muted-soft">이 페이지에서</p>
          {headings.length > 0 ? <nav className="grid gap-1 border-l border-line pl-3">{headings.map((heading) => <a key={heading.id} href={`#${heading.id}`} className={`py-1 text-[11px] leading-5 text-muted hover:text-brand-strong ${heading.level === 3 ? "pl-3" : "font-bold"}`}>{heading.text}</a>)}</nav> : <p className="text-[11px] leading-5 text-muted-soft">별도 목차가 없는 짧은 문서입니다.</p>}
          <Link href="/docs" className="mt-6 block rounded-[12px] border border-line bg-panel p-3 text-[11px] font-bold text-muted hover:border-brand/35 hover:text-brand-strong">⌕ 다른 설명서 찾기</Link>
        </aside>
      </div>
    </div>
  );
}
