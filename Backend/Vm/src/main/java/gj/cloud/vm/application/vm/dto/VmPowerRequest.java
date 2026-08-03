package gj.cloud.vm.application.vm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record VmPowerRequest(
        @Schema(description = "전원 동작", allowableValues = {"START", "STOP", "SUSPEND", "REBOOT"})
        @NotNull VmPowerAction action
) {
    public enum VmPowerAction {
        START, STOP, SUSPEND, REBOOT
    }
}
