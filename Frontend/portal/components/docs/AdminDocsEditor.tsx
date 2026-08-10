/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DocsArticle, DocsArticleInput } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/field";
import { PageLoader, Spinner } from "@/components/ui/loader";
import { MarkdownRenderer } from "./MarkdownRenderer";
import { toAdminDocsImageUrl } from "./admin-image-url";
import { parseDocsDraft } from "./parse-docs-draft";

type EditorMode = "WRITE" | "SPLIT" | "PREVIEW";

const STARTER_MARKDOWN = `## 이 문서에서 알아볼 내용

이 기능을 언제 사용하고, 어떤 결과를 얻을 수 있는지 간단히 설명해 주세요.

> 사용자가 작업을 시작하기 전에 알아야 할 중요한 내용을 강조할 수 있습니다.

## 시작하기 전에

- 필요한 권한 또는 플랜
- 미리 준비해야 할 정보
- 작업에 걸리는 예상 시간

## 단계별 사용 방법

### 1. 첫 번째 단계

화면에서 선택할 메뉴와 버튼을 정확히 적어 주세요.

### 2. 두 번째 단계

결과 화면과 다음 행동을 설명해 주세요.

## 자주 발생하는 문제

| 증상 | 확인할 내용 |
| --- | --- |
| 작업이 진행되지 않음 | VM 상태와 권한을 확인하세요. |

## 다음 단계

관련 기능이나 다음에 읽을 문서를 링크해 주세요.
`;

const EMPTY_DOCUMENT: DocsArticleInput = {
  slug: "",
  title: "",
  summary: "",
  category: "시작하기",
  coverImageUrl: null,
  content: STARTER_MARKDOWN,
  tags: [],
  featured: false,
  sortOrder: 0,
};

const TOOLBAR = [
  { label: "H2", title: "큰 제목", before: "\n## ", after: "\n" },
  { label: "H3", title: "작은 제목", before: "\n### ", after: "\n" },
  { label: "B", title: "굵게", before: "**", after: "**" },
  { label: "I", title: "기울임", before: "_", after: "_" },
  { label: "↗", title: "링크", before: "[", after: "](https://)" },
  { label: "❝", title: "인용/안내", before: "\n> ", after: "\n" },
  { label: "•", title: "목록", before: "\n- ", after: "\n" },
  { label: "☑", title: "체크리스트", before: "\n- [ ] ", after: "\n" },
  { label: "</>", title: "코드 블록", before: "\n```bash\n", after: "\n```\n" },
  { label: "▦", title: "표", before: "\n| 항목 | 설명 |\n| --- | --- |\n| ", after: " | 내용 |\n" },
];

