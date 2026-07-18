package gj.cloud.ops.application.deployment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// D.9 배포 동시성 제어 — Application(=vmId)당 활성 배포 1개만 허용.
// TTL은 Ops가 배포 도중 재시작/크래시돼도 락이 영구히 남지 않도록 하는 안전장치일 뿐,
// 정상 흐름에서는 항상 배포 종료 시 unlock을 명시적으로 호출해야 함.
@Component
@RequiredArgsConstructor
public class DeploymentLockService {

    private static final String KEY_PREFIX = "deployment-lock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${ops.deployment-lock-ttl-seconds:1800}")
    private long lockTtlSeconds;

    // SETNX 방식 — 락 획득 성공 시 true, 이미 다른 배포가 진행 중이면 false
    public boolean tryLock(String appId, String deploymentId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + appId, deploymentId, Duration.ofSeconds(lockTtlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    // 이 deploymentId가 실제로 잡고 있던 락일 때만 해제 (TTL 만료 후 다른 배포가 이미 새로 잡았다면 건드리지 않음)
    public void unlock(String appId, String deploymentId) {
        String current = redisTemplate.opsForValue().get(KEY_PREFIX + appId);
        if (deploymentId.equals(current)) {
            redisTemplate.delete(KEY_PREFIX + appId);
        }
    }

    public boolean isLocked(String appId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + appId));
    }
}
