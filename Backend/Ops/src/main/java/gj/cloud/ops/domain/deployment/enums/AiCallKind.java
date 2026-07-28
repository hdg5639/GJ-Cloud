package gj.cloud.ops.domain.deployment.enums;

// AI 호출 감사 로그 구분 — GENERATION(D-3 스펙 생성) / REVIEW(D.5-1 비차단 검수) /
// PLANNING(Auto Preview AiPagePlanner — 코멘트가 아니라 실제로 적용되는 페이지 구성 제안) /
// PART_SUGGESTION(Auto Preview AiPartAdvisor — 스왑 가능 Block별 Blueprint 파츠 추천, 결정론 검증 후 오버라이드)
public enum AiCallKind {
    GENERATION,
    REVIEW,
    PLANNING,
    PART_SUGGESTION
}
