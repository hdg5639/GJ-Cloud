package gj.cloud.ops.application.preview.planning.patch;

import java.util.List;

public record PagePlanPatchApplyResult(
        PlanPatchState state,
        List<String> decisions,
        List<String> errors
) {
    public boolean succeeded() {
        return errors == null || errors.isEmpty();
    }
}
