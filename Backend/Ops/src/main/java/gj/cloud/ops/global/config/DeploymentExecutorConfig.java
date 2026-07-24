package gj.cloud.ops.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 배포 작업(git/build/docker exec)은 콘솔 WebSocket 세션과 완전히 격리된 이 전용 풀에서만 실행함.
// 배포 스레드풀이 포화돼도 콘솔 세션 처리에는 영향이 없어야 한다는 원칙(gamjabox 기획 1.4절) 그대로 반영.
@Configuration
public class DeploymentExecutorConfig {

    @Bean(name = "deploymentTaskExecutor")
    public TaskExecutor deploymentTaskExecutor(
            @Value("${ops.deployment-executor.core-pool-size:2}") int corePoolSize,
            @Value("${ops.deployment-executor.max-pool-size:4}") int maxPoolSize,
            @Value("${ops.deployment-executor.queue-capacity:20}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("deploy-worker-");
        executor.initialize();
        return executor;
    }
}
