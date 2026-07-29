package gj.cloud.ops.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RegressionExecutorConfig {

    @Bean(name = "regressionTaskExecutor")
    public TaskExecutor regressionTaskExecutor(
            @Value("${ops.regression-executor.core-pool-size:1}") int corePoolSize,
            @Value("${ops.regression-executor.max-pool-size:2}") int maxPoolSize,
            @Value("${ops.regression-executor.queue-capacity:20}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("regression-worker-");
        executor.initialize();
        return executor;
    }
}
