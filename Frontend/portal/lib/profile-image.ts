export const MAX_PROFILE_IMAGE_BYTES = 2 * 1024 * 1024;
export const ALLOWED_PROFILE_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];

// User 서비스의 실제 검증 규칙(용량/매직넘버 기반 형식)과 일치시켜, 서버 왕복 없이 바로 피드백을 준다.
export function validateProfileImage(file: File): string | null {
  if (!ALLOWED_PROFILE_IMAGE_TYPES.includes(file.type)) {
    return "jpg, png, webp 형식만 업로드할 수 있어요";
  }
  if (file.size > MAX_PROFILE_IMAGE_BYTES) {
    return "이미지 용량은 2MB를 넘을 수 없어요";
  }
  return null;
}
