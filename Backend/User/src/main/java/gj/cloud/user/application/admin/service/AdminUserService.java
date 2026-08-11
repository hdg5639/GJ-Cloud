package gj.cloud.user.application.admin.service;

import gj.cloud.user.application.admin.dto.AdminUserResponse;
import gj.cloud.user.application.admin.dto.PlanUpdateRequest;
import gj.cloud.user.domain.plan.enums.PlanType;

import java.util.List;
import org.springframework.data.domain.Page;

public interface AdminUserService {
    List<AdminUserResponse> listUsers();
    Page<AdminUserResponse> listUsers(int page, int size);
    List<AdminUserResponse> listUsersByIds(List<String> userIds);
    AdminUserResponse getUser(String userId);
    AdminUserResponse suspendUser(String userId);
    AdminUserResponse activateUser(String userId);
    AdminUserResponse updatePlan(String userId, PlanType planType);
}
