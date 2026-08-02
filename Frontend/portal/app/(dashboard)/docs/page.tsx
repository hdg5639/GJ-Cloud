/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DocsArticleSummary, DocsCategory } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

const CATEGORY_ICONS = ["◫", "⌘", "◇", "▤", "↗", "◎"];

function categoryIcon(category: string): string {
  const score = Array.from(category).reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return CATEGORY_ICONS[score % CATEGORY_ICONS.length];
}

function GuideCard({ article, featured = false }: { article: DocsArticleSummary; featured?: boolean }) {
  return (
    <Link
      href={`/docs/${article.slug}`}
      className={`group overflow-hidden rounded-[18px] border border-line bg-panel transition duration-200 hover:-translate-y-0.5 hover:border-brand/45 hover:shadow-xl hover:shadow-black/15 ${
        featured ? "grid min-h-[230px] md:grid-cols-[1.05fr_.95fr]" : "flex min-h-[210px] flex-col"
      }`}
    >
      {article.coverImageUrl ? (
        <div className={featured ? "min-h-[210px] overflow-hidden bg-white/[0.025]" : "aspect-[16/8] overflow-hidden bg-white/[0.025]"}>
          <img src={article.coverImageUrl} alt="" className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.025]" />
        </div>
      ) : (
        <div className={`${featured ? "min-h-[210px]" : "aspect-[16/8]"} relative overflow-hidden bg-[radial-gradient(circle_at_20%_20%,rgba(118,218,143,.18),transparent_45%),linear-gradient(135deg,#141b16,#0d100e)]`}>
          <span className="absolute bottom-5 left-5 grid h-12 w-12 place-items-center rounded-[14px] border border-white/10 bg-white/[0.06] text-2xl text-brand-strong">
            {categoryIcon(article.category)}
          </span>
        </div>
      )}
      <div className={`flex flex-1 flex-col ${featured ? "p-6 md:p-7" : "p-5"}`}>
        <div className="flex items-center gap-2 text-[10px] font-extrabold uppercase tracking-[.11em] text-brand-strong">
          <span>{article.category}</span>
          {article.featured && <span className="rounded-full bg-brand/10 px-2 py-1 tracking-normal">추천</span>}
        </div>
        <h2 className={`${featured ? "mt-4 text-2xl" : "mt-3 text-lg"} font-black leading-tight tracking-[-.025em] text-foreground group-hover:text-brand-strong`}>
          {article.title}
        </h2>
        <p className="mt-3 line-clamp-3 text-sm leading-6 text-muted">{article.summary}</p>
        <div className="mt-auto flex items-center justify-between gap-3 pt-5 text-[11px] text-muted-soft">
          <span>{new Date(article.updatedAt).toLocaleDateString("ko-KR")} 업데이트</span>
          <span className="font-bold text-muted transition group-hover:translate-x-1 group-hover:text-brand-strong">읽어보기 →</span>
        </div>
      </div>
    </Link>
  );
}

