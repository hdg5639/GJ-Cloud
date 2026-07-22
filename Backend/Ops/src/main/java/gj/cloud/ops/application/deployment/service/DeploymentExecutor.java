package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.ComposeSpecResponse;
import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.application.deployment.dto.EnvironmentFile;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.dto.ResolvedCompose;
import gj.cloud.ops.application.deployment.dto.ServiceImageRef;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.application.deployment.git.GitReleaseManager;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import gj.cloud.ops.application.deployment.validation.ValidationResult;
import gj.cloud.ops.application.vmclient.VmDeploymentRoutesClient;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentEventType;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

// D.7 실행 파이프라인 — HTTP 요청 스레드가 아니라 전용 deploymentTaskExecutor에서 실행됨(I절 체크리스트).
// 배포 하나당 SSH 세션 1개를 처음부터 끝까지 재사용(git→업로드→검증→빌드→교체→헬스체크).
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentExecutor {

    private static final String PERMISSION_DEPLOY = "DEPLOY";
    private static final String COMPOSE_FILE_NAME = "docker-compose.yml";
    private static final String RESOLVED_COMPOSE_FILE_NAME = "resolved-compose.yml";
    private static final long DOCKER_COMPOSE_TIMEOUT_MS = 300_000;
    private static final long IMAGE_CLEANUP_TIMEOUT_MS = 120_000;
    private static final long HEALTH_CHECK_INTERVAL_MS = 3_000;
    private static final int HEALTH_CHECK_MAX_ATTEMPTS = 10;
    // vmPath가 mkdir -p '...' 셸 커맨드에 그대로 꽂히므로 따옴표/셸 메타문자를 차단 (명령 인젝션 방지)
    private static final Pattern UNSAFE_SHELL_CHARS = Pattern.compile("[;&`|$'\"\\\\]");
    // 배포 시 내부에서 생성한 태그만 삭제 대상으로 허용한다. DB 값이 손상되거나 변조돼도 임의의 Docker
    // 이미지 식별자가 셸 명령에 들어가지 않도록 형식과 app/deployment 소유권을 모두 확인한다.
    private static final Pattern SAFE_DEPLOYMENT_IMAGE_TAG = Pattern.compile(
            "^gamjabox/[A-Za-z0-9-]+/[a-z0-9][a-z0-9_-]{0,62}:[A-Za-z0-9-]+$");

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLockService lockService;
    private final DeploymentEventPublisher eventPublisher;
    private final VmServiceClient vmServiceClient;
    private final VmDeploymentRoutesClient routesClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SshCommandExecutor sshCommandExecutor;
    private final GitReleaseManager gitReleaseManager;
    private final ComposeValidator composeValidator;
    private final ComposeImageBuilder composeImageBuilder;
    private final HealthCheckExecutor healthCheckExecutor;
    private final RollbackService rollbackService;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    private final TaskExecutor deploymentTaskExecutor;

    // 권한/실행상태 확인 + D.5 Validator 통과 + 락 획득까지는 호출 스레드(HTTP 요청 스레드)에서 동기 수행하고,
    // 실제 파이프라인(runPipeline)만 전용 워커에 위임함 — 잘못된 요청은 즉시 4xx로 응답 가능해야 하므로.
    public DeploymentEntity enqueue(String bearerToken, String vmId, RepoConfig repoConfig, ComposeArtifact artifact) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }

        ValidationResult validation = composeValidator.validate(artifact.composeContent());
        if (!validation.valid()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }

        String previousDeploymentId = deploymentRepository
                .findTopByVmIdAndStatusOrderByCreatedAtDesc(vmId, DeploymentStatus.SUCCEEDED)
                .map(DeploymentEntity::getId)
                .orElse(null);

        String sourceCiphertext = cipher.encrypt(artifact.composeContent().getBytes(StandardCharsets.UTF_8));
        // 재시도/수정 후 재배포(compose-spec 조회 API)에서 그대로 복원할 수 있도록 함께 저장.
        // environmentFiles는 .env 내용(비밀값 가능)이라 compose 원문과 동일하게 암호화, 나머지는 평문 JSON.
        String environmentFilesCiphertext = artifact.environmentFiles().isEmpty()
                ? null
                : cipher.encrypt(toJson(artifact.environmentFiles()).getBytes(StandardCharsets.UTF_8));
        String exposedRoutesJson = artifact.exposedRoutes().isEmpty() ? null : toJson(artifact.exposedRoutes());
        String healthChecksJson = artifact.healthChecks().isEmpty() ? null : toJson(artifact.healthChecks());

        DeploymentEntity deployment = deploymentRepository.save(
                DeploymentEntity.createQueued(vmId, artifact.sourceType(), sourceCiphertext, previousDeploymentId,
                        environmentFilesCiphertext, exposedRoutesJson, healthChecksJson, repoConfig.context(),
                        repoConfig.installPath()));

        if (!tryLockWithStaleRecovery(vmId, deployment.getId())) {
            deployment = deploymentRepository.save(deployment.withFailed("이미 배포가 진행 중입니다."));
            throw new OpsException(OpsErrorCode.DEPLOYMENT_IN_PROGRESS);
        }

        String deploymentId = deployment.getId();
        String internalIp = context.internalIp();
        deploymentTaskExecutor.execute(() -> runPipeline(deploymentId, vmId, internalIp, bearerToken, repoConfig, artifact));

        return deployment;
    }

    // 재시도/수정 후 재배포용 — 저장된 compose 원문과 환경변수/라우트/헬스체크를 복호화해 그대로 반환.
    // repoUrl/branch/patToken은 애초에 영속화하지 않으므로(비밀값 DB 잔류 방지) 응답에 포함되지 않음 —
    // 재제출 시 사용자가 다시 입력해야 함(기존 새 배포 폼과 동일한 요구사항).
    public ComposeSpecResponse getComposeSpec(DeploymentEntity entity) {
        String composeContent = entity.getSourceComposeCiphertext() != null
                ? new String(cipher.decrypt(entity.getSourceComposeCiphertext()), StandardCharsets.UTF_8)
                : "";
        List<EnvironmentFile> environmentFiles = entity.getEnvironmentFilesCiphertext() != null
                ? readJsonList(new String(cipher.decrypt(entity.getEnvironmentFilesCiphertext()), StandardCharsets.UTF_8), EnvironmentFile.class)
                : List.of();
        List<ExposedRoute> exposedRoutes = entity.getExposedRoutesJson() != null
                ? readJsonList(entity.getExposedRoutesJson(), ExposedRoute.class)
                : List.of();
        List<HealthCheck> healthChecks = entity.getHealthChecksJson() != null
                ? readJsonList(entity.getHealthChecksJson(), HealthCheck.class)
                : List.of();
        return new ComposeSpecResponse(composeContent, environmentFiles, exposedRoutes, healthChecks, entity.getContext(), entity.getInstallPath());
    }

    // 사용자가 임의로 지정한 과거 SUCCEEDED 배포로 수동 롤백. 기존 자동 롤백(RollbackService)과 동일하게
    // 재빌드 없이 target의 release 디렉토리에 남아있는 resolved-compose.yml(이미지 태그 고정본)을 그대로 재기동함.
    // 이 롤백 자체도 하나의 배포 이력으로 남기고(RollbackService가 QUEUED→ROLLING_BACK→ROLLED_BACK으로 전이시킴)
    // 기존 SSE(/events) 스트림으로 진행 상황을 동일하게 관전할 수 있도록 함.
    public DeploymentEntity rollbackTo(String bearerToken, String vmId, DeploymentEntity target) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }
        if (target.getStatus() != DeploymentStatus.SUCCEEDED || target.getReleaseDir() == null) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_ROLLBACK_TARGET_NOT_SUCCEEDED);
        }

        DeploymentEntity rollbackEntity = deploymentRepository.save(
                DeploymentEntity.createQueued(vmId, target.getSourceType(), target.getSourceComposeCiphertext(), target.getId()));

        if (!tryLockWithStaleRecovery(vmId, rollbackEntity.getId())) {
            rollbackEntity = deploymentRepository.save(rollbackEntity.withFailed("이미 배포가 진행 중입니다."));
            throw new OpsException(OpsErrorCode.DEPLOYMENT_IN_PROGRESS);
        }

        String rollbackId = rollbackEntity.getId();
        String internalIp = context.internalIp();
        deploymentTaskExecutor.execute(() -> runRollback(rollbackId, vmId, internalIp, bearerToken));

        return rollbackEntity;
    }

    // 배포마다 컴포즈 프로젝트를 새로 만드는 게 아니라 VM당 하나(gj_{vmId})를 계속 갱신하는 구조라(D.7,
    // appId=vmId), "내리기"는 배포 이력 한 건이 아니라 그 VM에서 지금 떠 있는 것 자체를 내리는 동작이다 —
    // 그래서 대상이 그 VM의 최신 SUCCEEDED 배포인지(=지금 실제로 떠 있는 것인지) 먼저 확인한다.
    public DeploymentEntity teardown(String bearerToken, String vmId, DeploymentEntity target, List<String> removeRouteNicknames) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }
        if (target.getStatus() != DeploymentStatus.SUCCEEDED) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_TEARDOWN_TARGET_INVALID);
        }
        String latestSucceededId = deploymentRepository
                .findTopByVmIdAndStatusOrderByCreatedAtDesc(vmId, DeploymentStatus.SUCCEEDED)
                .map(DeploymentEntity::getId)
                .orElse(null);
        if (!target.getId().equals(latestSucceededId)) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_TEARDOWN_TARGET_INVALID);
        }

        if (!tryLockWithStaleRecovery(vmId, target.getId())) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_IN_PROGRESS);
        }
        DeploymentEntity stopping = null;
        boolean handedOff = false;
        try {
            stopping = deploymentRepository.save(target.withStatus(DeploymentStatus.STOPPING));
            String deploymentId = stopping.getId();
            String internalIp = context.internalIp();
            List<String> nicknamesToRemove = removeRouteNicknames != null ? removeRouteNicknames : List.of();
            deploymentTaskExecutor.execute(
                    () -> runTeardown(deploymentId, vmId, internalIp, bearerToken, nicknamesToRemove));
            handedOff = true;
            return stopping;
        } catch (RuntimeException e) {
            // 워커 제출이 실패했다면 STOPPING에 영구 고착되지 않게 원래 상태로 복구한다. DB 저장 자체가
            // 실패한 경우 stopping은 null이므로 복구할 레코드가 없고, 아래 finally에서 락만 반환한다.
            if (stopping != null) {
                try {
                    deploymentRepository.save(stopping.withStatus(DeploymentStatus.SUCCEEDED));
                } catch (RuntimeException restoreError) {
                    log.error("배포 내리기 상태 복구 실패: deploymentId={}, error={}",
                            stopping.getId(), restoreError.getMessage(), restoreError);
                }
            }
            throw e;
        } finally {
            // runTeardown에 정상 인계된 뒤에는 워커의 finally가 락을 해제한다. 그 전에 DB 제약 위반이나
            // TaskExecutor 거절이 발생하면 요청 스레드가 직접 해제해 leaked lock을 만들지 않는다.
            if (!handedOff) {
                lockService.unlock(vmId, target.getId());
            }
        }
    }

    private void runTeardown(String deploymentId, String appId, String internalIp, String bearerToken, List<String> removeRouteNicknames) {
        Session session = null;
        try {
            session = sshSessionFactory.createSession(appId, internalIp);
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "컨테이너 중지 중...");
            CommandResult downResult = sshCommandExecutor.exec(session,
                    "docker compose -p gj_" + appId + " down", DOCKER_COMPOSE_TIMEOUT_MS);
            if (!downResult.isSuccess()) {
                updateEntity(deploymentId, entity -> entity.withStatus(DeploymentStatus.SUCCEEDED));
                eventPublisher.publish(deploymentId, DeploymentEventType.ERROR,
                        "컨테이너 중지 실패: " + trim(downResult.stderr()));
                return;
            }
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "컨테이너 중지 완료");

            cleanupDeploymentImages(session, deploymentId, appId);

            if (!removeRouteNicknames.isEmpty()) {
                eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "선택한 포트 정리 중...");
                deploymentRepository.findById(deploymentId).ifPresent(entity -> {
                    List<ExposedRoute> current = entity.getExposedRoutesJson() != null
                            ? readJsonList(entity.getExposedRoutesJson(), ExposedRoute.class)
                            : List.of();
                    List<ExposedRoute> remaining = current.stream()
                            .filter(route -> !removeRouteNicknames.contains(route.nickname()))
                            .toList();
                    try {
                        routesClient.syncRoutes(bearerToken, appId, new DeploymentRoutesRequest(deploymentId, remaining));
                        eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "포트 정리 완료");
                    } catch (Exception e) {
                        // 컨테이너는 이미 내려갔으므로 배포 자체는 그대로 STOPPED 처리 — 포트 정리 실패는 경고만
                        eventPublisher.publish(deploymentId, DeploymentEventType.ERROR,
                                "포트 정리 실패 (컨테이너는 정상적으로 내려감, 재시도 필요): " + e.getMessage());
                    }
                });
            }

            updateEntity(deploymentId, entity -> entity.withStatus(DeploymentStatus.STOPPED));
            eventPublisher.publish(deploymentId, DeploymentEventType.DONE, "배포 내리기 완료");
        } catch (Exception e) {
            log.error("배포 내리기 처리 중 예외: deploymentId={}, error={}", deploymentId, e.getMessage());
            updateEntity(deploymentId, entity -> entity.withStatus(DeploymentStatus.SUCCEEDED));
            eventPublisher.publish(deploymentId, DeploymentEventType.ERROR, "배포 내리기 실패: " + e.getMessage());
        } finally {
            lockService.unlock(appId, deploymentId);
            eventPublisher.complete(deploymentId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private void cleanupDeploymentImages(Session session, String deploymentId, String appId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null || deployment.getServiceImageRefsJson() == null
                || deployment.getServiceImageRefsJson().isBlank()) {
            return;
        }

        try {
            Map<String, ServiceImageRef> refs = objectMapper.readValue(
                    deployment.getServiceImageRefsJson(), new TypeReference<>() {});
            if (refs == null || refs.isEmpty()) {
                return;
            }

            String expectedPrefix = "gamjabox/" + appId + "/";
            String expectedSuffix = ":" + deploymentId;
            List<String> imageTags = refs.values().stream()
                    .filter(ref -> ref != null && ref.imageTag() != null)
                    .map(ServiceImageRef::imageTag)
                    .filter(tag -> SAFE_DEPLOYMENT_IMAGE_TAG.matcher(tag).matches())
                    .filter(tag -> tag.startsWith(expectedPrefix) && tag.endsWith(expectedSuffix))
                    .distinct()
                    .sorted()
                    .toList();

            if (imageTags.size() != refs.size()) {
                log.warn("배포 이미지 정리에서 소유권이 확인되지 않은 태그 제외: deploymentId={}, total={}, safe={}",
                        deploymentId, refs.size(), imageTags.size());
            }
            if (imageTags.isEmpty()) {
                return;
            }

            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "배포 이미지 정리 중...");
            String command = "docker image rm " + String.join(" ", imageTags.stream()
                    .map(tag -> "'" + tag + "'")
                    .toList());
            CommandResult result = sshCommandExecutor.exec(session, command, IMAGE_CLEANUP_TIMEOUT_MS);
            if (result.isSuccess()) {
                eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "배포 이미지 정리 완료");
                return;
            }

            log.warn("배포 이미지 정리 실패: deploymentId={}, stderr={}", deploymentId, trim(result.stderr()));
            eventPublisher.publish(deploymentId, DeploymentEventType.ERROR,
                    "배포 이미지 정리 실패 (컨테이너는 정상적으로 내려감, Docker 관리에서 직접 삭제해주세요.)");
        } catch (Exception e) {
            // 컨테이너는 이미 정상적으로 내려갔다. 이미지 정리 문제만으로 상태를 SUCCEEDED로 되돌리면 실제
            // 런타임 상태와 어긋나므로 경고를 남기고 teardown의 STOPPED 전이는 계속 진행한다.
            log.warn("배포 이미지 정보 처리 실패: deploymentId={}, error={}", deploymentId, e.getMessage());
            eventPublisher.publish(deploymentId, DeploymentEventType.ERROR,
                    "배포 이미지 정리 실패 (컨테이너는 정상적으로 내려감, Docker 관리에서 직접 삭제해주세요.)");
        }
    }

    private void runRollback(String deploymentId, String appId, String internalIp, String bearerToken) {
        Session session = null;
        try {
            session = sshSessionFactory.createSession(appId, internalIp);
            rollbackService.rollback(session, deploymentId, appId, bearerToken, "사용자 요청에 의한 수동 롤백");
        } catch (Exception e) {
            log.error("수동 롤백 처리 중 예외: deploymentId={}, error={}", deploymentId, e.getMessage());
            updateEntity(deploymentId, entity -> entity.withFailed(e.getMessage()));
            eventPublisher.publish(deploymentId, DeploymentEventType.ERROR, "롤백 실패: " + e.getMessage());
        } finally {
            lockService.unlock(appId, deploymentId);
            eventPublisher.complete(deploymentId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    private <T> List<T> readJsonList(String json, Class<T> type) {
        try {
            return objectMapper.readerForListOf(type).readValue(json);
        } catch (Exception e) {
            return List.of();
        }
    }

    // Ops 프로세스가 파이프라인 도중(비정상 종료 등으로) finally의 unlock을 못 타면 락이 leaked 상태로
    // TTL(기본 30분) 내내 남아 새 배포/롤백/내리기가 전부 DEPLOYMENT_IN_PROGRESS로 막힌다. 이 락을 쥔
    // deploymentId가 이미 터미널 상태(SUCCEEDED/FAILED/STOPPED/ROLLED_BACK)라면 그 배포의 파이프라인이
    // 실제로는 끝났다는 뜻이므로 — 즉 이 락은 확실히 leaked다 — 강제로 비우고 한 번 더 시도한다.
    private boolean tryLockWithStaleRecovery(String vmId, String newDeploymentId) {
        if (lockService.tryLock(vmId, newDeploymentId)) {
            return true;
        }
        Optional<String> holderId = lockService.currentHolder(vmId);
        boolean staleLock = holderId.isEmpty() || holderId
                .flatMap(deploymentRepository::findById)
                .map(entity -> isTerminalStatus(entity.getStatus()))
                .orElse(true);
        if (!staleLock) {
            return false;
        }
        log.warn("leaked deployment lock 감지 — 강제 해제 후 재시도: vmId={}, staleHolder={}", vmId, holderId.orElse(null));
        lockService.forceUnlock(vmId);
        return lockService.tryLock(vmId, newDeploymentId);
    }

    private boolean isTerminalStatus(DeploymentStatus status) {
        return status == DeploymentStatus.SUCCEEDED || status == DeploymentStatus.FAILED
                || status == DeploymentStatus.STOPPED || status == DeploymentStatus.ROLLED_BACK;
    }

    private void runPipeline(String deploymentId, String appId, String internalIp, String bearerToken,
                              RepoConfig repoConfig, ComposeArtifact artifact) {
        Session session = null;
        try {
            session = sshSessionFactory.createSession(appId, internalIp);

            updateStatus(deploymentId, DeploymentStatus.CLONING, "소스 체크아웃 시작 (branch: " + repoConfig.branch() + ")");
            gitReleaseManager.ensureBareRepo(session, appId, repoConfig.repoUrl(), repoConfig.patToken());
            String commitSha = gitReleaseManager.fetchAndResolveCommit(session, appId, repoConfig.branch(), repoConfig.patToken());
            gitReleaseManager.createWorktree(session, appId, deploymentId, commitSha);
            String releaseDir = gitReleaseManager.releaseDir(appId, deploymentId);
            updateEntity(deploymentId, e -> e.withSourceRevision(commitSha, releaseDir));
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE,
                    "소스 체크아웃 완료 (" + repoConfig.branch() + " → " + shortSha(commitSha) + ")");

            String contextDir = resolveContextDir(releaseDir, repoConfig.context());

            updateStatus(deploymentId, DeploymentStatus.UPLOADING, "파일 전송 중...");
            uploadFiles(session, releaseDir, contextDir, artifact);
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "파일 전송 완료");

            updateStatus(deploymentId, DeploymentStatus.VALIDATING, "compose 검증 중...");
            String composeFilePath = contextDir + "/" + COMPOSE_FILE_NAME;
            CommandResult configCheck = sshCommandExecutor.exec(session,
                    "docker compose -p gj_" + appId + " -f '" + composeFilePath + "' config", 60_000);
            if (!configCheck.isSuccess()) {
                failImmediately(deploymentId, "compose 검증 실패: " + trim(configCheck.stderr()));
                return;
            }
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "compose 검증 완료");

            updateStatus(deploymentId, DeploymentStatus.BUILDING, "이미지 빌드 중...");
            ResolvedCompose resolved;
            try {
                resolved = composeImageBuilder.buildAndResolve(session, appId, deploymentId, contextDir, artifact.composeContent());
            } catch (Exception e) {
                failImmediately(deploymentId, "이미지 빌드 실패: " + e.getMessage());
                return;
            }
            String resolvedFilePath = contextDir + "/" + RESOLVED_COMPOSE_FILE_NAME;
            writeRemoteFile(session, resolvedFilePath, resolved.resolvedComposeContent());
            String imageRefsJson = objectMapper.writeValueAsString(resolved.serviceImageRefs());
            String resolvedCiphertext = cipher.encrypt(resolved.resolvedComposeContent().getBytes(StandardCharsets.UTF_8));
            updateEntity(deploymentId, e -> e.withResolvedCompose(resolvedCiphertext, imageRefsJson));
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "이미지 빌드 완료");

            // ---- 여기서부터는 실패 시 즉시 중단이 아니라 롤백 (기존 컨테이너 교체 절차가 이미 시작됐으므로) ----
            updateStatus(deploymentId, DeploymentStatus.SWAPPING, "컨테이너 교체 중...");
            CommandResult upResult = sshCommandExecutor.exec(session,
                    "docker compose -p gj_" + appId + " -f '" + resolvedFilePath + "' up -d", DOCKER_COMPOSE_TIMEOUT_MS);
            if (!upResult.isSuccess()) {
                rollbackService.rollback(session, deploymentId, appId, bearerToken, "컨테이너 교체 실패: " + trim(upResult.stderr()));
                return;
            }
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "컨테이너 교체 완료");

            updateStatus(deploymentId, DeploymentStatus.HEALTH_CHECKING, "헬스체크 중...");
            if (!runHealthChecks(session, appId, artifact.healthChecks())) {
                rollbackService.rollback(session, deploymentId, appId, bearerToken, "헬스체크 실패");
                return;
            }
            eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "헬스체크 통과");

            updateStatus(deploymentId, DeploymentStatus.ROUTING, "라우트 등록 중...");
            try {
                routesClient.syncRoutes(bearerToken, appId, new DeploymentRoutesRequest(deploymentId, artifact.exposedRoutes()));
                eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, "라우트 등록 완료");
            } catch (Exception e) {
                // D.7 실패 처리 표: Cloudflare 등록 실패는 배포 완료 처리, Cloudflare만 재시도 안내
                eventPublisher.publish(deploymentId, DeploymentEventType.ERROR,
                        "라우트 등록 실패 (배포 자체는 완료됨, 재시도 필요): " + e.getMessage());
            }

            gitReleaseManager.updateCurrentSymlink(session, appId, deploymentId);

            if (repoConfig.installPath() != null && !repoConfig.installPath().isBlank()) {
                try {
                    gitReleaseManager.linkInstallPath(session, appId, repoConfig.installPath());
                    eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE,
                            "VM 배포 경로 연결 완료 (" + repoConfig.installPath() + ")");
                } catch (Exception e) {
                    // 앱 자체는 정상 기동했으므로 배포 실패로 취급하지 않고 경고만 남김 (라우트 등록 실패와 동일한 관례)
                    eventPublisher.publish(deploymentId, DeploymentEventType.ERROR,
                            "VM 배포 경로 연결 실패 (배포 자체는 완료됨): " + e.getMessage());
                }
            }

            updateEntity(deploymentId, DeploymentEntity::withSucceeded);
            eventPublisher.publish(deploymentId, DeploymentEventType.DONE, "배포 완료");
        } catch (Exception e) {
            log.error("배포 파이프라인 처리 중 예외: deploymentId={}, error={}", deploymentId, e.getMessage());
            updateEntity(deploymentId, entity -> entity.withFailed(e.getMessage()));
            eventPublisher.publish(deploymentId, DeploymentEventType.ERROR, "배포 실패: " + e.getMessage());
        } finally {
            lockService.unlock(appId, deploymentId);
            eventPublisher.complete(deploymentId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    // SWAPPING 이전 단계 실패 — 기존 서비스가 아직 그대로이므로 롤백 없이 즉시 FAILED (D.7 실패 처리 표)
    private void failImmediately(String deploymentId, String message) {
        updateEntity(deploymentId, entity -> entity.withFailed(message));
        eventPublisher.publish(deploymentId, DeploymentEventType.ERROR, message);
    }

    private boolean runHealthChecks(Session session, String appId, List<HealthCheck> healthChecks) {
        if (healthChecks == null || healthChecks.isEmpty()) {
            return true;
        }
        for (HealthCheck healthCheck : healthChecks) {
            if (!waitForHealthy(session, appId, healthCheck)) {
                return false;
            }
        }
        return true;
    }

    private boolean waitForHealthy(Session session, String appId, HealthCheck healthCheck) {
        for (int attempt = 1; attempt <= HEALTH_CHECK_MAX_ATTEMPTS; attempt++) {
            if (healthCheckExecutor.check(session, appId, healthCheck)) {
                return true;
            }
            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // compose 원문 + 업로드 파일들을 release 디렉토리에 SFTP로 전송. 소스 체크아웃 "이후"에 전송해
    // 체크아웃 결과를 덮어쓰는 순서를 보장함 (D.4)
    // compose 파일 자체는 사용자가 선택한 컨텍스트 디렉토리(contextDir)에 두어 build context가 자동으로
    // 그 디렉토리 기준으로 잡히게 함. 환경변수 파일은 기존과 동일하게 release 루트 기준 상대경로 유지.
    private void uploadFiles(Session session, String releaseDir, String contextDir, ComposeArtifact artifact) {
        if (!contextDir.equals(releaseDir)) {
            sshCommandExecutor.execOrThrow(session, "mkdir -p '" + contextDir + "'", 10_000);
        }
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(10_000);

            writeSftpFile(sftp, contextDir + "/" + COMPOSE_FILE_NAME, artifact.composeContent().getBytes(StandardCharsets.UTF_8));

            for (EnvironmentFile file : artifact.environmentFiles()) {
                String targetPath = resolveVmPath(releaseDir, file.vmPath());
                ensureParentDir(session, targetPath);
                writeSftpFile(sftp, targetPath, file.content().getBytes(StandardCharsets.UTF_8));
            }
            for (UploadedFile file : artifact.uploadedFiles()) {
                String targetPath = resolveVmPath(releaseDir, file.vmPath());
                ensureParentDir(session, targetPath);
                writeSftpFile(sftp, targetPath, file.content());
            }
        } catch (JSchException | SftpException e) {
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
        }
    }

    private void writeRemoteFile(Session session, String path, String content) {
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(10_000);
            writeSftpFile(sftp, path, content.getBytes(StandardCharsets.UTF_8));
        } catch (JSchException | SftpException e) {
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
        }
    }

    private void writeSftpFile(ChannelSftp sftp, String path, byte[] content) throws SftpException {
        sftp.put(new ByteArrayInputStream(content), path);
    }

    private void ensureParentDir(Session session, String targetPath) {
        String parent = targetPath.substring(0, targetPath.lastIndexOf('/'));
        sshCommandExecutor.execOrThrow(session, "mkdir -p '" + parent + "'", 10_000);
    }

    // context는 release 디렉토리 기준 상대 경로의 서브디렉토리 하나만 가리킴 ("backend", "services/api" 등).
    // null/빈 값/"."이면 저장소 루트에서 배포. resolveVmPath와 동일한 이유로 루트 탈출·셸 메타문자를 차단.
    private String resolveContextDir(String releaseDir, String context) {
        if (context == null || context.isBlank() || context.equals(".")) {
            return releaseDir;
        }
        String cleaned = context.trim();
        if (cleaned.contains("..") || cleaned.startsWith("/") || cleaned.contains("~") || UNSAFE_SHELL_CHARS.matcher(cleaned).find()) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        cleaned = cleaned.startsWith("./") ? cleaned.substring(2) : cleaned;
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isBlank() ? releaseDir : releaseDir + "/" + cleaned;
    }

    // vmPath는 release 디렉토리 기준 상대 경로로 취급 ("./auth/.env", "nginx/nginx.conf" 등). 루트 탈출 방지.
    // ensureParentDir에서 이 값을 그대로 'mkdir -p' 셸 커맨드에 꽂아 넣으므로, 따옴표/셸 메타문자도 함께 차단해야 함.
    private String resolveVmPath(String releaseDir, String vmPath) {
        if (vmPath == null || vmPath.contains("..") || UNSAFE_SHELL_CHARS.matcher(vmPath).find()) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        String cleaned = vmPath.startsWith("./") ? vmPath.substring(2) : vmPath;
        cleaned = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        return releaseDir + "/" + cleaned;
    }

    private void updateStatus(String deploymentId, DeploymentStatus status, String message) {
        updateEntity(deploymentId, e -> e.withStatus(status));
        eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE, message);
    }

    private void updateEntity(String deploymentId, UnaryOperator<DeploymentEntity> mutator) {
        deploymentRepository.findById(deploymentId).ifPresent(entity -> deploymentRepository.save(mutator.apply(entity)));
    }

    private String shortSha(String commitSha) {
        return commitSha.substring(0, Math.min(7, commitSha.length()));
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
