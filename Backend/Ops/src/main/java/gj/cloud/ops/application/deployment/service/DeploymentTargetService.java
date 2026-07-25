package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.DeploymentTargetResponse;
import gj.cloud.ops.application.deployment.dto.EnvironmentFile;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.application.github.dto.GithubRepositoryAccess;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeploymentTargetService {

    private static final Pattern SAFE_REPOSITORY_URL =
            Pattern.compile("^https://[A-Za-z0-9._/:@-]+$");
    private static final Pattern SAFE_BRANCH = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final DeploymentTargetRepository targetRepository;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    public DeploymentTargetEntity create(
            String vmId,
            String ownerUserId,
            String ownerEmail,
            String targetName,
            String repositoryUrl,
            String branch,
            GithubRepositoryAccess githubAccess,
            ComposeArtifact artifact,
            String context,
            String installPath,
            boolean autoDeploy
    ) {
        String normalizedName = targetName == null ? "" : targetName.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 60) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
        // Auto Preview는 Git 저장소가 없는 target(repositoryUrl/branch가 빈 값)이라 이 검증 대상이 아니다 —
        // repositoryUrl이 실제로 채워진 경우에만 형식을 검증한다(빈 값 자체를 허용하는 건 이 한 가지 경우뿐).
        boolean hasRepository = repositoryUrl != null && !repositoryUrl.isBlank();
        if (hasRepository) {
            validateRepositoryUrl(repositoryUrl);
            validateBranch(branch);
        }
        if (targetRepository.existsByVmIdAndNameIgnoreCaseAndActiveTrue(vmId, normalizedName)) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_TARGET_NAME_DUPLICATED);
        }
        if (autoDeploy && githubAccess == null) {
            throw new OpsException(OpsErrorCode.AUTO_DEPLOY_REQUIRES_GITHUB);
        }

        String sourceCiphertext = encryptText(artifact.composeContent());
        String environmentsCiphertext = encryptJsonOrNull(artifact.environmentFiles());
        String uploadedFilesCiphertext = encryptJsonOrNull(artifact.uploadedFiles());
        String routesJson = jsonOrNull(artifact.exposedRoutes());
        String healthChecksJson = jsonOrNull(artifact.healthChecks());

        try {
            return targetRepository.saveAndFlush(DeploymentTargetEntity.create(
                    vmId,
                    ownerUserId,
                    ownerEmail,
                    normalizedName,
                    repositoryUrl,
                    branch,
                    githubAccess != null ? githubAccess.installationId() : null,
                    githubAccess != null ? githubAccess.repositoryId() : null,
                    githubAccess != null ? githubAccess.fullName() : null,
                    artifact.sourceType(),
                    sourceCiphertext,
                    environmentsCiphertext,
                    uploadedFilesCiphertext,
                    routesJson,
                    healthChecksJson,
                    context,
                    installPath,
                    autoDeploy
            ));
        } catch (DataIntegrityViolationException e) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_TARGET_NAME_DUPLICATED);
        }
    }

    public List<DeploymentTargetResponse> list(String vmId) {
        return targetRepository.findAllByVmIdAndActiveTrueOrderByCreatedAtAsc(vmId)
                .stream()
                .map(DeploymentTargetResponse::from)
                .toList();
    }

    public DeploymentTargetEntity findOwned(String vmId, String targetId) {
        return targetRepository.findByIdAndVmIdAndActiveTrue(targetId, vmId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.DEPLOYMENT_TARGET_NOT_FOUND));
    }

    @Transactional
    public DeploymentTargetEntity setAutoDeploy(String vmId, String targetId, boolean enabled) {
        DeploymentTargetEntity target = findOwned(vmId, targetId);
        if (enabled && (target.getGithubInstallationId() == null || target.getGithubRepositoryId() == null)) {
            throw new OpsException(OpsErrorCode.AUTO_DEPLOY_REQUIRES_GITHUB);
        }
        targetRepository.updateAutoDeploy(targetId, enabled, LocalDateTime.now());
        return findOwned(vmId, targetId);
    }

    @Transactional
    public void deactivate(String targetId) {
        targetRepository.deactivate(targetId, LocalDateTime.now());
    }

    public ComposeArtifact restoreArtifact(DeploymentTargetEntity target) {
        return new ComposeArtifact(
                decryptText(target.getSourceComposeCiphertext()),
                decryptList(target.getEnvironmentFilesCiphertext(), EnvironmentFile.class),
                decryptList(target.getUploadedFilesCiphertext(), UploadedFile.class),
                readList(target.getExposedRoutesJson(), ExposedRoute.class),
                readList(target.getHealthChecksJson(), HealthCheck.class),
                target.getSourceType()
        );
    }

    @Transactional
    public void markRequested(DeploymentTargetEntity target, String revision) {
        LocalDateTime now = LocalDateTime.now();
        targetRepository.markRequested(target.getId(), revision, now, now);
    }

    @Transactional
    public void markDeploymentResult(String targetId, String deploymentId, String revision, boolean succeeded) {
        if (!succeeded) {
            return;
        }
        markActiveDeployment(targetId, deploymentId, revision);
    }

    @Transactional
    public void markActiveDeployment(String targetId, String deploymentId, String revision) {
        targetRepository.markDeploymentSucceeded(
                targetId, deploymentId, revision, LocalDateTime.now());
    }

    @Transactional
    public void clearActiveDeployment(String targetId, String deploymentId) {
        targetRepository.clearActiveDeployment(
                targetId, deploymentId, LocalDateTime.now());
    }

    public boolean isActiveDeployment(String targetId, String deploymentId) {
        return targetRepository.findById(targetId)
                .map(target -> deploymentId.equals(target.getLatestDeploymentId()))
                .orElse(false);
    }

    private String encryptText(String value) {
        return cipher.encrypt(value.getBytes(StandardCharsets.UTF_8));
    }

    private void validateRepositoryUrl(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl);
            if (repositoryUrl.length() > 500
                    || !SAFE_REPOSITORY_URL.matcher(repositoryUrl).matches()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
            }
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    private void validateBranch(String branch) {
        if (branch == null
                || branch.isBlank()
                || branch.length() > 255
                || branch.startsWith("-")
                || !SAFE_BRANCH.matcher(branch).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    private String decryptText(String value) {
        return new String(cipher.decrypt(value), StandardCharsets.UTF_8);
    }

    private String encryptJsonOrNull(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return encryptText(writeJson(values));
    }

    private String jsonOrNull(List<?> values) {
        return values == null || values.isEmpty() ? null : writeJson(values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    private <T> List<T> decryptList(String ciphertext, Class<T> type) {
        return ciphertext == null ? List.of() : readList(decryptText(ciphertext), type);
    }

    private <T> List<T> readList(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(type).readValue(json);
        } catch (Exception e) {
            throw new IllegalStateException("저장된 배포 설정을 복원하지 못했습니다.", e);
        }
    }
}
