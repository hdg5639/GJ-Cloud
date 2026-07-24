package gj.cloud.ops.application.vmclient.dto;

import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;

public record AutomationRoutesRequest(
        String ownerUserId,
        String ownerEmail,
        DeploymentRoutesRequest routes
) {
}
