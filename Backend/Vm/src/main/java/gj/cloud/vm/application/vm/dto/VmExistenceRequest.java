package gj.cloud.vm.application.vm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record VmExistenceRequest(
        @NotNull @Size(min = 1, max = 500) List<@NotNull UUID> vmIds
) {
}
