package gj.cloud.user.api.controller;

import gj.cloud.user.global.storage.DocsImageStorage;
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

@Tag(name = "Docs Image", description = "발행 문서 이미지 공개 서빙")
@SecurityRequirements
@RestController
@RequestMapping("/users/docs/images")
@RequiredArgsConstructor
public class DocsImageController {

    private final DocsImageStorage docsImageStorage;

    @Operation(
            summary = "문서 이미지 조회",
            description = "저장된 JPEG, PNG, WebP 또는 GIF 문서 이미지를 공개 캐시 정책으로 반환합니다."
    )
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(mediaType(filename))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(docsImageStorage.loadAsResource(filename));
    }

    private MediaType mediaType(String filename) {
        if (filename.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (filename.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (filename.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_JPEG;
    }
}
