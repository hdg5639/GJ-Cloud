package gj.cloud.ops.application.deployment.ai;

import gj.cloud.ops.application.deployment.spec.DeploymentSpec;

import java.util.List;

public record AiGenerationResult(
        GenerationStatus status,
        DeploymentSpec spec,
        List<UnresolvedField> unresolved,
        List<String> warnings,
        List<String> evidenceRefs
) {
}
