"use client";

import type { ApiCallLogEntry } from "./types";

// GamjaBox_2.0_Key_Features.md 41절 MVP 출력 "요청·응답 확인" — 라이브 프리뷰가 실제로 보낸 요청/받은
// 응답을 그대로 펼쳐볼 수 있게 한다. 최신 호출이 위로 오도록 호출 쪽(config.onApiCall)에서 순서를 맞춘다.
export function ApiCallLog({ entries }: { entries: ApiCallLogEntry[] }) {
  if (entries.length === 0) {
    return <p className="text-xs text-muted-soft">아직 API 호출이 없습니다. 위에서 화면을 조작해보세요.</p>;
  }

  return (
    <div className="space-y-1.5">
      {entries.map((entry) => (
        <details key={entry.id} className="rounded-md border border-line-strong bg-white/[0.02] text-xs">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2">
            <span
              className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                entry.error ? "bg-danger/15 text-danger" : "bg-brand/15 text-brand-strong"
              }`}
            >
              {entry.status ?? "실패"}
            </span>
            <span className="font-bold text-muted-soft">{entry.method}</span>
            <span className="truncate text-muted">{entry.url}</span>
            <span className="ml-auto shrink-0 text-[10px] text-muted-soft">
              {new Date(entry.timestamp).toLocaleTimeString()}
            </span>
          </summary>
          <div className="space-y-2 border-t border-line-strong px-3 py-2">
            {entry.error && <p className="text-danger">{entry.error}</p>}
            {entry.requestBody != null && (
              <div>
                <p className="mb-1 font-bold text-muted-soft">요청 본문</p>
                <pre className="overflow-x-auto rounded bg-black/30 p-2 text-[11px] text-muted">
                  {JSON.stringify(entry.requestBody, null, 2)}
                </pre>
              </div>
            )}
            <div>
              <p className="mb-1 font-bold text-muted-soft">응답</p>
              <pre className="overflow-x-auto rounded bg-black/30 p-2 text-[11px] text-muted">
                {entry.responseBody != null ? JSON.stringify(entry.responseBody, null, 2) : "(본문 없음)"}
              </pre>
            </div>
          </div>
        </details>
      ))}
    </div>
  );
}
