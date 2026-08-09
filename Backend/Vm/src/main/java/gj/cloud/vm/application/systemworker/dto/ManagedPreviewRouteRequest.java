package gj.cloud.vm.application.systemworker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ManagedPreviewRouteRequest(
        @Pattern(regexp = "preview-[a-f0-9]{12}") String subdomain,
        @Min(20000) @Max(29999) int port
) {}
