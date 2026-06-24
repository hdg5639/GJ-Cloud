package gj.cloud.vm.application.org.dto;

import gj.cloud.vm.domain.org.enums.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MemberInviteRequest(
        @NotBlank @Email String email,
        MemberRole role
) {}
