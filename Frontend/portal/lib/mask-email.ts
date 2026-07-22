// 닉네임 중복을 허용하는 대신, 다른 사용자에게 신원을 보여줄 때(초대 검색/조직 멤버 목록) 이메일을
// 절반쯤 가려서 같이 보여줘 식별 가능하게 한다. 로컬파트 앞부분만 남기고 나머지는 * 처리, 도메인은 그대로.
export function maskEmail(email: string): string {
  const at = email.indexOf("@");
  if (at <= 0) return email;

  const local = email.slice(0, at);
  const domain = email.slice(at);
  const visibleLength = Math.max(1, Math.ceil(local.length / 2));
  const visible = local.slice(0, visibleLength);
  const masked = "*".repeat(Math.max(2, local.length - visibleLength));

  return `${visible}${masked}${domain}`;
}
