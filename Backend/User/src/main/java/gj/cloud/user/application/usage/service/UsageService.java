package gj.cloud.user.application.usage.service;

import gj.cloud.user.application.usage.dto.UsageResponse;

public interface UsageService {
    UsageResponse getUsage(String userId, String email);
}
