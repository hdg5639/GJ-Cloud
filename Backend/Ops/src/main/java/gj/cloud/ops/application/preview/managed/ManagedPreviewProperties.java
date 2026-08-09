package gj.cloud.ops.application.preview.managed;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "ops.managed-preview")
public class ManagedPreviewProperties {
    private int portStart = 20000;
    private int portEnd = 20999;
    private int freeTtlHours = 6;
    private int proTtlHours = 24;
}
