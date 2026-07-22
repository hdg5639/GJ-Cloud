package gj.cloud.user.api.controller;

import gj.cloud.user.global.storage.ProfileImageStorage;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "ProfileImage", description = "프로필 이미지 공개 서빙")
@RestController
@RequestMapping("/uploads/profile-images")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageStorage profileImageStorage;

    @Operation(summary = "프로필 이미지 조회")
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
