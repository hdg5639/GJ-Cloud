package gj.cloud.ops.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// 콘솔용 처리는 WebSocket 세션 단위 경량 처리로 유지 (배포용 TaskExecutor와 격리 — 배포 스레드풀이
// 포화돼도 이 핸들러의 세션들에는 영향 없음). 세션당 블로킹 리더 스레드 1개만 사용해 점유를 최소화함.
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final VmSshSessionFactory sshSessionFactory;
    private final ObjectMapper objectMapper;

    @Value("${ops.terminal-idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    private final Map<String, TerminalBridge> bridges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "terminal-heartbeat");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String vmId = (String) session.getAttributes().get(TerminalHandshakeInterceptor.ATTR_VM_ID);
        String internalIp = (String) session.getAttributes().get(TerminalHandshakeInterceptor.ATTR_INTERNAL_IP);

        try {
            Session sshSession = sshSessionFactory.createSession(vmId, internalIp);

            ChannelShell channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPtyType("xterm-256color");
            channel.setPtySize(80, 24, 640, 480);

            InputStream sshOutput = channel.getInputStream();
            OutputStream sshInput = channel.getOutputStream();
            channel.connect(CONNECT_TIMEOUT_MS);

            TerminalBridge bridge = new TerminalBridge(sshSession, channel, sshInput);
            bridges.put(session.getId(), bridge);

            Thread reader = new Thread(() -> pipeSshToWebSocket(session, sshOutput), "terminal-ssh-reader-" + session.getId());
            reader.setDaemon(true);
            reader.start();
            bridge.readerThread = reader;

            bridge.heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(
                    () -> heartbeatOrCloseIfIdle(session), 30, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("VM SSH 연결 실패: vmId={}, error={}", vmId, e.getMessage());
            closeQuietly(session, CloseStatus.SERVER_ERROR.withReason("VM SSH 연결 실패"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        TerminalBridge bridge = bridges.get(session.getId());
        if (bridge == null) {
            return;
        }
        bridge.touch();

        String payload = message.getPayload();
        if (tryHandleControlMessage(bridge, payload)) {
            return;
        }
        bridge.sshInput.write(payload.getBytes(StandardCharsets.UTF_8));
        bridge.sshInput.flush();
    }

    // 리사이즈 등 제어 메시지는 JSON({"type":"resize","cols":...,"rows":...})으로 구분, 그 외는 일반 키 입력으로 취급
    private boolean tryHandleControlMessage(TerminalBridge bridge, String payload) {
        if (!payload.startsWith("{")) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> control = objectMapper.readValue(payload, Map.class);
            if (!"resize".equals(control.get("type"))) {
                return false;
            }
            int cols = ((Number) control.get("cols")).intValue();
            int rows = ((Number) control.get("rows")).intValue();
            bridge.channel.setPtySize(cols, rows, cols * 8, rows * 16);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session.getId());
    }

    private void pipeSshToWebSocket(WebSocketSession session, InputStream sshOutput) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = sshOutput.read(buf)) != -1) {
                if (!session.isOpen()) {
                    break;
                }
                TerminalBridge bridge = bridges.get(session.getId());
                if (bridge != null) {
                    bridge.touch();
                }
                session.sendMessage(new TextMessage(new String(buf, 0, n, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            log.debug("SSH 출력 스트림 종료: sessionId={}", session.getId());
        } finally {
            closeQuietly(session, CloseStatus.NORMAL);
        }
    }

    private void heartbeatOrCloseIfIdle(WebSocketSession session) {
        TerminalBridge bridge = bridges.get(session.getId());
        if (bridge == null) {
            return;
        }
        long idleMillis = System.currentTimeMillis() - bridge.lastActivityMillis;
        if (idleMillis > TimeUnit.MINUTES.toMillis(idleTimeoutMinutes)) {
            log.info("유휴 콘솔 세션 종료: sessionId={}", session.getId());
            closeQuietly(session, CloseStatus.NORMAL.withReason("idle timeout"));
            return;
        }
        try {
            if (session.isOpen()) {
                session.sendMessage(new PingMessage());
            }
        } catch (IOException e) {
            cleanup(session.getId());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ignored) {
        }
        cleanup(session.getId());
    }

    private void cleanup(String sessionId) {
        TerminalBridge bridge = bridges.remove(sessionId);
        if (bridge == null) {
            return;
        }
        if (bridge.heartbeatFuture != null) {
            bridge.heartbeatFuture.cancel(true);
        }
        if (bridge.readerThread != null) {
            bridge.readerThread.interrupt();
        }
        if (bridge.channel != null && bridge.channel.isConnected()) {
            bridge.channel.disconnect();
        }
        if (bridge.sshSession != null && bridge.sshSession.isConnected()) {
            bridge.sshSession.disconnect();
        }
    }

    private static final class TerminalBridge {
        final Session sshSession;
        final ChannelShell channel;
        final OutputStream sshInput;
        volatile long lastActivityMillis = System.currentTimeMillis();
        Thread readerThread;
        ScheduledFuture<?> heartbeatFuture;

        TerminalBridge(Session sshSession, ChannelShell channel, OutputStream sshInput) {
            this.sshSession = sshSession;
            this.channel = channel;
            this.sshInput = sshInput;
        }

        void touch() {
            lastActivityMillis = System.currentTimeMillis();
        }
    }
}
