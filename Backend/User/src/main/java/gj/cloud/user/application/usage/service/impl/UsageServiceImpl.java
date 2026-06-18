package gj.cloud.user.application.usage.service.impl;

import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.application.usage.dto.UsageResponse;
import gj.cloud.user.application.usage.service.UsageService;
import gj.cloud.user.application.vm.client.VmServiceClient;
import gj.cloud.user.domain.plan.enums.PlanType;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsageServiceImpl implements UsageService {

    private final UserProfileRepository profileRepository;
    private final ProfileService profileService;
    private final VmServiceClient vmServiceClient;

    @Override
    @Transactional(readOnly = true)
    public UsageResponse getUsage(String userId, String email, String bearerToken) {
        PlanType planType = profileRepository.findById(userId)
                .map(p -> p.getPlanType())
                .orElseGet(() -> {
                    profileService.getProfile(userId, email);
                    return PlanType.FREE;
                });

        long currentVmCount = vmServiceClient.getVmCount(userId, bearerToken);

        return new UsageResponse(
                planType.name(),
                planType.getVCpu(),
                planType.getRamGb(),
                (int) currentVmCount,
                planType.getMaxVmCount()
        );
    }
}
