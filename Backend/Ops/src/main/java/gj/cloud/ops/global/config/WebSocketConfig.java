package gj.cloud.ops.global.config;

import gj.cloud.ops.global.websocket.TerminalHandshakeInterceptor;
import gj.cloud.ops.global.websocket.TerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final TerminalHandshakeInterceptor terminalHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origin 검증은 TerminalHandshakeInterceptor가 직접 수행하므로 여기서는 넓게 허용
        registry.addHandler(terminalWebSocketHandler, "/ws/terminal/{vmId}")
                .addInterceptors(terminalHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
