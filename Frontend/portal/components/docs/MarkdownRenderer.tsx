/* eslint-disable @next/next/no-img-element */
"use client";

import { Children, isValidElement, type ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { cn } from "@/components/ui/cn";
import { markdownHeadingId } from "./markdown";

function textOf(node: ReactNode): string {
  return Children.toArray(node)
    .map((child) => {
      if (typeof child === "string" || typeof child === "number") return String(child);
      if (isValidElement<{ children?: ReactNode }>(child)) return textOf(child.props.children);
      return "";
    })
    .join("");
}

export function MarkdownRenderer({ content, className }: { content: string; className?: string }) {
  const headingCounts = new Map<string, number>();

  function uniqueHeadingId(text: string): string {
    const base = markdownHeadingId(text) || "section";
    const count = headingCounts.get(base) ?? 0;
    headingCounts.set(base, count + 1);
    return count === 0 ? base : `${base}-${count + 1}`;
  }

  return (
    <article className={cn("min-w-0 [overflow-wrap:anywhere] text-[15px] leading-7 text-foreground", className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => <h1 className="mb-5 mt-10 text-3xl font-black tracking-[-.035em] first:mt-0">{children}</h1>,
          h2: ({ children }) => {
            const text = textOf(children);
            return <h2 id={uniqueHeadingId(text)} className="mb-4 mt-12 scroll-mt-24 border-b border-line pb-3 text-2xl font-black tracking-[-.025em]">{children}</h2>;
          },
          h3: ({ children }) => {
            const text = textOf(children);
            return <h3 id={uniqueHeadingId(text)} className="mb-3 mt-9 scroll-mt-24 text-xl font-extrabold">{children}</h3>;
          },
          h4: ({ children }) => <h4 className="mb-2 mt-7 text-base font-extrabold">{children}</h4>,
          p: ({ children }) => <p className="my-4 text-muted leading-7">{children}</p>,
          strong: ({ children }) => <strong className="font-extrabold text-foreground">{children}</strong>,
          em: ({ children }) => <em className="text-foreground">{children}</em>,
          a: ({ href, children }) => {
            const external = Boolean(href?.startsWith("http"));
            return <a href={href} target={external ? "_blank" : undefined} rel={external ? "noreferrer noopener" : undefined} className="font-bold text-brand-strong underline decoration-brand/35 underline-offset-4 hover:decoration-brand">{children}</a>;
          },
          ul: ({ children }) => <ul className="my-5 list-disc space-y-2 pl-6 text-muted marker:text-brand">{children}</ul>,
          ol: ({ children }) => <ol className="my-5 list-decimal space-y-2 pl-6 text-muted marker:font-bold marker:text-brand-strong">{children}</ol>,
          li: ({ children }) => <li className="pl-1 leading-7">{children}</li>,
          blockquote: ({ children }) => <blockquote className="my-6 rounded-r-[14px] border-l-4 border-brand bg-brand/[0.07] px-5 py-3 text-muted [&>p]:my-1">{children}</blockquote>,
          hr: () => <hr className="my-10 border-line" />,
          pre: ({ children }) => <pre className="my-6 overflow-x-auto rounded-[14px] border border-line-strong bg-[#0b0e0c] p-4 text-[13px] leading-6 text-[#dce8df] shadow-inner [scrollbar-gutter:stable]">{children}</pre>,
          code: ({ className: codeClassName, children }) => codeClassName
            ? <code className={cn("font-mono", codeClassName)}>{children}</code>
            : <code className="rounded-md border border-line bg-white/[0.05] px-1.5 py-0.5 font-mono text-[.88em] text-brand-strong">{children}</code>,
          table: ({ children }) => <div className="my-7 overflow-x-auto rounded-[14px] border border-line"><table className="w-full min-w-[560px] border-collapse text-left text-sm">{children}</table></div>,
          thead: ({ children }) => <thead className="bg-white/[0.04] text-foreground">{children}</thead>,
          th: ({ children }) => <th className="border-b border-line px-4 py-3 text-xs font-extrabold">{children}</th>,
          td: ({ children }) => <td className="border-b border-line px-4 py-3 text-sm text-muted last:[tr:last-child_&]:border-b-0">{children}</td>,
          img: ({ src, alt }) => <figure className="my-8"><img src={src ?? ""} alt={alt ?? ""} loading="lazy" className="max-h-[680px] w-full rounded-[16px] border border-line object-contain bg-black/10 shadow-xl shadow-black/10" />{alt && <figcaption className="mt-2 text-center text-xs text-muted-soft">{alt}</figcaption>}</figure>,
          input: ({ type, checked, disabled }) => type === "checkbox"
            ? <input type="checkbox" checked={checked} disabled={disabled} readOnly className="mr-2 h-4 w-4 accent-brand" />
            : <input type={type} disabled={disabled} />,
        }}
      >
        {content}
      </ReactMarkdown>
    </article>
  );
}
