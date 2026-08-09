package gj.cloud.vm.application.systemworker.dto;

public record SystemWorkerVmResponse(
        boolean exists,
        int vmId,
        String node,
        String internalIp,
        String powerState,
        int cores,
        int memoryMb,
        int diskGb
) {}
