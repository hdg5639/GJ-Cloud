package gj.cloud.ops.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {
    private final TerminalSessionRegistry terminalSessions;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        terminalSessions.attach(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        terminalSessions.receive(session, message.getPayload());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        terminalSessions.pong(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        terminalSessions.detach(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        terminalSessions.detach(session);
    }
}
