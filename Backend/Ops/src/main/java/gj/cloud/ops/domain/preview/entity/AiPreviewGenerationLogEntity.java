package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// AiSpecGenerationLogEntity(D-3/D.5-1)와 같은 목적의 감사 로그를 Auto Preview용으로 별도 둔 것 —
// Preview 분석/검수는 특정 VM에 종속되지 않으므로(vmId가 없음) 기존 테이블을 그대로 재사용하지 않고
// requester_user_id 기준으로 감사한다. 원문 프롬프트/응답은 저장하지 않고 모델명·토큰 수·성공 여부만 기록.
@Entity
@Table(name = "ai_preview_generation_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiPreviewGenerationLogEntity {

    @Id
    private String id;

    @Column(name = "requester_user_id", nullable = false)
    private String requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiCallKind kind;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AiPreviewGenerationLogEntity create(String requesterUserId, AiCallKind kind, String model,
                                                        long inputTokens, long outputTokens, boolean succeeded) {
        return AiPreviewGenerationLogEntity.builder()
                .id(UUID.randomUUID().toString())
                .requesterUserId(requesterUserId)
                .kind(kind)
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .succeeded(succeeded)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
