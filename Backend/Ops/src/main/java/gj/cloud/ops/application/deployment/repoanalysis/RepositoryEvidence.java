package gj.cloud.ops.application.deployment.repoanalysis;

import java.util.List;

// AI-Deployment-Pipeline.md 3.4절 — 저장소(정확히는 서비스 카드가 가리키는 context 하위 디렉토리) 하나를
// 결정론적으로 분석한 결과. AI 호출 전에 항상 먼저 계산되고, AI 프롬프트에도 raw 파일 대신 이 요약만 전달됨
// (저장소 원문 자체는 저장/로깅하지 않음 — RepositorySnapshotBuilder가 분석 직후 임시 클론을 삭제함).
public record RepositoryEvidence(
        String context,
        DetectedFiles files,
        ManifestData manifest,
        String detectedType,
        String confidence,
        List<String> positiveEvidence,
        List<String> missingEvidence,
        List<String> conflicts,
        String evidenceHash
) {
    public static final String TYPE_STATIC = "STATIC";
    public static final String TYPE_NODE_BUILT_STATIC = "NODE_BUILT_STATIC";
    public static final String TYPE_NODEJS = "NODEJS";
    public static final String TYPE_SPRING_BOOT = "SPRING_BOOT";
    public static final String TYPE_PYTHON = "PYTHON";
    public static final String TYPE_DOCKERFILE = "DOCKERFILE";
    public static final String TYPE_UNKNOWN = "UNKNOWN";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
}
