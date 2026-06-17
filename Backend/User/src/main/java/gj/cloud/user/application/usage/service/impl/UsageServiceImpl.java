package gj.cloud.user.application.usage.service.impl;

import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.application.usage.dto.UsageResponse;
import gj.cloud.user.application.usage.service.UsageService;
import gj.cloud.user.domain.plan.enums.PlanType;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsageServiceImpl implements UsageService {

    private final UserProfileRepository profileRepository;
    private final ProfileService profileService;

    @Override
    @Transactional(readOnly = true)
    public UsageResponse getUsage(String userId, String email) {
        // Lazy provision if needed, then fetch plan
        PlanType planType = profileRepository.findById(userId)
                .map(p -> p.getPlanType())
                .orElseGet(() -> {
                    profileService.getProfile(userId, email);
                    return PlanType.FREE;
                });

        // TODO: call vm-service GET /internal/vms/count?userId={userId} via Token Exchange
        int currentVmCount = 0;

        return new UsageResponse(
                planType.name(),
                planType.getVCpu(),
                planType.getRamGb(),
                currentVmCount,
                planType.getMaxVmCount()
        );
    }
}
