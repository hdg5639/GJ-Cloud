package gj.cloud.ops.global.websocket;

import gj.cloud.ops.application.terminal.dto.TicketPayload;
import gj.cloud.ops.application.terminal.service.TerminalTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerminalHandshakeInterceptorTest {
    private final TerminalTicketService tickets = mock(TerminalTicketService.class);
    private final TerminalHandshakeInterceptor interceptor = new TerminalHandshakeInterceptor(tickets);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private static final String ID = "00000000-0000-4000-8000-000000000001";

    private ServerHttpRequest request(String query, String origin) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders(); headers.setOrigin(origin);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("https://api.example.test/ws/terminal/vm?" + query));
        ReflectionTestUtils.setField(interceptor, "allowedOrigins", "https://portal.example.test");
        return request;
    }
    @Test void resumeRequiresFreshConsumedTicketAndBindsOwnerFromTicket() {
        when(tickets.consumeTicket("fresh")).thenReturn(Optional.of(new TicketPayload("alice", "vm", "192.0.2.1"))).thenReturn(Optional.empty());
        var request = request("ticket=fresh&sessionId="+ID, "https://portal.example.test");
        var attributes = new HashMap<String, Object>();
        assertTrue(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes));
        assertEquals("alice", attributes.get(TerminalHandshakeInterceptor.ATTR_USER_ID));
        assertEquals(ID, attributes.get(TerminalHandshakeInterceptor.ATTR_TERMINAL_SESSION_ID));
        assertEquals(true, attributes.get(TerminalHandshakeInterceptor.ATTR_RESUMABLE));
        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
    @Test void wrongOriginAndInvalidSessionNeverConsumeTicket() {
        assertFalse(interceptor.beforeHandshake(request("ticket=fresh&sessionId="+ID, "https://attacker.example.test"), response, mock(WebSocketHandler.class), new HashMap<>()));
        assertFalse(interceptor.beforeHandshake(request("ticket=fresh&sessionId=arbitrary", "https://portal.example.test"), response, mock(WebSocketHandler.class), new HashMap<>()));
        verifyNoInteractions(tickets);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN); verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }
    @Test void ticketForAnotherVmCannotResume() {
        when(tickets.consumeTicket("fresh")).thenReturn(Optional.of(new TicketPayload("alice", "other", "192.0.2.1")));
        assertFalse(interceptor.beforeHandshake(request("ticket=fresh&sessionId="+ID, "https://portal.example.test"), response, mock(WebSocketHandler.class), new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