export function AdminDocsEditor({ articleId }: { articleId?: string }) {
  const router = useRouter();
  const { accessToken } = useAuth();
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const imagePurposeRef = useRef<"CONTENT" | "COVER">("CONTENT");
  const [article, setArticle] = useState<DocsArticle | null>(null);
  const [draft, setDraft] = useState<DocsArticleInput>(EMPTY_DOCUMENT);
  const [tagInput, setTagInput] = useState("");
  const [mode, setMode] = useState<EditorMode>("SPLIT");
  const [showImport, setShowImport] = useState(false);
  const [importText, setImportText] = useState("");
  const [loading, setLoading] = useState(Boolean(articleId));
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dirty, setDirty] = useState(!articleId);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken || !articleId) return;
    api.admin.docs.get(accessToken, articleId)
      .then((data) => {
        setArticle(data);
        setDraft({
          slug: data.slug,
          title: data.title,
          summary: data.summary,
          category: data.category,
          coverImageUrl: data.coverImageUrl,
          content: data.content,
          tags: data.tags,
          featured: data.featured,
          sortOrder: data.sortOrder,
        });
        setDirty(false);
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "문서를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [accessToken, articleId]);

  function update<K extends keyof DocsArticleInput>(key: K, value: DocsArticleInput[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
    setDirty(true);
    setNotice(null);
  }

  function insertMarkdown(before: string, after: string, fallback = "내용") {
    const textarea = textareaRef.current;
    if (!textarea) {
      update("content", draft.content + before + fallback + after);
      return;
    }
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selected = draft.content.slice(start, end) || fallback;
    const next = draft.content.slice(0, start) + before + selected + after + draft.content.slice(end);
    update("content", next);
    requestAnimationFrame(() => {
      textarea.focus();
      const cursor = start + before.length + selected.length;
      textarea.setSelectionRange(cursor, cursor);
    });
  }

  function addTag() {
    const tag = tagInput.trim().replace(/^#/, "");
    if (!tag || draft.tags.includes(tag) || draft.tags.length >= 12) return;
    update("tags", [...draft.tags, tag]);
    setTagInput("");
  }

  function applyImport() {
    const parsed = parseDocsDraft(importText);
    if (!parsed) {
      setError("인식할 수 있는 내용이 없습니다. 제목/본문이 포함된 초안 양식을 붙여넣어 주세요.");
      return;
    }
    setDraft((current) => ({ ...current, ...parsed.fields }));
    setDirty(true);
    setError(null);
    setNotice(`가져오기 완료 — 채워진 항목: ${parsed.matched.join(", ")}. 검토 후 저장하세요.`);
    setShowImport(false);
    setImportText("");
  }

  async function save(): Promise<DocsArticle | null> {
    if (!accessToken) return null;
    if (!draft.title.trim() || !draft.summary.trim() || !draft.category.trim() || !draft.content.trim()) {
      setError("제목, 요약, 카테고리, 본문을 모두 입력해 주세요.");
      return null;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = articleId
        ? await api.admin.docs.update(accessToken, articleId, draft)
        : await api.admin.docs.create(accessToken, draft);
      setArticle(saved);
      setDraft((current) => ({ ...current, slug: saved.slug }));
      setDirty(false);
      setNotice("변경 사항을 저장했습니다.");
      if (!articleId) router.replace(`/docs/${saved.id}`);
      return saved;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "문서를 저장하지 못했습니다.");
      return null;
    } finally {
      setSaving(false);
    }
  }

  async function togglePublish() {
    if (!accessToken) return;
    let target = article;
    if (!target || dirty) target = await save();
    if (!target) return;
    setSaving(true);
    try {
      const updated = target.status === "PUBLISHED"
        ? await api.admin.docs.unpublish(accessToken, target.id)
        : await api.admin.docs.publish(accessToken, target.id);
      setArticle(updated);
      setNotice(updated.status === "PUBLISHED" ? "사용자 포털에 문서를 발행했습니다." : "문서를 초안으로 전환했습니다.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "발행 상태를 변경하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function uploadImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !accessToken) return;
    setUploading(true);
    setError(null);
    try {
      const uploaded = await api.admin.docs.uploadImage(accessToken, file);
      if (imagePurposeRef.current === "COVER") {
        update("coverImageUrl", uploaded.url);
      } else {
        insertMarkdown(`\n![${file.name.replace(/\.[^.]+$/, "")}](`, `${uploaded.url})\n`, "");
      }
      setNotice("이미지를 업로드했습니다.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "이미지를 업로드하지 못했습니다.");
    } finally {
      setUploading(false);
    }
  }

  function chooseImage(purpose: "CONTENT" | "COVER") {
    imagePurposeRef.current = purpose;
    imageInputRef.current?.click();
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void save();
  }

  function handleShortcut(event: KeyboardEvent<HTMLFormElement>) {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") {
      event.preventDefault();
      void save();
    }
  }

  if (loading) return <PageLoader />;

  return (
    <form onSubmit={handleSubmit} onKeyDown={handleShortcut} className="mx-auto max-w-[1560px] text-[#1d2720]">
      <input ref={imageInputRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="hidden" onChange={uploadImage} />

      <header className="sticky top-0 z-30 -mx-4 -mt-5 mb-5 flex flex-col gap-3 border-b border-[#dce4de] bg-white/95 px-4 py-3 backdrop-blur-xl sm:-mx-7 sm:-mt-7 sm:px-7 lg:-mx-[38px] lg:-mt-[34px] lg:mb-6 lg:flex-row lg:items-center lg:justify-between lg:px-[38px]">
        <div className="flex min-w-0 w-full items-center gap-3 lg:w-auto">
          <Link href="/docs" className="grid h-9 w-9 shrink-0 place-items-center rounded-[9px] border border-[#d9e1db] text-sm text-muted hover:bg-[#f2f6f3]">←</Link>
          <div className="min-w-0"><div className="flex items-center gap-2"><h1 className="truncate text-sm font-extrabold">{draft.title || "제목 없는 문서"}</h1><span className={`shrink-0 rounded-full px-2 py-1 text-[9px] font-extrabold ${article?.status === "PUBLISHED" ? "bg-[#e6f6ea] text-[#28753c]" : "bg-[#eff2ef] text-muted"}`}>{article?.status === "PUBLISHED" ? "발행됨" : "초안"}</span>{dirty && <span className="h-1.5 w-1.5 rounded-full bg-[#e5a83a]" title="저장하지 않은 변경" />}</div><p className="mt-0.5 text-[10px] text-muted-soft">{notice ?? (dirty ? "저장하지 않은 변경 사항이 있습니다" : "모든 변경 사항이 저장됨")}</p></div>
        </div>
        <div className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto sm:flex-wrap sm:items-center lg:shrink-0"><div className="col-span-2 grid grid-cols-3 rounded-[9px] border border-[#dce3dd] bg-[#f6f8f6] p-1 sm:flex">{(["WRITE", "SPLIT", "PREVIEW"] as EditorMode[]).map((item) => <button key={item} type="button" onClick={() => setMode(item)} className={`rounded-[7px] px-3 py-2 text-[10px] font-extrabold sm:py-1.5 ${mode === item ? "bg-white text-[#24432d] shadow-sm" : "text-muted"}`}>{item === "WRITE" ? "작성" : item === "SPLIT" ? "분할" : "미리보기"}</button>)}</div><Button type="submit" disabled={saving || !dirty} className="w-full border-[#d8e0da] bg-white text-[#435048] hover:bg-[#f1f6f2] sm:w-auto">{saving ? "저장 중..." : "저장"}</Button><Button type="button" variant={article?.status === "PUBLISHED" ? "secondary" : "primary"} disabled={saving} onClick={togglePublish} className="w-full sm:w-auto">{article?.status === "PUBLISHED" ? "발행 취소" : "저장 후 발행"}</Button></div>
      </header>

      {error && <div className="mb-5 flex items-center justify-between rounded-[12px] border border-[#efcccc] bg-[#fff4f4] px-4 py-3 text-sm text-danger"><span>{error}</span><button type="button" onClick={() => setError(null)}>×</button></div>}

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_300px]">
        <main className="min-w-0 overflow-hidden rounded-[18px] border border-[#dce4de] bg-white shadow-sm">
          <div className="border-b border-[#e4eae5] p-4 sm:p-8">
            <Input value={draft.title} onChange={(event) => update("title", event.target.value)} placeholder="문서 제목" maxLength={180} className="h-auto min-h-0 rounded-none border-0 bg-transparent px-0 text-2xl font-black tracking-[-.035em] text-[#18221b] shadow-none focus:ring-0 placeholder:text-muted-soft sm:text-3xl" />
            <Textarea value={draft.summary} onChange={(event) => update("summary", event.target.value)} placeholder="목록과 문서 상단에 표시할 한두 문장의 요약을 적어 주세요." maxLength={400} rows={2} className="mt-3 min-h-0 resize-none rounded-none border-0 bg-transparent px-0 py-0 text-sm leading-6 text-muted focus:ring-0" />
          </div>

          <div className="flex flex-wrap items-center gap-1 border-b border-[#e4eae5] bg-[#fbfcfb] px-4 py-2">
            {TOOLBAR.map((tool) => <button key={tool.title} type="button" title={tool.title} onClick={() => insertMarkdown(tool.before, tool.after)} className="grid min-h-8 min-w-8 place-items-center rounded-[7px] px-2 text-[11px] font-extrabold text-muted hover:bg-[#ebf1ec] hover:text-[#2f7140]">{tool.label}</button>)}
            <span className="mx-1 h-5 w-px bg-[#dce3dd]" />
            <button type="button" onClick={() => chooseImage("CONTENT")} disabled={uploading} className="inline-flex min-h-8 items-center gap-1.5 rounded-[7px] px-2.5 text-[11px] font-extrabold text-muted hover:bg-[#ebf1ec] hover:text-[#2f7140]">{uploading ? <Spinner className="h-3 w-3" /> : "▧"} 이미지</button>
            <span className="mx-1 h-5 w-px bg-[#dce3dd]" />
            <button type="button" onClick={() => setShowImport(true)} className="inline-flex min-h-8 items-center gap-1.5 rounded-[7px] px-2.5 text-[11px] font-extrabold text-muted hover:bg-[#ebf1ec] hover:text-[#2f7140]" title="user-guides 양식 붙여넣기로 폼 채우기">⇪ 가져오기</button>
            <span className="ml-auto hidden text-[10px] text-muted-soft sm:block">Markdown · ⌘S 저장</span>
          </div>

          <div className={`grid min-h-[520px] sm:min-h-[680px] ${mode === "SPLIT" ? "lg:grid-cols-2" : "grid-cols-1"}`}>
            {mode !== "PREVIEW" && <div className={`min-w-0 ${mode === "SPLIT" ? "lg:border-r lg:border-[#e4eae5]" : ""}`}><textarea ref={textareaRef} value={draft.content} onChange={(event) => update("content", event.target.value)} spellCheck={false} className="h-full min-h-[520px] w-full resize-none bg-[#fcfdfc] p-4 font-mono text-[13px] leading-7 text-[#344139] outline-none placeholder:text-muted-soft sm:min-h-[680px] sm:p-8" placeholder="Markdown으로 사용 설명서를 작성하세요." /></div>}
            {mode !== "WRITE" && <div className={`min-w-0 overflow-y-auto bg-white p-4 sm:p-8 lg:max-h-[820px] ${mode === "SPLIT" ? "hidden lg:block" : ""}`}><div className="mb-7 border-b border-[#e5ebe6] pb-6"><span className="text-[10px] font-extrabold uppercase tracking-[.12em] text-[#347145]">{draft.category || "카테고리"}</span><h1 className="mt-3 break-words text-2xl font-black tracking-[-.04em] sm:text-3xl">{draft.title || "문서 제목"}</h1><p className="mt-3 break-words text-sm leading-6 text-muted">{draft.summary || "문서 요약이 여기에 표시됩니다."}</p></div><MarkdownRenderer content={draft.content} resolveImageUrl={toAdminDocsImageUrl} className="[--foreground:#1d2720] [--muted:#4d5a52] [--line:#e1e8e3] [--brand:#68c77d] [--brand-strong:#276c38]" /></div>}
          </div>
        </main>

        <aside className="grid gap-4 xl:sticky xl:top-20">
          <section className="rounded-[16px] border border-[#dce4de] bg-white p-4 shadow-sm"><h2 className="text-xs font-extrabold text-[#2c3730]">문서 설정</h2><div className="mt-4"><Field label="카테고리" htmlFor="docs-category"><Input id="docs-category" value={draft.category} onChange={(event) => update("category", event.target.value)} placeholder="예: 인스턴스, 배포, 협업" className="border-[#d9e1db] bg-[#fbfdfb] text-[#344038]" /></Field><Field label="문서 주소" htmlFor="docs-slug"><div className="flex items-center rounded-[9px] border border-[#d9e1db] bg-[#fbfdfb] px-3"><span className="text-xs text-muted-soft">/docs/</span><input id="docs-slug" value={draft.slug ?? ""} onChange={(event) => update("slug", event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ""))} placeholder="instance-create" className="min-w-0 flex-1 bg-transparent py-3 text-xs text-[#344038] outline-none" /></div><span className="text-[10px] font-normal text-muted-soft">비워두면 제목을 기준으로 자동 생성됩니다.</span></Field><Field label="정렬 순서" htmlFor="docs-sort"><Input id="docs-sort" type="number" value={draft.sortOrder} onChange={(event) => update("sortOrder", Number(event.target.value))} className="border-[#d9e1db] bg-[#fbfdfb] text-[#344038]" /></Field><label className="flex items-start gap-3 rounded-[11px] border border-[#e0e6e1] bg-[#fafcfa] p-3"><input type="checkbox" checked={draft.featured} onChange={(event) => update("featured", event.target.checked)} className="mt-0.5 h-4 w-4 accent-[#64be78]" /><span><strong className="block text-xs">추천 가이드로 표시</strong><span className="mt-1 block text-[10px] leading-4 text-muted">Docs 첫 화면 상단에 크게 노출합니다.</span></span></label></div></section>

          <section className="rounded-[16px] border border-[#dce4de] bg-white p-4 shadow-sm"><div className="flex items-center justify-between"><h2 className="text-xs font-extrabold">커버 이미지</h2>{draft.coverImageUrl && <button type="button" onClick={() => update("coverImageUrl", null)} className="text-[10px] font-bold text-danger">제거</button>}</div>{draft.coverImageUrl ? <img src={toAdminDocsImageUrl(draft.coverImageUrl)} alt="" className="mt-3 aspect-[16/9] w-full rounded-[11px] border border-[#e0e6e1] object-cover" /> : <button type="button" onClick={() => chooseImage("COVER")} className="mt-3 grid aspect-[16/9] w-full place-items-center rounded-[11px] border border-dashed border-[#cad6cc] bg-[#f8fbf8] text-center"><span><span className="block text-xl text-[#7caa86]">▧</span><span className="mt-1 block text-[10px] font-bold text-muted">이미지 업로드</span></span></button>}{draft.coverImageUrl && <button type="button" onClick={() => chooseImage("COVER")} className="mt-2 w-full text-[10px] font-bold text-muted hover:text-[#347b44]">다른 이미지로 교체</button>}</section>

          <section className="rounded-[16px] border border-[#dce4de] bg-white p-4 shadow-sm"><h2 className="text-xs font-extrabold">태그</h2><div className="mt-3 flex gap-2"><Input value={tagInput} onChange={(event) => setTagInput(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addTag(); } }} placeholder="태그 입력" className="min-h-9 border-[#d9e1db] bg-[#fbfdfb] text-xs text-[#344038]" /><button type="button" onClick={addTag} className="shrink-0 rounded-[9px] border border-[#d9e1db] px-3 text-xs font-bold text-[#56635a]">추가</button></div><div className="mt-3 flex flex-wrap gap-1.5">{draft.tags.map((tag) => <button key={tag} type="button" onClick={() => update("tags", draft.tags.filter((item) => item !== tag))} className="rounded-full bg-[#edf5ef] px-2.5 py-1.5 text-[10px] font-bold text-[#3e7449]">#{tag} ×</button>)}</div><p className="mt-2 text-[10px] text-muted-soft">최대 12개 · 검색 키워드로도 사용됩니다.</p></section>
        </aside>
      </div>

      {showImport && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" onClick={() => setShowImport(false)}>
          <div className="w-full max-w-[560px] rounded-[16px] border border-[#dce4de] bg-white p-5 shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <h2 className="text-sm font-extrabold text-[#1d2720]">초안 붙여넣기 가져오기</h2>
            <p className="mt-1 text-[11px] leading-4 text-muted">
              <code className="rounded bg-[#eef3ef] px-1 text-[10px]">---</code> 로 구분된 초안 양식(제목·요약·본문·카테고리·문서 주소·정렬 순서·추천·태그)을 붙여넣으면 아래 폼이 자동으로 채워집니다. 채운 뒤 검토하고 저장·발행하세요.
            </p>
            <textarea
              value={importText}
              onChange={(event) => setImportText(event.target.value)}
              rows={12}
              autoFocus
              spellCheck={false}
              placeholder={"---\n제목\n인스턴스(VM) 생성하기\n---\n요약\n...\n---\n본문\n\n## ...\n---\n카테고리\n인스턴스\n---\n..."}
              className="mt-3 w-full resize-none rounded-[10px] border border-[#d9e1db] bg-[#fbfdfb] p-3 font-mono text-[12px] leading-6 text-[#344139] outline-none placeholder:text-muted-soft focus:border-[#9dc7a8]"
            />
            <div className="mt-3 flex items-center justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowImport(false)} className="border-[#d8e0da] bg-white text-[#435048] hover:bg-[#f1f6f2]">취소</Button>
              <Button type="button" variant="primary" onClick={applyImport} disabled={!importText.trim()}>폼 채우기</Button>
            </div>
          </div>
        </div>
      )}
    </form>
  );
}
