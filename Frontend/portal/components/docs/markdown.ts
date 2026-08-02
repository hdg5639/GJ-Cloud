export interface MarkdownHeading {
  id: string;
  level: 2 | 3;
  text: string;
}

export function markdownHeadingId(value: string): string {
  return value
    .toLowerCase()
    .replace(/[`*_~\[\]]/g, "")
    .replace(/[^\p{L}\p{N}\s-]/gu, "")
    .trim()
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-");
}

export function extractMarkdownHeadings(markdown: string): MarkdownHeading[] {
  const seen = new Map<string, number>();
  return Array.from(markdown.matchAll(/^(##|###)\s+(.+)$/gm)).map((match) => {
    const text = match[2].replace(/\s+#+\s*$/, "").replace(/[`*_~\[\]]/g, "").trim();
    const base = markdownHeadingId(text) || "section";
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    return {
      id: count === 0 ? base : `${base}-${count + 1}`,
      level: match[1].length as 2 | 3,
      text,
    };
  });
}
