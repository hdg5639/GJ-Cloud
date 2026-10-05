package gj.cloud.ops.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TerminalSessionRegistryTest {
    private final VmSshSessionFactory factory = mock(VmSshSessionFactory.class);
    private final MutableClock clock = new MutableClock();
    private final List<Shell> shells = new CopyOnWriteArrayList<>();
    private TerminalSessionRegistry registry;
    private static final String SESSION = "00000000-0000-4000-8000-000000000001";

    @BeforeEach void setup() throws Exception {
        when(factory.createSession(anyString(), anyString())).thenAnswer(call -> {
            Shell shell = new Shell(); shells.add(shell); return shell.ssh;
        });
        registry = new TerminalSessionRegistry(factory, new ObjectMapper(), clock);
    }
    @AfterEach void cleanup() { registry.shutdown(); }

    @Test void detachAndReturnKeepsShellWorkingDirectoryAndDetachedOutput() throws Exception {
        Viewer first = viewer("one", "alice", "vm", SESSION, true);
        registry.attach(first.socket);
        registry.receive(first.socket, "cd /srv/app\n");
        shells.get(0).output.emit("before\r\n".getBytes(StandardCharsets.UTF_8));
        await(() -> first.output().contains("before"));
        registry.detach(first.socket);
        shells.get(0).output.emit("detached 로그\r\n".getBytes(StandardCharsets.UTF_8));
        // A barrier confirms the asynchronous reader consumed the detached output.
        await(() -> shells.get(0).output.consumed >= 2);
        Viewer second = viewer("two", "alice", "vm", SESSION, true);
        registry.attach(second.socket);
        await(() -> second.output().contains("detached 로그"));
        assertTrue(second.controls().contains("\"resumed\":true"));
        assertTrue(second.output().contains("before"));
        assertEquals("cd /srv/app\n", shells.get(0).input.toString(StandardCharsets.UTF_8));
        verify(factory, times(1)).createSession("vm", "192.0.2.1");
        verify(shells.get(0).channel, never()).disconnect();
    }

    @Test void unicodeIsForwardedAsOriginalBytesEvenWhenSplitBetweenReads() throws Exception {
        Viewer viewer = viewer("one", "alice", "vm", SESSION, true); registry.attach(viewer.socket);
        byte[] bytes = "한글🙂".getBytes(StandardCharsets.UTF_8);
        shells.get(0).output.emit(Arrays.copyOfRange(bytes, 0, 2));
        shells.get(0).output.emit(Arrays.copyOfRange(bytes, 2, bytes.length));
        await(() -> viewer.bytes().length == bytes.length);
        assertArrayEquals(bytes, viewer.bytes());
    }

    @Test void sameSessionIdCannotReadAnotherUserOrVmOutput() throws Exception {
        Viewer alice = viewer("one", "alice", "vm", SESSION, true); registry.attach(alice.socket);
        shells.get(0).output.emit("private-output".getBytes(StandardCharsets.UTF_8));
        await(() -> alice.output().contains("private-output"));
        registry.detach(alice.socket);
        Viewer bob = viewer("two", "bob", "vm", SESSION, true); registry.attach(bob.socket);
        Viewer otherVm = viewer("three", "alice", "other", SESSION, true); registry.attach(otherVm.socket);
        assertFalse(bob.output().contains("private-output"));
        assertFalse(otherVm.output().contains("private-output"));
        assertEquals(3, shells.size());
    }

    @Test void replacementFencesOldViewerInputAndCloseCallback() throws Exception {
        Viewer first = viewer("one", "alice", "vm", SESSION, true); registry.attach(first.socket);
        Viewer second = viewer("two", "alice", "vm", SESSION, true); registry.attach(second.socket);
        registry.receive(first.socket, "stale"); registry.detach(first.socket);
        registry.receive(second.socket, "current");
        assertEquals("current", shells.get(0).input.toString(StandardCharsets.UTF_8));
        assertTrue(first.reasons.contains("replaced"));
        assertTrue(second.open.get());
    }

    @Test void heartbeatDoesNotWriteToShellOrExtendIdleTimeout() throws Exception {
        Viewer viewer = viewer("one", "alice", "vm", SESSION, true); registry.attach(viewer.socket);
        for (int i = 0; i < 30; i++) {
            clock.advance(Duration.ofMinutes(1));
            registry.receive(viewer.socket, "{\"type\":\"heartbeat\"}"); registry.heartbeatAndExpire();
        }
        assertEquals(0, shells.get(0).input.size());
        assertTrue(viewer.controls().contains("heartbeat"));
        assertTrue(viewer.reasons.contains("idle timeout"));
        verify(shells.get(0).channel).disconnect();
    }

    @Test void missingHeartbeatDetachesTransportAndAllowsResume() throws Exception {
        Viewer viewer = viewer("one", "alice", "vm", SESSION, true); registry.attach(viewer.socket);
        clock.advance(Duration.ofSeconds(71)); registry.heartbeatAndExpire();
        assertTrue(viewer.reasons.contains("heartbeat timeout"));
        Viewer returned = viewer("two", "alice", "vm", SESSION, true); registry.attach(returned.socket);
        assertTrue(returned.controls().contains("\"resumed\":true"));
        assertEquals(1, shells.size());
    }

    @Test void detachedExpiryIsEnforcedAtReattachWithoutWaitingForSweep() throws Exception {
        Viewer first = viewer("one", "alice", "vm", SESSION, true); registry.attach(first.socket); registry.detach(first.socket);
        clock.advance(Duration.ofMinutes(31));
        Viewer returned = viewer("two", "alice", "vm", SESSION, true); registry.attach(returned.socket);
        assertEquals(2, shells.size());
        assertTrue(returned.controls().contains("\"resumed\":false"));
        verify(shells.get(0).channel).disconnect();
    }

    @Test void replayMemoryIsBoundedAndReportsTruncation() throws Exception {
        ReflectionTestUtils.setField(registry, "replayMaxBytes", 4096);
        Viewer first = viewer("one", "alice", "vm", SESSION, true); registry.attach(first.socket);
        shells.get(0).output.emit(new byte[4096]); shells.get(0).output.emit(new byte[4096]);
        await(() -> first.bytes().length == 8192); registry.detach(first.socket);
        Viewer returned = viewer("two", "alice", "vm", SESSION, true); registry.attach(returned.socket);
        assertEquals(4096, returned.bytes().length);
        assertTrue(returned.controls().contains("\"truncated\":true"));
    }

    @Test void sessionCapacityRejectsNewShellButAllowsExistingResumeAndReleasesAfterExpiry() throws Exception {
        ReflectionTestUtils.setField(registry, "maxSessionsPerUser", 1);
        Viewer first = viewer("one", "alice", "vm", SESSION, true); registry.attach(first.socket); registry.detach(first.socket);
        Viewer rejected = viewer("two", "alice", "vm", "other-session", true); registry.attach(rejected.socket);
        assertTrue(rejected.reasons.contains("terminal session limit"));
        Viewer returned = viewer("three", "alice", "vm", SESSION, true); registry.attach(returned.socket);
        assertTrue(returned.open.get()); registry.detach(returned.socket);
        clock.advance(Duration.ofMinutes(31)); registry.heartbeatAndExpire();
        Viewer fresh = viewer("four", "alice", "vm", "fresh", true); registry.attach(fresh.socket);
        assertTrue(fresh.open.get()); assertEquals(2, shells.size());
    }

    @Test void legacyClientStillReceivesTextAndClosesSshOnDisconnect() throws Exception {
        Viewer legacy = viewer("one", "alice", "vm", SESSION, false); registry.attach(legacy.socket);
        shells.get(0).output.emit("legacy".getBytes(StandardCharsets.UTF_8));
        await(() -> legacy.controls().contains("legacy")); registry.detach(legacy.socket);
        verify(shells.get(0).channel).disconnect();
    }

    @Test void sshCreationFailureReleasesCapacityForRetry() throws Exception {
        ReflectionTestUtils.setField(registry, "maxSessions", 1);
        doThrow(new IllegalStateException("unavailable"))
                .doAnswer(call -> { Shell shell = new Shell(); shells.add(shell); return shell.ssh; })
                .when(factory).createSession(anyString(), anyString());
        Viewer failed = viewer("one", "alice", "vm", SESSION, true); registry.attach(failed.socket);
        assertTrue(failed.reasons.contains("terminal connection failed"));
        Viewer returned = viewer("two", "alice", "vm", SESSION, true); registry.attach(returned.socket);
        assertTrue(returned.open.get()); assertEquals(1, shells.size());
    }

    @Test void shellExitEndsSessionAndLaterEntryCreatesFreshShell() throws Exception {
        Viewer first = viewer("one", "alice", "vm", SESSION, true); registry.attach(first.socket);
        shells.get(0).output.emit(new byte[0]); await(() -> first.reasons.contains("shell ended"));
        Viewer returned = viewer("two", "alice", "vm", SESSION, true); registry.attach(returned.socket);
        assertEquals(2, shells.size()); assertTrue(returned.controls().contains("\"resumed\":false"));
    }

    @Test void shutdownDisconnectsDetachedShellAndClearsResources() throws Exception {
        Viewer viewer = viewer("one", "alice", "vm", SESSION, true); registry.attach(viewer.socket); registry.detach(viewer.socket);
        registry.shutdown();
        verify(shells.get(0).channel).disconnect(); verify(shells.get(0).ssh).disconnect();
        assertEquals(0, ReflectionTestUtils.getField(registry, "sessionCount"));
    }

    private Viewer viewer(String id, String user, String vm, String session, boolean resumable) throws Exception {
        Viewer viewer = new Viewer();
        when(viewer.socket.getId()).thenReturn(id);
        when(viewer.socket.isOpen()).thenAnswer(call -> viewer.open.get());
        when(viewer.socket.getAttributes()).thenReturn(Map.of(
                TerminalHandshakeInterceptor.ATTR_USER_ID, user, TerminalHandshakeInterceptor.ATTR_VM_ID, vm,
                TerminalHandshakeInterceptor.ATTR_INTERNAL_IP, "192.0.2.1",
                TerminalHandshakeInterceptor.ATTR_TERMINAL_SESSION_ID, session,
                TerminalHandshakeInterceptor.ATTR_RESUMABLE, resumable));
        doAnswer(call -> { viewer.messages.add(call.getArgument(0)); return null; }).when(viewer.socket).sendMessage(any());
        doAnswer(call -> { viewer.open.set(false); viewer.reasons.add(((CloseStatus) call.getArgument(0)).getReason()); return null; }).when(viewer.socket).close(any());
        return viewer;
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "asynchronous SSH output was not delivered");
    }
    private static final class Viewer {
        final WebSocketSession socket = mock(WebSocketSession.class);
        final List<WebSocketMessage<?>> messages = new CopyOnWriteArrayList<>();
        final List<String> reasons = new CopyOnWriteArrayList<>();
        final AtomicBoolean open = new AtomicBoolean(true);
        String controls() { return messages.stream().filter(m -> m instanceof TextMessage).map(m -> (String)m.getPayload()).reduce("", String::concat); }
        byte[] bytes() {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            messages.stream().filter(m -> m instanceof BinaryMessage).forEach(m -> {
                var buffer = ((BinaryMessage)m).getPayload().duplicate(); byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); result.writeBytes(bytes);
            }); return result.toByteArray();
        }
        String output() { return new String(bytes(), StandardCharsets.UTF_8); }
    }
    private static final class Shell {
        final Session ssh = mock(Session.class); final ChannelShell channel = mock(ChannelShell.class);
        final ByteArrayOutputStream input = new ByteArrayOutputStream(); final QueueInput output = new QueueInput();
        Shell() throws Exception {
            when(ssh.openChannel("shell")).thenReturn(channel); when(channel.getInputStream()).thenReturn(output);
            when(channel.getOutputStream()).thenReturn(input);
            doAnswer(call -> { output.emit(new byte[0]); return null; }).when(channel).disconnect();
        }
    }
    private static final class QueueInput extends InputStream {
        final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(); volatile int consumed;
        void emit(byte[] bytes) { queue.add(bytes); }
        @Override public int read() { throw new UnsupportedOperationException(); }
        @Override public int read(byte[] buffer) throws IOException {
            try { byte[] bytes = queue.take(); if (bytes.length == 0) return -1;
                System.arraycopy(bytes, 0, buffer, 0, bytes.length); consumed++; return bytes.length;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        }
    }
    private static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-01T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
