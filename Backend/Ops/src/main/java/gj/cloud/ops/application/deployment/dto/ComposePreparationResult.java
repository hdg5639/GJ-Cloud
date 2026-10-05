package gj.cloud.ops.application.deployment.dto;

import java.util.List;

public record ComposePreparationResult(boolean valid, String composeContent, List<EnvironmentFile> environmentFiles,
        List<HealthCheck> healthChecks, List<Service> services, List<String> errors, List<String> warnings) {
    public record Service(String name, List<Integer> containerPorts, List<Integer> hostPorts, List<String> environmentKeys) { }
}
