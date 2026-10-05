package gj.cloud.ops.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** SSH/PTY lives independently of its current browser transport. Output stays in bounded process memory. */
@Slf4j
@Component
public class TerminalSessionRegistry {
    private final VmSshSessionFactory sshSessionFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<SessionKey, TerminalSession> terminals = new ConcurrentHashMap<>();
    private final Map<String, Attachment> attachments = new ConcurrentHashMap<>();
    private final Object capacityLock = new Object();
    private final Map<String, Integer> userCounts = new HashMap<>();
    private int sessionCount;
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "terminal-session-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${ops.terminal-idle-timeout-minutes:30}")
    private long idleTimeoutMinutes = 30;
    @Value("${ops.terminal-session-retention-minutes:30}")
    private long retentionMinutes = 30;
    @Value("${ops.terminal-replay-max-bytes:262144}")
    private int replayMaxBytes = 262144;
    @Value("${ops.terminal-max-sessions:128}")
    private int maxSessions = 128;
    @Value("${ops.terminal-max-sessions-per-user:4}")
    private int maxSessionsPerUser = 4;

    @Autowired
    public TerminalSessionRegistry(VmSshSessionFactory factory, ObjectMapper mapper) {
        this(factory, mapper, Clock.systemUTC());
    }

    TerminalSessionRegistry(VmSshSessionFactory factory, ObjectMapper mapper, Clock clock) {
        this.sshSessionFactory = factory;
        this.objectMapper = mapper;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        maintenance.scheduleAtFixedRate(this::heartbeatAndExpire, 20, 20, TimeUnit.SECONDS);
    }

    public void attach(WebSocketSession socket) {
        Map<String, Object> attributes = socket.getAttributes();
        SessionKey key = new SessionKey((String) attributes.get(TerminalHandshakeInterceptor.ATTR_USER_ID),
                (String) attributes.get(TerminalHandshakeInterceptor.ATTR_VM_ID),
                (String) attributes.get(TerminalHandshakeInterceptor.ATTR_TERMINAL_SESSION_ID));
        String ip = (String) attributes.get(TerminalHandshakeInterceptor.ATTR_INTERNAL_IP);
        boolean resumable = Boolean.TRUE.equals(attributes.get(TerminalHandshakeInterceptor.ATTR_RESUMABLE));
        try {
            TerminalSession terminal = terminals.compute(key, (ignored, existing) -> {
                if (existing != null) {
                    synchronized (existing) {
                        boolean expired = existing.viewer == null && clock.millis() - existing.detachedAtMillis
                                >= TimeUnit.MINUTES.toMillis(retentionMinutes);
                        if (!existing.ended.get() && existing.internalIp.equals(ip) && !expired) return existing;
                    }
                }
                if (existing != null) terminate(existing, "VM changed", false);
                return create(key, ip);
            });
            startReader(terminal);
            synchronized (terminal) {
                if (terminal.ended.get() || !socket.isOpen()) {
                    closeSocket(socket, CloseStatus.NORMAL.withReason("shell ended"));
                    return;
                }
                boolean resumed = terminal.everAttached;
                if (terminal.viewer != null) detachLocked(terminal.viewer, "replaced");
                Attachment viewer = new Attachment(socket, terminal, resumable, clock.millis());
                terminal.viewer = viewer;
                terminal.everAttached = true;
                terminal.detachedAtMillis = 0;
                attachments.put(socket.getId(), viewer);
                if (resumable) {
                    sendControl(viewer, Map.of("type", "attached", "sessionId", key.sessionId(),
                            "resumed", resumed, "truncated", terminal.truncated));
                    for (byte[] output : terminal.output) viewer.socket.sendMessage(new BinaryMessage(output));
                    sendControl(viewer, Map.of("type", "ready"));
                }
            }
        } catch (SessionLimitException e) {
            closeSocket(socket, new CloseStatus(1013, "terminal session limit"));
        } catch (Exception e) {
            detach(socket);
            closeSocket(socket, CloseStatus.SERVER_ERROR.withReason("terminal connection failed"));
            log.warn("터미널 연결 실패: vmId={}, error={}", key.vmId(), e.getClass().getSimpleName());
        }
    }

    private TerminalSession create(SessionKey key, String ip) {
        reserve(key.userId());
        Session ssh = null;
        ChannelShell channel = null;
        try {
            ssh = sshSessionFactory.createSession(key.vmId(), ip);
            channel = (ChannelShell) ssh.openChannel("shell");
            channel.setPtyType("xterm-256color");
            channel.setPtySize(80, 24, 640, 480);
            InputStream output = channel.getInputStream();
            OutputStream input = channel.getOutputStream();
            channel.connect(10_000);
            return new TerminalSession(key, ip, ssh, channel, input, output, clock.millis());
        } catch (Exception e) {
            if (channel != null) channel.disconnect();
            if (ssh != null) ssh.disconnect();
            release(key.userId());
            throw new IllegalStateException("terminal connection failed", e);
        }
    }

    private void reserve(String userId) {
        synchronized (capacityLock) {
            int userCount = userCounts.getOrDefault(userId, 0);
            if (sessionCount >= maxSessions || userCount >= maxSessionsPerUser) throw new SessionLimitException();
            sessionCount++;
            userCounts.put(userId, userCount + 1);
        }
    }

    private void release(String userId) {
        synchronized (capacityLock) {
            sessionCount--;
            userCounts.computeIfPresent(userId, (ignored, count) -> count > 1 ? count - 1 : null);
        }
    }

    private void startReader(TerminalSession terminal) {
        if (!terminal.readerStarted.compareAndSet(false, true)) return;
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int count;
                while ((count = terminal.sshOutput.read(buffer)) != -1) {
                    if (terminal.ended.get()) break;
                    publish(terminal, Arrays.copyOf(buffer, count));
                }
            } catch (Exception e) {
                log.debug("터미널 SSH 출력 종료: vmId={}", terminal.key.vmId());
            } finally {
                terminate(terminal, "shell ended", true);
            }
        }, "terminal-ssh-reader");
        reader.setDaemon(true);
        terminal.reader = reader;
        reader.start();
    }

    private void publish(TerminalSession terminal, byte[] output) {
        synchronized (terminal) {
            if (terminal.ended.get()) return;
            terminal.lastActivityMillis = clock.millis();
            terminal.output.addLast(output);
            terminal.outputBytes += output.length;
            while (terminal.outputBytes > Math.max(4096, replayMaxBytes)) {
                terminal.outputBytes -= terminal.output.removeFirst().length;
                terminal.truncated = true;
            }
            Attachment viewer = terminal.viewer;
            if (viewer == null) return;
            try {
                if (viewer.resumable) viewer.socket.sendMessage(new BinaryMessage(output));
                else viewer.socket.sendMessage(new TextMessage(new String(output, StandardCharsets.UTF_8)));
            } catch (Exception e) {
                detachLocked(viewer, "transport closed");
            }
        }
    }

    public void receive(WebSocketSession socket, String payload) throws Exception {
        Attachment viewer = attachments.get(socket.getId());
        if (viewer == null) return;
        TerminalSession terminal = viewer.terminal;
        synchronized (terminal) {
            if (terminal.ended.get() || terminal.viewer != viewer) return;
            viewer.lastSeenMillis = clock.millis();
            Map<?, ?> control = null;
            if (payload.startsWith("{")) {
                try { control = objectMapper.readValue(payload, Map.class); } catch (Exception ignored) { }
            }
            if (control != null && "heartbeat".equals(control.get("type"))) {
                sendControl(viewer, Map.of("type", "heartbeat"));
                return;
            }
            if (control != null && "resize".equals(control.get("type"))) {
                Object columns = control.get("cols"), lines = control.get("rows");
                if (columns instanceof Number cols && lines instanceof Number rows
                        && cols.intValue() > 0 && cols.intValue() <= 1000 && rows.intValue() > 0 && rows.intValue() <= 1000) {
                    terminal.channel.setPtySize(cols.intValue(), rows.intValue(), cols.intValue() * 8, rows.intValue() * 16);
                }
                return;
            }
            terminal.lastActivityMillis = clock.millis();
            terminal.sshInput.write(payload.getBytes(StandardCharsets.UTF_8));
            terminal.sshInput.flush();
        }
    }

    public void pong(WebSocketSession socket) {
        Attachment viewer = attachments.get(socket.getId());
        if (viewer != null) viewer.lastSeenMillis = clock.millis();
    }

    public void detach(WebSocketSession socket) {
        Attachment viewer = attachments.get(socket.getId());
        if (viewer == null) return;
        synchronized (viewer.terminal) {
            if (viewer.terminal.viewer == viewer) detachLocked(viewer, "detached");
        }
        if (!viewer.resumable) terminate(viewer.terminal, "closed", true);
    }

    private void detachLocked(Attachment viewer, String reason) {
        attachments.remove(viewer.socket.getId(), viewer);
        if (viewer.terminal.viewer == viewer) {
            viewer.terminal.viewer = null;
            viewer.terminal.detachedAtMillis = clock.millis();
        }
        closeSocket(viewer.socket, CloseStatus.NORMAL.withReason(reason));
    }

    void heartbeatAndExpire() {
        long now = clock.millis();
        terminals.values().forEach(terminal -> {
            String expiryReason = null;
            synchronized (terminal) {
                if (terminal.ended.get()) return;
                if (terminal.viewer == null) {
                    if (now - terminal.detachedAtMillis >= TimeUnit.MINUTES.toMillis(retentionMinutes)) {
                        expiryReason = "session expired";
                    }
                } else if (now - terminal.lastActivityMillis >= TimeUnit.MINUTES.toMillis(idleTimeoutMinutes)) {
                    expiryReason = "idle timeout";
                } else if (now - terminal.viewer.lastSeenMillis >= 70_000) {
                    detachLocked(terminal.viewer, "heartbeat timeout");
                } else {
                    try { terminal.viewer.socket.sendMessage(new PingMessage()); }
                    catch (Exception e) { detachLocked(terminal.viewer, "transport closed"); }
                }
            }
            // Never remove from the registry while holding a PTY lock: attach computes by key first.
            if (expiryReason != null) {
                String reason = expiryReason;
                terminals.computeIfPresent(terminal.key, (key, current) -> {
                    if (current != terminal) return current;
                    synchronized (current) {
                        boolean expired = current.viewer == null
                                ? now - current.detachedAtMillis >= TimeUnit.MINUTES.toMillis(retentionMinutes)
                                : now - current.lastActivityMillis >= TimeUnit.MINUTES.toMillis(idleTimeoutMinutes);
                        if (!expired) return current;
                        terminate(current, reason, false);
                        return null;
                    }
                });
            }
        });
    }

    private void sendControl(Attachment viewer, Map<String, ?> control) throws Exception {
        viewer.socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(control)));
    }

    private void terminate(TerminalSession terminal, String reason, boolean remove) {
        if (!terminal.ended.compareAndSet(false, true)) return;
        if (remove) terminals.remove(terminal.key, terminal);
        synchronized (terminal) {
            if (terminal.viewer != null) detachLocked(terminal.viewer, reason);
            terminal.output.clear();
            terminal.outputBytes = 0;
        }
        terminal.channel.disconnect();
        terminal.sshSession.disconnect();
        if (terminal.reader != null) terminal.reader.interrupt();
        release(terminal.key.userId());
    }

    private void closeSocket(WebSocketSession socket, CloseStatus status) {
        try { if (socket.isOpen()) socket.close(status); } catch (Exception ignored) { }
    }

    @PreDestroy
    void shutdown() {
        maintenance.shutdownNow();
        terminals.values().forEach(terminal -> terminate(terminal, "server shutdown", true));
    }

    private record SessionKey(String userId, String vmId, String sessionId) { }
    private static final class SessionLimitException extends RuntimeException { }
    private static final class Attachment {
        final WebSocketSession socket;
        final TerminalSession terminal;
        final boolean resumable;
        volatile long lastSeenMillis;
        Attachment(WebSocketSession socket, TerminalSession terminal, boolean resumable, long now) {
            this.socket = new ConcurrentWebSocketSessionDecorator(socket, 10_000, 524_288);
            this.terminal = terminal;
            this.resumable = resumable;
            this.lastSeenMillis = now;
        }
    }
    private static final class TerminalSession {
        final SessionKey key;
        final String internalIp;
        final Session sshSession;
        final ChannelShell channel;
        final OutputStream sshInput;
        final InputStream sshOutput;
        final AtomicBoolean ended = new AtomicBoolean();
        final AtomicBoolean readerStarted = new AtomicBoolean();
        final ArrayDeque<byte[]> output = new ArrayDeque<>();
        int outputBytes;
        boolean truncated;
        boolean everAttached;
        long lastActivityMillis;
        long detachedAtMillis;
        Attachment viewer;
        Thread reader;
        TerminalSession(SessionKey key, String internalIp, Session sshSession, ChannelShell channel,
                        OutputStream input, InputStream output, long now) {
            this.key = key;
            this.internalIp = internalIp;
            this.sshSession = sshSession;
            this.channel = channel;
            this.sshInput = input;
            this.sshOutput = output;
            this.lastActivityMillis = now;
            this.detachedAtMillis = now;
        }
    }
}
