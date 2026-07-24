package gj.cloud.vm.application.org.dto;

import gj.cloud.vm.domain.org.enums.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OrgCreateRequest(
        @NotBlank @Size(max = 100) String name,
        List<InviteEntry> invites,
        List<UUID> vmIds
) {
    public record InviteEntry(String email, MemberRole role) {}
}
