package gj.cloud.vm.api.controller;

import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalVmController {

    private final VmRepository vmRepository;

    @GetMapping("/vms/count")
    public Mono<ApiResponse<Long>> countVms(@RequestParam String userId) {
        return vmRepository.countActiveByUserId(userId)
                .map(ApiResponse::ok);
    }
}
