"use client";

import { useRef, useState } from "react";
import { cn } from "./cn";

const DRAG_THRESHOLD_PX = 4;

// 도커 이미지명처럼 공백 없이 길게 이어지는 값이 테이블/행 폭을 밀어내는 걸 막기 위한 컴포넌트.
// 기본은 한 줄 말줄임(뒷부분 생략)이고, 클릭하면 아래로 펼쳐져 전체 내용을 보여준다(다시 클릭 시 닫힘).
// 드래그로 텍스트를 선택해 복사하는 동작과 충돌하지 않도록, 포인터가 눌린 지점에서 일정 거리
// 이상 움직였거나(드래그) 실제로 텍스트가 선택된 상태면 클릭으로 취급하지 않고 토글을 건너뛴다.
export function ExpandableText({ text, className }: { text: string; className?: string }) {
  const [expanded, setExpanded] = useState(false);
  const downPos = useRef<{ x: number; y: number } | null>(null);

  if (!text) {
    return <span className={className}>—</span>;
  }

  function handlePointerDown(e: React.PointerEvent) {
    downPos.current = { x: e.clientX, y: e.clientY };
  }

  function handlePointerUp(e: React.PointerEvent) {
    const start = downPos.current;
    downPos.current = null;
    if (!start) return;
    const dragged =
      Math.abs(e.clientX - start.x) > DRAG_THRESHOLD_PX || Math.abs(e.clientY - start.y) > DRAG_THRESHOLD_PX;
    const hasSelection = (window.getSelection()?.toString().length ?? 0) > 0;
    if (dragged || hasSelection) return;
    setExpanded((prev) => !prev);
  }

  return (
    <div
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
      role="button"
      tabIndex={0}
      aria-expanded={expanded}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          setExpanded((prev) => !prev);
        }
      }}
      title={expanded ? undefined : text}
      className={cn("max-w-full cursor-pointer select-text", className)}
    >
      {expanded ? (
        <span className="block whitespace-normal break-all">{text}</span>
      ) : (
        <span className="block truncate">{text}</span>
      )}
    </div>
  );
}
