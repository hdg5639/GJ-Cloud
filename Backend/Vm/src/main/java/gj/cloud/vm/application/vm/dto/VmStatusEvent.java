package gj.cloud.vm.application.vm.dto;

import java.time.LocalDateTime;

public record VmStatusEvent(
        String vmId,
        String status,
        String internalIp,
        String errorMessage,
        LocalDateTime timestamp
) {}
