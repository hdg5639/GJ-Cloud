package gj.cloud.vm.application.vm.dto;

import java.util.List;

public record AdminVmPageResponse(
        List<VmResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean empty
) {
}
