package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanResult;

public record DeploymentSpecRenderResult(
        ComposeArtifact artifact,
        ComposeRouterPlanResult routerPlan
) {
}
