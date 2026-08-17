package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.domain.deployment.repository.DeploymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentEventPublisherTest {

    @Mock
    private DeploymentEventRepository repository;

    private DeploymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DeploymentEventPublisher(repository, new ObjectMapper());
    }

    @Test
    void subscribeUsesBoundedStreamTimeout() {
        when(repository.findAllByDeploymentIdAndSequenceGreaterThanOrderBySequenceAsc("deployment-1", 0L))
                .thenReturn(List.of());

        SseEmitter emitter = publisher.subscribe("deployment-1", 0L);

        assertEquals(DeploymentEventPublisher.STREAM_TIMEOUT_MS, emitter.getTimeout());
        publisher.complete("deployment-1");
    }

    @Test
    void heartbeatWritesCommentFrameToActiveEmitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        DeploymentEventPublisher publisherSpy = spy(publisher);
        doReturn(emitter).when(publisherSpy).createEmitter();
        when(repository.findAllByDeploymentIdAndSequenceGreaterThanOrderBySequenceAsc("deployment-1", 0L))
                .thenReturn(List.of());
        assertSame(emitter, publisherSpy.subscribe("deployment-1", 0L));

        publisherSpy.sendHeartbeats();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void failedHeartbeatRemovesEmitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        DeploymentEventPublisher publisherSpy = spy(publisher);
        doReturn(emitter).when(publisherSpy).createEmitter();
        when(repository.findAllByDeploymentIdAndSequenceGreaterThanOrderBySequenceAsc("deployment-1", 0L))
                .thenReturn(List.of());
        publisherSpy.subscribe("deployment-1", 0L);
        doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        publisherSpy.sendHeartbeats();
        publisherSpy.sendHeartbeats();

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void replayFailureDoesNotLeaveEmitterRegistered() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        DeploymentEventPublisher publisherSpy = spy(publisher);
        doReturn(emitter).when(publisherSpy).createEmitter();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db unavailable");
        when(repository.findAllByDeploymentIdAndSequenceGreaterThanOrderBySequenceAsc("deployment-1", 0L))
                .thenThrow(failure);

        assertThrows(DataAccessResourceFailureException.class,
                () -> publisherSpy.subscribe("deployment-1", 0L));
        publisherSpy.sendHeartbeats();

        verify(emitter).completeWithError(failure);
        verify(emitter, times(0)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
