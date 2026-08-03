package gj.cloud.vm.global.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.vm.global.config.SseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

// SEC-006: EventSource(SSE)는 커스텀 헤더를 못 보내 Authorization 헤더 인증이 불가능하다는 이유로
// 기존에는 JwtAuthenticationWebFilter가 ?token=<원본 액세스 토큰>을 그대로 받아줬는데, 이 폴백이
// SSE 전용이 아니라 전체 공개 API에 걸려 있었고, 노출된 토큰은 만료 전까지 재사용 가능한 완전한
// 베어러 토큰이었음. Ops의 TerminalTicketService와 동일한 패턴 — opaque 랜덤 값 + Redis 원자적
// 조회+폐기(getAndDelete) + 짧은 TTL로 대체.
@Component
@RequiredArgsConstructor
public class SseTicketService {

    private static final String KEY_PREFIX = "sse-ticket:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SseProperties sseProperties;

    public Mono<String> issueTicket(String userId, String email) {
        String ticket = UUID.randomUUID().toString();
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(new SseTicketPayload(userId, email)))
                .flatMap(json -> redisTemplate.opsForValue()
                        .set(KEY_PREFIX + ticket, json, Duration.ofSeconds(sseProperties.getTicketTtlSeconds())))
                .thenReturn(ticket);
    }

    public Mono<SseTicketPayload> consumeTicket(String ticket) {
        return redisTemplate.opsForValue()
                .getAndDelete(KEY_PREFIX + ticket)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, SseTicketPayload.class)));
    }
}
