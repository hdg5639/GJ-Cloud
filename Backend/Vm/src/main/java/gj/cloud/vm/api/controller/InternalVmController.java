package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.user.service.UserCleanupService;
import gj.cloud.vm.application.vm.dto.VmUsageStats;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalVmController {

    private final VmRepository vmRepository;
    private final UserCleanupService userCleanupService;

    @GetMapping("/vms/count")
    public Mono<ApiResponse<Long>> countVms(@RequestParam String userId) {
        return vmRepository.countActiveByUserId(userId)
                .map(ApiResponse::ok);
    }

    @GetMapping("/vms/usage")
    public Mono<ApiResponse<VmUsageStats>> getVmUsage(@RequestParam String userId) {
        return Mono.zip(
                vmRepository.countActiveByUserIdAndPlanTypeFree(userId),
                vmRepository.countActiveByUserIdAndPlanTypePro(userId),
                vmRepository.countActiveByPlanTypeFree(),
                vmRepository.countActiveByPlanTypePro()
        ).map(t -> ApiResponse.ok(new VmUsageStats(t.getT1(), t.getT2(), t.getT3(), t.getT4())));
    }

    @DeleteMapping("/users")
    public Mono<ApiResponse<Void>> deleteUserData(@RequestParam String userId, @RequestParam String email) {
        return userCleanupService.deleteUserData(userId, email)
                .thenReturn(ApiResponse.<Void>ok(null));
    }
}
