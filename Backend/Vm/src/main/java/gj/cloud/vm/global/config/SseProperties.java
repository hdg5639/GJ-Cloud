package gj.cloud.vm.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "sse")
public class SseProperties {
    private long ticketTtlSeconds;
}
