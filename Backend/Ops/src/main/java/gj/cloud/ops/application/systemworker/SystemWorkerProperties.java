package gj.cloud.ops.application.systemworker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "ops.system-worker.auto-preview")
public class SystemWorkerProperties {
    private boolean enabled = true;
    private int preferredVmid = 300;
    private int cores = 4;
    private int memoryMb = 5120;
    private int diskGb = 80;
    private int templateVmid = 9026;
    private String name = "Auto Preview Worker";
}
