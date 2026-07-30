"use client";

import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { isPasswordLikeField } from "./api";

// Auto Preview 생성/수정/저니 입력 폼이 공유하는 필드 렌더러.
// 백엔드 Capability.fields는 이름(string)만 주므로, 이름에서 사람이 이해하는 라벨과 입력 컨트롤 종류를
// 추론해 "스웨거 try-it-out"식 밋밋한 텍스트 나열 대신 실제 제품 폼처럼 보이게 만든다. 값은 문자열로
// 유지해 API 요청 바디 형태(Record<string,string>)를 그대로 둔다.

type FieldControl = "text" | "email" | "url" | "number" | "date" | "datetime" | "textarea" | "boolean" | "password";

export function humanizeFieldLabel(name: string): string {
  return name
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[._-]+/g, " ")
    .replace(/\bid\b/gi, "ID")
    .replace(/\burl\b/gi, "URL")
    .trim()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function inferFieldControl(name: string): FieldControl {
  const n = name.toLowerCase();
  if (isPasswordLikeField(name)) return "password";
  if (/mail/.test(n)) return "email";
  if (/(^|[._-])(url|uri|link|website|homepage|endpoint|webhook|href|avatar|image|photo|thumbnail|logo)([._-]|$)/.test(n)) return "url";
  if (/(datetime|timestamp|_at$|expiresat|scheduledat)/.test(n)) return "datetime";
  if (/(^|[._-])(date|dob|birth|deadline|due|start|end|expiry|expires)([._-]|$)|(date)$/.test(n)) return "date";
  if (/(count|amount|price|cost|fee|qty|quantity|total|number|num|age|size|weight|height|width|length|score|rating|rank|stock|balance|duration|percent|ratio|limit|max|min|capacity|priority)/.test(n)) return "number";
  if (/(description|reason|note|memo|message|content|body|comment|summary|detail|bio|remark|address|instruction|context|payload|excerpt|caption|text)/.test(n)) return "textarea";
  if (/^(is|has|can|should|allow|enable|require)[a-z]|(enabled|disabled|active|visible|published|archived|deleted|default|verified|featured|public|private|readonly|completed|approved)$/.test(n)) return "boolean";
  return "text";
}

function placeholderFor(name: string, control: FieldControl): string {
  switch (control) {
    case "email": return "name@example.com";
    case "url": return "https://example.com";
    case "number": return "0";
    case "password": return "••••••••";
    case "textarea": return `${humanizeFieldLabel(name)}을(를) 입력하세요`;
    case "date":
    case "datetime":
    case "boolean": return "";
    default: return `${humanizeFieldLabel(name)} 입력`;
  }
}

export function PreviewFormFields({
  fields,
  values,
  onChange,
  idPrefix,
}: {
  fields: string[];
  values: Record<string, string>;
  onChange: (field: string, value: string) => void;
  idPrefix: string;
}) {
  return (
    <div className="grid gap-1 sm:grid-cols-2 [&>*]:min-w-0">
      {fields.map((field) => {
        const control = inferFieldControl(field);
        const id = `${idPrefix}-${field}`;
        const label = humanizeFieldLabel(field);
        const value = values[field] ?? "";
        const wide = control === "textarea";
        return (
          <Field
            key={field}
            label={label}
            htmlFor={id}
            className={wide ? "sm:col-span-2" : undefined}
          >
            {control === "textarea" ? (
              <Textarea id={id} value={value} placeholder={placeholderFor(field, control)}
                onChange={(event) => onChange(field, event.target.value)} />
            ) : control === "boolean" ? (
              <Select id={id} value={value} onChange={(event) => onChange(field, event.target.value)}>
                <option value="">선택 안 함</option>
                <option value="true">예</option>
                <option value="false">아니오</option>
              </Select>
            ) : (
              <Input
                id={id}
                type={
                  control === "datetime" ? "datetime-local"
                  : control === "date" ? "date"
                  : control === "number" ? "number"
                  : control === "email" ? "email"
                  : control === "url" ? "url"
                  : control === "password" ? "password"
                  : "text"
                }
                value={value}
                placeholder={placeholderFor(field, control)}
                onChange={(event) => onChange(field, event.target.value)}
              />
            )}
          </Field>
        );
      })}
    </div>
  );
}
