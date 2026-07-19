package gj.cloud.ops.application.deployment.ai;

// AI-Deployment-Pipeline.md 7절 — 근거가 부족한데도 완전한 스펙을 억지로 만들어내지 않기 위한 명시적 상태.
public enum GenerationStatus {
    READY,
    NEEDS_INPUT,
    UNSUPPORTED,
    CONFLICT,
    INVALID_RESPONSE
}
