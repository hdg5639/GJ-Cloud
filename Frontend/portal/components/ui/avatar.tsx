import { cn } from "./cn";

// 프로필 이미지가 있으면 이미지, 없으면 닉네임(없으면 이메일) 첫 글자로 된 원형 아바타.
// 사이드바 자기 아바타, 조직 멤버 리스트, 초대 검색 드롭다운이 전부 이 컴포넌트 하나를 재사용한다.
export function Avatar({
  nickname,
  email,
  profileImageUrl,
  sizePx = 34,
  textSizeClassName = "text-xs",
  className,
}: {
  nickname?: string | null;
  email?: string | null;
  profileImageUrl?: string | null;
  sizePx?: number;
  textSizeClassName?: string;
  className?: string;
}) {
  const style = { width: sizePx, height: sizePx };

  if (profileImageUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={profileImageUrl} alt="" className={cn("shrink-0 rounded-full object-cover", className)} style={style} />
    );
  }

  const initial = (nickname?.trim()?.[0] ?? email?.[0] ?? "?").toUpperCase();
  return (
    <div
      className={cn(
        "grid shrink-0 place-items-center rounded-full bg-white/[0.06] font-extrabold",
        textSizeClassName,
        className
      )}
      style={style}
    >
      {initial}
    </div>
  );
}
