package gj.cloud.vm.application.user.service;

import reactor.core.publisher.Mono;

public interface UserCleanupService {
    Mono<Void> deleteUserData(String userId, String email);
}
