package gj.cloud.user.global.storage;

import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// 프로필 이미지를 로컬 디스크에 저장하고 공개 URL을 돌려주는 저장소. 클라이언트가 보낸
// Content-Type/파일명은 신뢰하지 않고 실제 바이트(매직 넘버)로 형식을 판별한다 — SVG 등 스크립트를
// 담을 수 있는 형식은 애초에 허용 목록에 없어 저장 자체가 불가능(아바타로 서빙되는 정적 파일이라
// 저장형 XSS 벡터를 원천 차단하는 게 중요).
@Slf4j
@Component
public class ProfileImageStorage {

    private final Path storageDir;
    private final String publicUrlPrefix;
    private final long maxSizeBytes;

    public ProfileImageStorage(
            @Value("${user.profile-image.storage-path}") String storagePath,
            @Value("${user.profile-image.public-url-prefix}") String publicUrlPrefix,
            @Value("${user.profile-image.max-size-bytes}") long maxSizeBytes
    ) {
        this.storageDir = Paths.get(storagePath);
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/")
                ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1)
                : publicUrlPrefix;
        this.maxSizeBytes = maxSizeBytes;
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("프로필 이미지 저장 경로를 생성할 수 없습니다: " + storagePath, e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserException(UserErrorCode.INVALID_IMAGE_FORMAT);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new UserException(UserErrorCode.IMAGE_TOO_LARGE);
        }

        String extension = detectExtension(file);
        String filename = UUID.randomUUID() + "." + extension;
        Path target = storageDir.resolve(filename);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("프로필 이미지 저장 실패: {}", e.getMessage());
            throw new UserException(UserErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return publicUrlPrefix + "/" + filename;
    }

    // 이전 이미지 삭제는 best-effort — 실패해도 새 이미지 저장/프로필 갱신 자체는 막지 않는다.
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith(publicUrlPrefix + "/")) {
            return;
        }
        String filename = url.substring((publicUrlPrefix + "/").length());
        // path traversal 방지 — 파일명에 구분자가 섞여 들어올 수 없게 정확히 단일 세그먼트인지 확인
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(storageDir.resolve(filename));
        } catch (IOException e) {
            log.warn("이전 프로필 이미지 삭제 실패: filename={}, error={}", filename, e.getMessage());
        }
    }

    // 경로순회 방지 — 파일명이 단일 세그먼트인지 확인 후에만 storageDir 하위로 resolve
    public Resource loadAsResource(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }
        Path file = storageDir.resolve(filename);
        if (!Files.isRegularFile(file)) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }
        return new FileSystemResource(file);
    }

    private String detectExtension(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException e) {
            throw new UserException(UserErrorCode.INVALID_IMAGE_FORMAT);
        }

        if (matches(header, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (matches(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        throw new UserException(UserErrorCode.INVALID_IMAGE_FORMAT);
    }

    private boolean matches(byte[] header, int... signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((header[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
