package gj.cloud.vm.application.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrgUpdateRequest(@NotBlank @Size(max = 100) String name) {}