export default function DocsPage() {
  const { accessToken } = useAuth();
  const [articles, setArticles] = useState<DocsArticleSummary[]>([]);
  const [categories, setCategories] = useState<DocsCategory[]>([]);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([api.docs.list(accessToken), api.docs.categories(accessToken)])
      .then(([articleData, categoryData]) => {
        setArticles(articleData);
        setCategories(categoryData);
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "문서를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [accessToken]);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return articles.filter((article) => {
      if (category !== "ALL" && article.category !== category) return false;
      if (!normalized) return true;
      return [article.title, article.summary, article.category, ...article.tags]
        .join(" ")
        .toLowerCase()
        .includes(normalized);
    });
  }, [articles, category, query]);

  const featured = articles.filter((article) => article.featured).slice(0, 2);

  if (loading) return <PageLoader />;

  return (
    <div className="mx-auto max-w-[1260px]">
      <section className="relative overflow-hidden rounded-[28px] border border-line bg-[radial-gradient(circle_at_78%_20%,rgba(122,224,147,.13),transparent_33%),linear-gradient(145deg,#151b17,#0d100e)] px-6 py-10 sm:px-10 sm:py-14 lg:px-14">
        <div className="relative z-10 max-w-3xl">
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-brand/25 bg-brand/[0.08] px-3 py-1.5 text-[10px] font-extrabold tracking-[.13em] text-brand-strong">
            GAMJABOX GUIDE
          </div>
          <h1 className="text-3xl font-black tracking-[-.045em] text-foreground sm:text-5xl">필요한 기능을 빠르게 찾아보세요.</h1>
          <p className="mt-4 max-w-2xl text-sm leading-7 text-muted sm:text-base">
            인스턴스 생성부터 배포, 협업, Auto Preview까지. 실제 화면을 따라갈 수 있는 단계별 사용 설명서를 모았습니다.
          </p>
          <label className="mt-8 flex max-w-2xl items-center gap-3 rounded-[14px] border border-line-strong bg-black/25 px-4 py-3 shadow-2xl shadow-black/20 focus-within:border-brand/60 focus-within:ring-4 focus-within:ring-brand/10">
            <span aria-hidden className="text-xl text-muted-soft">⌕</span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="기능, 화면, 문제 해결 방법 검색"
              className="min-w-0 flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-soft"
            />
            {query && <button type="button" onClick={() => setQuery("")} className="text-xs font-bold text-muted hover:text-foreground">지우기</button>}
          </label>
        </div>
        <div className="pointer-events-none absolute -bottom-24 -right-16 h-80 w-80 rounded-full border border-brand/10 bg-brand/[0.035]" />
      </section>

      {error && <div className="mt-5 rounded-[14px] border border-danger-soft bg-danger/10 p-4 text-sm text-danger">{error}</div>}

      <section className="mt-8">
        <div className="flex gap-2 overflow-x-auto pb-2 [scrollbar-width:none]">
          <button
            type="button"
            onClick={() => setCategory("ALL")}
            className={`shrink-0 rounded-full border px-4 py-2 text-xs font-extrabold transition ${category === "ALL" ? "border-brand bg-brand text-black" : "border-line bg-panel text-muted hover:border-line-strong hover:text-foreground"}`}
          >
            전체 <span className="ml-1 opacity-60">{articles.length}</span>
          </button>
          {categories.map((item) => (
            <button
              key={item.name}
              type="button"
              onClick={() => setCategory(item.name)}
              className={`shrink-0 rounded-full border px-4 py-2 text-xs font-extrabold transition ${category === item.name ? "border-brand bg-brand text-black" : "border-line bg-panel text-muted hover:border-line-strong hover:text-foreground"}`}
            >
              {item.name} <span className="ml-1 opacity-60">{item.articleCount}</span>
            </button>
          ))}
        </div>
      </section>

      {!query && category === "ALL" && featured.length > 0 && (
        <section className="mt-10">
          <div className="mb-4 flex items-end justify-between">
            <div><p className="text-[10px] font-extrabold tracking-[.14em] text-brand-strong">START HERE</p><h2 className="mt-1 text-xl font-black">추천 가이드</h2></div>
            <span className="text-xs text-muted-soft">가장 많이 찾는 기능부터 시작해 보세요</span>
          </div>
          <div className="grid gap-4 xl:grid-cols-2">
            {featured.map((article) => <GuideCard key={article.id} article={article} featured />)}
          </div>
        </section>
      )}

      <section className="mt-12 pb-8">
        <div className="mb-5 flex items-end justify-between gap-4">
          <div><p className="text-[10px] font-extrabold tracking-[.14em] text-muted-soft">ALL GUIDES</p><h2 className="mt-1 text-xl font-black">{category === "ALL" ? "전체 사용 설명서" : category}</h2></div>
          <span className="text-xs text-muted-soft">{filtered.length}개의 문서</span>
        </div>
        {filtered.length > 0 ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {filtered.map((article) => <GuideCard key={article.id} article={article} />)}
          </div>
        ) : (
          <div className="grid min-h-72 place-items-center rounded-[20px] border border-dashed border-line-strong bg-panel/40 text-center">
            <div><span className="text-3xl text-muted-soft">⌕</span><h3 className="mt-3 text-base font-extrabold">찾는 문서가 없습니다</h3><p className="mt-1 text-sm text-muted">검색어를 줄이거나 다른 카테고리를 선택해 보세요.</p></div>
          </div>
        )}
      </section>
    </div>
  );
}
