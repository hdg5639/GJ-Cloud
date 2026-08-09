package gj.cloud.vm.application.systemworker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ManagedPreviewRouteDeleteRequest(
        @Pattern(regexp = "preview-[a-f0-9]{12}") String subdomain,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,128}") String dnsRecordId
) {}
