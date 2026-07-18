package gj.cloud.ops.global.websocket;

import gj.cloud.ops.application.terminal.dto.TicketPayload;
import gj.cloud.ops.application.terminal.service.TerminalTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

// 일회용 티켓(Redis GETDEL) + Origin 검증. 여기서 통과해야만 실제 WebSocket 업그레이드가 진행됨
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_VM_ID = "vmId";
    public static final String ATTR_INTERNAL_IP = "internalIp";

    private final TerminalTicketService terminalTicketService;

    @Value("${ops.allowed-origin:}")
    private String allowedOrigin;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String origin = request.getHeaders().getOrigin();
        if (StringUtils.hasText(allowedOrigin) && origin != null && !allowedOrigin.equals(origin)) {
            log.warn("터미널 WebSocket Origin 불일치: origin={}", origin);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        String vmIdFromPath = extractVmId(request.getURI().getPath());
        String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
        if (!StringUtils.hasText(ticket)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<TicketPayload> payload = terminalTicketService.consumeTicket(ticket);
        if (payload.isEmpty() || !payload.get().vmId().equals(vmIdFromPath)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_USER_ID, payload.get().userId());
        attributes.put(ATTR_VM_ID, payload.get().vmId());
        attributes.put(ATTR_INTERNAL_IP, payload.get().internalIp());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractVmId(String path) {
        String[] segments = path.split("/");
        return segments[segments.length - 1];
    }
}
