package gj.cloud.user.api.controller;

import gj.cloud.user.global.storage.ProfileImageStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

// 프로필 이미지는 아바타로 다른 사용자에게도 노출되는 정적 파일이라 인증 없이 공개 서빙
// (SecurityConfig의 기본 체인에서 permitAll). 업로드마다 파일명이 UUID로 새로 생성되므로
// 강한 캐시(1년)를 걸어도 이미지 교체 시 URL 자체가 바뀌어 캐시 무효화 문제가 없다.
// 배포 서버 Caddy는 host 구분 없이 접두사 없는 bare path(`/users`, `/vms`, `/ops` 등)로만 각
// 서비스에 라우팅하므로, 이 컨트롤러도 반드시 `/users`로 시작해야 브라우저 요청이 실제로 이
// 서비스까지 도달한다 — `/uploads/profile-images`로만 매핑했다가 Caddy가 어떤 서비스로도 안
// 보내고 프론트 catch-all로 흘려보내 이미지가 조용히 깨지던 문제(에러 없이 로드만 실패)로 발견.
@Tag(name = "ProfileImage", description = "프로필 이미지 공개 서빙")
@SecurityRequirements
@RestController
@RequestMapping("/users/uploads/profile-images")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageStorage profileImageStorage;

    @Operation(summary = "프로필 이미지 조회", description = "UUID 파일명의 공개 프로필 이미지를 1년 immutable 캐시 정책으로 반환합니다.")
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Resource resource = profileImageStorage.loadAsResource(filename);
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(filename))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(resource);
    }

    private MediaType mediaTypeFor(String filename) {
        if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (filename.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
