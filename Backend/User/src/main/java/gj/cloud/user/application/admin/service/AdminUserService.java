package gj.cloud.user.application.admin.service;

import gj.cloud.user.application.admin.dto.AdminUserResponse;

import java.util.List;

public interface AdminUserService {
    List<AdminUserResponse> listUsers();
    AdminUserResponse getUser(String userId);
    AdminUserResponse suspendUser(String userId);
    AdminUserResponse activateUser(String userId);
}
