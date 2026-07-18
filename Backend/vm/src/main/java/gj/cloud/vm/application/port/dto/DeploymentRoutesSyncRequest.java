package gj.cloud.vm.application.port.dto;

import java.util.List;

public record DeploymentRoutesSyncRequest(String deploymentId, List<DeploymentRouteItem> routes) {
}
