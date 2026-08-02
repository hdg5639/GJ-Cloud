package gj.cloud.user.global.storage;

import gj.cloud.user.application.docs.dto.DocsImageUploadResponse;
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

@Slf4j
@Component
public class DocsImageStorage {

    private final Path storageDir;
    private final String publicUrlPrefix;
    private final long maxSizeBytes;

    public DocsImageStorage(
            @Value("${user.docs-image.storage-path}") String storagePath,
            @Value("${user.docs-image.public-url-prefix}") String publicUrlPrefix,
            @Value("${user.docs-image.max-size-bytes}") long maxSizeBytes
    ) {
        this.storageDir = Paths.get(storagePath);
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/")
                ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1)
                : publicUrlPrefix;
        this.maxSizeBytes = maxSizeBytes;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("문서 이미지 저장 경로를 생성할 수 없습니다: " + storagePath, e);
        }
    }

    public DocsImageUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserException(UserErrorCode.INVALID_DOCS_IMAGE_FORMAT);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new UserException(UserErrorCode.DOCS_IMAGE_TOO_LARGE);
        }
        String extension = detectExtension(file);
        String filename = UUID.randomUUID() + "." + extension;
        Path target = storageDir.resolve(filename);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("문서 이미지 저장 실패: {}", e.getMessage());
            throw new UserException(UserErrorCode.DOCS_IMAGE_UPLOAD_FAILED);
        }
        return new DocsImageUploadResponse(publicUrlPrefix + "/" + filename, filename);
    }

    public Resource loadAsResource(String filename) {
        if (!isSafeFilename(filename)) {
            throw new UserException(UserErrorCode.DOCS_IMAGE_NOT_FOUND);
        }
        Path file = storageDir.resolve(filename);
        if (!Files.isRegularFile(file)) {
            throw new UserException(UserErrorCode.DOCS_IMAGE_NOT_FOUND);
        }
        return new FileSystemResource(file);
    }

    private boolean isSafeFilename(String filename) {
        return filename != null && !filename.isBlank()
                && !filename.contains("/") && !filename.contains("\\") && !filename.contains("..");
    }

    private String detectExtension(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(16);
        } catch (IOException e) {
            throw new UserException(UserErrorCode.INVALID_DOCS_IMAGE_FORMAT);
        }
        if (matches(header, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (matches(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "webp";
        if (matches(header, 'G', 'I', 'F', '8', '7', 'a') || matches(header, 'G', 'I', 'F', '8', '9', 'a')) return "gif";
        throw new UserException(UserErrorCode.INVALID_DOCS_IMAGE_FORMAT);
    }

    private boolean matches(byte[] header, int... signature) {
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i += 1) {
            if ((header[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
