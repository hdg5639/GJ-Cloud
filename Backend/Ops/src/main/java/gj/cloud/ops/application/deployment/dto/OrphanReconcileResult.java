package gj.cloud.ops.application.deployment.dto;

public record OrphanReconcileResult(
        int scanned,
        int missing,
        int hardDeleted,
        int quarantined,
        int errors
) {}
