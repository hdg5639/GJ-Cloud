"use client";

import type { PreviewInputSchema } from "@/lib/types";
import { Field, Input, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { isPasswordLikeField } from "../api";

function sensitiveField(name: string): boolean {
  return isPasswordLikeField(name) || /(secret|token|authorization|api.?key|cookie|private.?key)/i.test(name);
}

export function fieldLabel(name: string, schema?: PreviewInputSchema | null): string {
  const labels: Record<string, string> = { name: "이름", title: "제목", description: "설명", content: "내용", quantity: "수량", price: "가격", currency: "통화", category: "카테고리", initialStock: "초기 재고", lowStockThreshold: "재고 경고 기준", inventory: "재고", email: "이메일", customerName: "받는 분", shippingAddress: "배송지", postalCode: "우편번호", addressLine1: "주소", addressLine2: "상세 주소", city: "도시", countryCode: "국가 코드", images: "이미지", url: "주소", alt: "이미지 설명", primary: "대표 이미지", optionGroups: "옵션 그룹", options: "옵션", code: "코드", label: "표시 이름", required: "필수 여부", additionalPrice: "추가 금액", available: "선택 가능", variantCode: "상품 옵션 코드" };
  return schema?.title || (name === "available" && ["integer", "number"].includes(schema?.type ?? "") ? "재고 수량" : labels[name]) || name.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replaceAll("_", " ").replace(/^./, c => c.toUpperCase());
}

export function SchemaField({ name, schema, value, required = false, onChange, depth = 0 }: {
  name: string; schema?: PreviewInputSchema | null; value: unknown; required?: boolean;
  onChange: (value: unknown) => void; depth?: number;
}) {
  const label = fieldLabel(name.split(".").at(-1) ?? name, schema);
  const id = `product-input-${name}`;
  if (depth > 7) return <p className="text-xs text-muted">{label}: 입력 구조를 Inspector에서 확인해주세요.</p>;
  if (schema?.type === "object" && Object.keys(schema.properties).length > 0) {
    if (!required && (value === undefined || value === null || value === "")) return <div className="space-y-2"><p className="text-sm font-semibold">{label} <span className="text-xs text-muted">선택</span></p><Button type="button" size="small" onClick={() => onChange({})}>{label} 입력하기</Button></div>;
    const object = value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
    return <fieldset className="min-w-0 space-y-3 border-t border-[var(--px-line)] py-4"><legend className="px-1 text-sm font-semibold">{label}{required ? " *" : ""}</legend>
      <div className="grid min-w-0 gap-x-4 gap-y-3 sm:grid-cols-2">{Object.entries(schema.properties).map(([key, child]) => <SchemaField key={key} name={`${name}.${key}`} schema={child} value={object[key]} required={schema.required.includes(key)} depth={depth + 1} onChange={next => onChange({ ...object, [key]: next })} />)}</div>
    </fieldset>;
  }
  if (schema?.type === "array" && schema.items) {
    const values = Array.isArray(value) ? value : [];
    return <fieldset className="min-w-0 space-y-3 border-t border-[var(--px-line)] py-4"><legend className="px-1 text-sm font-semibold">{label}{required ? " *" : ""}</legend>
      {values.map((item, index) => <div key={index} className="min-w-0 border-l-2 border-[var(--px-line)] pl-3">
        <SchemaField name={`${name}.${index + 1}`} schema={schema.items} value={item} depth={depth + 1} onChange={next => onChange(values.map((v, i) => i === index ? next : v))} />
        <Button type="button" size="small" onClick={() => onChange(values.filter((_, i) => i !== index))}>{label} {index + 1} 제거</Button>
      </div>)}
      <Button type="button" size="small" onClick={() => onChange([...values, schema.items?.type === "object" ? {} : ""])}>{label} 추가</Button>
    </fieldset>;
  }
  const type = schema?.type;
  const inputType = sensitiveField(name) ? "password" : type === "number" || type === "integer" ? "number"
    : schema?.format === "date" ? "date" : schema?.format === "date-time" ? "datetime-local" : schema?.format === "email" ? "email" : "text";
  let text = value === null || value === undefined ? "" : String(value);
  if (inputType === "datetime-local" && text && !Number.isNaN(Date.parse(text))) {
    const date = new Date(text);
    const pad = (part: number) => String(part).padStart(2, "0");
    text = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  return <Field label={label} htmlFor={id}>
    {schema?.enumValues?.length ? <select aria-label={label} aria-describedby={schema?.description ? `${id}-hint` : undefined} id={id} value={text} required={required} className="min-h-10 w-full rounded-md border border-line bg-panel px-3 text-sm" onChange={e => onChange(e.target.value !== "" && (type === "number" || type === "integer") ? Number(e.target.value) : e.target.value)}><option value="">선택해주세요</option>{schema.enumValues.map(v => <option key={v} value={v}>{v}</option>)}</select>
      : type === "boolean" ? <select aria-label={label} aria-describedby={schema?.description ? `${id}-hint` : undefined} id={id} value={text} required={required} className="min-h-10 w-full rounded-md border border-line bg-panel px-3 text-sm" onChange={e => onChange(e.target.value === "" ? "" : e.target.value === "true")}><option value="">선택해주세요</option><option value="true">예</option><option value="false">아니요</option></select>
      : /(description|content|message|reason|note|body)/i.test(name) ? <Textarea aria-label={label} aria-describedby={schema?.description ? `${id}-hint` : undefined} id={id} required={required} value={text} onChange={e => onChange(e.target.value)} />
      : <Input aria-label={label} aria-describedby={schema?.description ? `${id}-hint` : undefined} id={id} type={inputType} required={required} value={text} min={schema?.minimum ?? undefined} max={schema?.maximum ?? undefined} step={type === "integer" ? 1 : type === "number" ? "any" : undefined} onChange={e => onChange(inputType === "number" && e.target.value !== "" ? Number(e.target.value)
        : inputType === "datetime-local" && e.target.value && !Number.isNaN(Date.parse(e.target.value)) ? new Date(e.target.value).toISOString() : e.target.value)} />}
    {schema?.description && <span id={`${id}-hint`} className="text-xs leading-5 text-muted">{schema.description}</span>}
  </Field>;
}

export function resourceImage(row: Record<string, unknown>): string | null {
  const images = Array.isArray(row.images) ? row.images : [];
  const first = images.find(image => image && typeof image === "object" && (image as Record<string, unknown>).primary === true) ?? images[0];
  const value = row.imageUrl ?? row.thumbnailUrl ?? (typeof first === "string" ? first : first && typeof first === "object" ? (first as Record<string, unknown>).url : null);
  return typeof value === "string" && /^https?:\/\//i.test(value) ? value : null;
}

// Show response content as a document, with transport metadata available in the Inspector.
export function ResourceDetails({ value, depth = 0 }: { value: unknown; depth?: number }) {
  if (value === null || value === undefined || depth > 5) return null;
  if (Array.isArray(value)) return <ul className="space-y-3">{value.map((item, index) => <li key={index}><ResourceDetails value={item} depth={depth + 1} /></li>)}</ul>;
  if (typeof value !== "object") return <span className="break-words text-sm">{typeof value === "boolean" ? value ? "예" : "아니요" : String(value)}</span>;
  const row = value as Record<string, unknown>;
  if ("data" in row && Object.keys(row).every(key => ["data", "success", "code", "message", "errorCode", "status", "timestamp"].includes(key))) return <ResourceDetails value={row.data} depth={depth} />;
  const heading = row.title ?? row.name ?? row.subject;
  const body = row.description ?? row.content ?? row.summary;
  const image = resourceImage(row);
  return <div className="min-w-0 space-y-5">
    {image && <div className="overflow-hidden rounded-xl bg-[var(--px-surface-soft)]">
      {/* eslint-disable-next-line @next/next/no-img-element -- also exported as a standalone Vite runtime */}
      <img src={image} alt={typeof heading === "string" ? heading : ""} className="max-h-80 w-full object-contain" loading="lazy" referrerPolicy="no-referrer" />
    </div>}
    {(heading || body || row.price !== undefined) && <div>
      {typeof heading === "string" && <h3 className="text-lg font-semibold">{heading}</h3>}
      {typeof body === "string" && <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-7 text-[var(--px-muted)]">{body}</p>}
      {typeof row.price === "number" && <p className="mt-3 text-xl font-semibold">{row.price.toLocaleString()} {typeof row.currency === "string" ? row.currency : ""}</p>}
    </div>}
    <dl className="grid min-w-0 gap-x-6 gap-y-4 sm:grid-cols-2">{Object.entries(row).filter(([key, val]) => val !== null && val !== undefined && !/^(id|.*Id|slug|createdAt|updatedAt|title|name|subject|description|content|summary|price|currency|images|imageUrl|thumbnailUrl)$/i.test(key)).map(([key, val]) => <div key={key} className={`min-w-0 ${typeof val === "object" ? "sm:col-span-2" : ""}`}>
      <dt className="mb-1 text-xs font-medium text-[var(--px-muted)]">{fieldLabel(key)}</dt>
      <dd>{sensitiveField(key) ? "••••••" : <ResourceDetails value={val} depth={depth + 1} />}</dd>
    </div>)}</dl>
  </div>;
}
