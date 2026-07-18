package gj.cloud.ops.domain.deployment.entity;

import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// D-3 생성 / D.5-1 검수 AI 호출 감사 로그 — 원문 프롬프트/응답은 저장하지 않고
// 종류·모델명·토큰 수·재교정 횟수·성공 여부만 기록
@Entity
@Table(name = "ai_spec_generation_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiSpecGenerationLogEntity {

    @Id
    private String id;

    @Column(name = "vm_id", nullable = false)
    private String vmId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiCallKind kind;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "correction_attempt_count", nullable = false)
    private int correctionAttemptCount;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AiSpecGenerationLogEntity create(String vmId, AiCallKind kind, String model, long inputTokens,
                                                     long outputTokens, int correctionAttemptCount, boolean succeeded) {
        return AiSpecGenerationLogEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .kind(kind)
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .correctionAttemptCount(correctionAttemptCount)
                .succeeded(succeeded)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
