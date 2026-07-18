package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.ResolvedCompose;
import gj.cloud.ops.application.deployment.dto.ServiceImageRef;
import gj.cloud.ops.domain.deployment.enums.DeploymentEventType;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// D.7 4~5단계 — 서비스별 이미지 빌드(태그: gamjabox/{appId}/{service}:{deploymentId}) 후,
// 원본 compose에서 build 필드를 제거하고 방금 빌드한 이미지 태그로 고정한 resolvedCompose를 생성.
// image만 있고 build가 없는 서비스(postgres/redis 등 기성 이미지)는 그대로 둠 — 재빌드 대상 아님.
@Component
@RequiredArgsConstructor
public class ComposeImageBuilder {

    private static final long BUILD_TIMEOUT_MS = 600_000; // 10분
    private static final long INSPECT_TIMEOUT_MS = 30_000;
    // 컴포즈 YAML은 사용자가 제출한 값이므로, docker build 커맨드 문자열에 그대로 꽂아 넣기 전에
    // 셸 메타문자·따옴표·경로 탈출(..)을 반드시 걸러냄 (명령 인젝션/디렉토리 탈출 방지)
    private static final Pattern UNSAFE_PATH_SEGMENT = Pattern.compile("[;&`|$'\"\\\\]|\\.\\.");

    // 로그 전체를 한 번에 담기엔 이벤트 payload가 너무 커질 수 있어 마지막 일부만 발행 (D.8 "로그 펼치기" 데이터 소스)
    private static final int BUILD_LOG_TAIL_CHARS = 4000;

    private final SshCommandExecutor sshCommandExecutor;
    private final DeploymentEventPublisher eventPublisher;

    public ResolvedCompose buildAndResolve(Session session, String appId, String deploymentId,
                                            String releaseDir, String composeContent) {
        Map<String, Object> root = parseYaml(composeContent);
        Object servicesObj = root.get("services");
        if (!(servicesObj instanceof Map<?, ?> services)) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }

        Map<String, ServiceImageRef> imageRefs = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : services.entrySet()) {
            String serviceName = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> serviceDefRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> serviceDef = (Map<String, Object>) serviceDefRaw;

            Object buildObj = serviceDef.get("build");
            if (buildObj == null) {
                continue;
            }

            String buildContext = sanitize(extractBuildContext(buildObj));
            String dockerfile = extractDockerfile(buildObj);
            if (dockerfile != null) {
                dockerfile = sanitize(dockerfile);
            }
            String imageTag = "gamjabox/" + appId + "/" + serviceName + ":" + deploymentId;

            String contextPath = releaseDir + "/" + buildContext;
            StringBuilder buildCmd = new StringBuilder("docker build -t '").append(imageTag).append("'");
            if (dockerfile != null) {
                buildCmd.append(" -f '").append(contextPath).append("/").append(dockerfile).append("'");
            }
            buildCmd.append(" '").append(contextPath).append("'");

            CommandResult buildResult = sshCommandExecutor.exec(session, buildCmd.toString(), BUILD_TIMEOUT_MS);
            eventPublisher.publish(deploymentId, DeploymentEventType.BUILD_LOG,
                    "[" + serviceName + "] 빌드 로그", tail(buildResult.stdout() + buildResult.stderr()));
            if (!buildResult.isSuccess()) {
                throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
            }

            CommandResult inspect = sshCommandExecutor.execOrThrow(session,
                    "docker image inspect --format '{{.Id}}' '" + imageTag + "'", INSPECT_TIMEOUT_MS);
            String imageId = inspect.stdout().trim();

            imageRefs.put(serviceName, new ServiceImageRef(imageTag, imageId));

            serviceDef.remove("build");
            serviceDef.put("image", imageTag);
        }

        String resolvedComposeContent = dumpYaml(root);
        return new ResolvedCompose(resolvedComposeContent, imageRefs);
    }

    private Map<String, Object> parseYaml(String composeContent) {
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(composeContent);
            if (!(loaded instanceof Map)) {
                throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) loaded;
            return root;
        } catch (OpsException e) {
            throw e;
        } catch (Exception e) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
    }

    private String dumpYaml(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(root);
    }

    private String extractBuildContext(Object buildObj) {
        if (buildObj instanceof String s) {
            return s;
        }
        if (buildObj instanceof Map<?, ?> m) {
            Object ctx = m.get("context");
            return ctx != null ? String.valueOf(ctx) : ".";
        }
        return ".";
    }

    private String extractDockerfile(Object buildObj) {
        if (buildObj instanceof Map<?, ?> m) {
            Object df = m.get("dockerfile");
            return df != null ? String.valueOf(df) : null;
        }
        return null;
    }

    private String tail(String log) {
        if (log == null) {
            return "";
        }
        return log.length() > BUILD_LOG_TAIL_CHARS ? log.substring(log.length() - BUILD_LOG_TAIL_CHARS) : log;
    }

    private String sanitize(String pathSegment) {
        if (pathSegment == null || UNSAFE_PATH_SEGMENT.matcher(pathSegment).find()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        return pathSegment;
    }
}
