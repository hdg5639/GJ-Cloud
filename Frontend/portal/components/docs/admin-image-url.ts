const USER_DOCS_IMAGE_PREFIX = "/users/docs/images/";
const ADMIN_DOCS_IMAGE_PREFIX = "/admin/users/docs/images/";

export function toAdminDocsImageUrl(value: string): string {
  if (value.startsWith(USER_DOCS_IMAGE_PREFIX)) {
    return ADMIN_DOCS_IMAGE_PREFIX + value.slice(USER_DOCS_IMAGE_PREFIX.length);
  }

  try {
    const url = new URL(value);
    if (url.pathname.startsWith(USER_DOCS_IMAGE_PREFIX)) {
      return ADMIN_DOCS_IMAGE_PREFIX
        + url.pathname.slice(USER_DOCS_IMAGE_PREFIX.length)
        + url.search
        + url.hash;
    }
  } catch {
    // 상대경로가 대상 prefix와 일치하지 않으면 원본을 그대로 사용한다.
  }

  return value;
}
