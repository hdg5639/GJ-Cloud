import type { DocsArticleInput } from "@/lib/types";

// user-guides 초안 양식(`---` 구분: 제목/요약/본문/카테고리/문서 주소/정렬 순서/추천/태그)을
// 파싱해 DocsArticleInput 부분값으로 변환한다. 라벨 기준이라 순서가 바뀌어도 동작한다.
// 본문의 표 구분자(`| --- |`)는 정확히 `---` 한 줄이 아니라 경계로 잡히지 않는다.
const LABEL_TO_FIELD: Record<string, keyof DocsArticleInput> = {
  "제목": "title",
  "요약": "summary",
  "본문": "content",
  "카테고리": "category",
  "문서 주소": "slug",
  "정렬 순서": "sortOrder",
  "추천 카테고리 표시 유무": "featured",
  "태그": "tags",
};

const FIELD_LABEL: Record<string, string> = {
  title: "제목", summary: "요약", content: "본문", category: "카테고리",
  slug: "문서 주소", sortOrder: "정렬 순서", featured: "추천", tags: "태그",
};

export interface ParsedDocsDraft {
  fields: Partial<DocsArticleInput>;
  matched: string[]; // 채워진 필드의 한글 라벨(사용자 안내용)
}

function isPlaceholder(value: string): boolean {
  // 템플릿의 "(...적어주세요)" 같은 안내문이면 실제 값이 아님
  return /^\(.*\)$/.test(value.trim());
}

export function parseDocsDraft(raw: string): ParsedDocsDraft | null {
  const chunks = raw.split(/^---\s*$/m).map((c) => c.trim()).filter(Boolean);
  const fields: Partial<DocsArticleInput> = {};
  const matched: string[] = [];

  for (const chunk of chunks) {
    const nl = chunk.indexOf("\n");
    const label = (nl === -1 ? chunk : chunk.slice(0, nl)).trim();
    const value = (nl === -1 ? "" : chunk.slice(nl + 1)).trim();
    const field = LABEL_TO_FIELD[label];
    if (!field || isPlaceholder(value)) continue;

    switch (field) {
      case "sortOrder":
        fields.sortOrder = Number.parseInt(value, 10) || 0;
        break;
      case "featured":
        fields.featured = /^(예|y|yes|true|1|on)$/i.test(value);
        break;
      case "tags":
        fields.tags = value.split(",").map((t) => t.trim().replace(/^#/, "")).filter(Boolean).slice(0, 12);
        break;
      default:
        fields[field] = value as never;
        break;
    }
    matched.push(FIELD_LABEL[field] ?? label);
  }

  // 제목이나 본문 중 하나도 없으면 사실상 인식 실패
  if (!fields.title && !fields.content) return null;
  return { fields, matched };
}
