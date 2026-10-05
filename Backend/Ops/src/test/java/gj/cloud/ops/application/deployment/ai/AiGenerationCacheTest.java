package gj.cloud.ops.application.deployment.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiGenerationCacheTest {

    @Test
    void changingEitherModelDoesNotReusePreviousGenerationCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AiGenerationCache cache = new AiGenerationCache(redis, new ObjectMapper());
        var request = new GenerateDeploymentSpecRequest(
                "https://github.com/example/app.git", "main", null,
                List.of(), List.of(), null, null, null);

        ReflectionTestUtils.setField(cache, "standardModel", "previous-standard");
        ReflectionTestUtils.setField(cache, "escalatedModel", "previous-escalated");
        cache.get(request);
        ReflectionTestUtils.setField(cache, "standardModel", "gpt-6.1-sol");
        cache.get(request);
        ReflectionTestUtils.setField(cache, "escalatedModel", "gpt-6.1-sol");
        cache.get(request);
        cache.get(request);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, times(4)).get(keys.capture());
        assertThat(keys.getAllValues().subList(0, 3)).doesNotHaveDuplicates();
        assertThat(keys.getAllValues().get(3)).isEqualTo(keys.getAllValues().get(2));
    }
}
