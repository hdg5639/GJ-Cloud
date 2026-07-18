package gj.cloud.ops.application.terminal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.terminal.dto.TicketPayload;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TerminalTicketService {

    private static final String KEY_PREFIX = "terminal-ticket:";
    private static final String PERMISSION_TERMINAL_ACCESS = "TERMINAL_ACCESS";

    private final VmServiceClient vmServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ops.terminal-ticket-ttl-seconds}")
    private long ticketTtlSeconds;

    // 권한 확인(TERMINAL_ACCESS) 후 30초 TTL 일회용 티켓 발급. 티켓 자체는 JWT가 아닌 opaque 랜덤 값 — Redis에서만 의미를 가짐
    public String issueTicket(String bearerToken, String userId, UUID vmId) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId.toString());

        if (!context.hasPermission(PERMISSION_TERMINAL_ACCESS)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }

        String ticket = UUID.randomUUID().toString();
        TicketPayload payload = new TicketPayload(userId, vmId.toString(), context.internalIp());
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket, json, Duration.ofSeconds(ticketTtlSeconds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("티켓 직렬화 실패", e);
        }
        return ticket;
    }

    // 원자적 조회+폐기(GETDEL 상당) — 재사용 원천 차단
    public Optional<TicketPayload> consumeTicket(String ticket) {
        String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, TicketPayload.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
