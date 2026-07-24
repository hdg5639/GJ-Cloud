package gj.cloud.ops.application.deployment.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import gj.cloud.ops.application.deployment.dto.InfraSelection;
import gj.cloud.ops.application.deployment.dto.ServiceCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

// AI-Deployment-Pipeline.md 15절 — 프로바이더 측 프롬프트 캐싱에 기대기 전에 애플리케이션 레벨 캐시를 둔다.
// 같은 저장소/브랜치/서비스 카드/인프라 선택으로 재요청(사용자가 생성 버튼을 다시 누르거나 프론트가 재시도하는
// 경우)하면 저장소 재클론과 AI 호출을 모두 건너뛴다. 키에 커밋 SHA를 포함하지 않는 대신 TTL을 짧게 잡아
// (같은 브랜치에 새 커밋이 푸시된 뒤 TTL 내에 재요청하면 약간 낡은 결과를 받을 수 있음) 이 절충을 상쇄한다.
// 전송/일시적 오류는 캐시하지 않는다 — INVALID_RESPONSE 등 확정 실패만 짧게라도 캐시할 가치가 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGenerationCache {

    private static final String KEY_PREFIX = "ai-gen-cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ops.ai-generation-cache.ttl-seconds:900}")
    private long ttlSeconds;

    public Optional<AiGenerationResult> get(GenerateDeploymentSpecRequest request) {
        String key = buildKey(request);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, AiGenerationResult.class));
        } catch (Exception e) {
            log.warn("AI 생성 캐시 조회 실패(캐시 없이 진행): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(GenerateDeploymentSpecRequest request, AiGenerationResult result) {
        if (result.status() == GenerationStatus.INVALID_RESPONSE) {
            return; // 일시적 오류일 수 있는 실패는 캐시하지 않음
        }
        String key = buildKey(request);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("AI 생성 캐시 저장 실패(무시): {}", e.getMessage());
        }
    }

    private String buildKey(GenerateDeploymentSpecRequest request) {
        StringBuilder raw = new StringBuilder();
        raw.append(request.repoUrl()).append('|').append(request.branch()).append('|');
        for (ServiceCard card : request.services()) {
            raw.append(card.name()).append(':').append(card.runtime()).append(':').append(card.context())
                    .append(':').append(card.containerPort()).append(':').append(card.expose())
                    .append(':').append(card.customSubdomain())
                    .append(':').append(card.buildCommand())
                    .append(':').append(card.startCommand()).append(';');
        }
        List<InfraSelection> infra = request.infrastructure();
        if (infra != null) {
            for (InfraSelection i : infra) {
                raw.append(i.type()).append(':').append(i.version()).append(';');
            }
        }
        raw.append("|network:").append(request.existingNetworkName());
        return KEY_PREFIX + sha256(raw.toString());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(value.hashCode());
        }
    }
}
