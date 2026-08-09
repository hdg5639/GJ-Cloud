package gj.cloud.vm.application.systemworker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SystemWorkerProvisionRequest(
        @NotBlank String role,
        @Min(100) @Max(999999999) int vmId,
        @Min(1) @Max(64) int cores,
        @Min(512) @Max(262144) int memoryMb,
        @Min(8) @Max(4096) int diskGb,
        @Min(100) int templateVmid,
        @NotBlank String sshPublicKey
) {}
