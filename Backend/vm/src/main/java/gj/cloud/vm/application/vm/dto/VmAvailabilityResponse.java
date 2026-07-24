package gj.cloud.vm.application.vm.dto;

public record VmAvailabilityResponse(
        PlanAvailability free,
        PlanAvailability pro
) {
    public record PlanAvailability(int used, int total, boolean isFull) {}
}
