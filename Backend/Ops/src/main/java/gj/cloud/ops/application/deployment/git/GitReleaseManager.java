package gj.cloud.ops.application.deployment.git;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.application.deployment.dto.DeploymentCommandLogPayload;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

// D.7 Release 디렉토리 구조 그대로 구현: bare mirror(최초 1회) + 배포별 git worktree + current 심볼릭 링크.
// 빈 release 디렉토리에서는 git fetch가 안 되므로(Git 저장소가 아님) 반드시 이 순서(bare 먼저, worktree는 그 산출물)를 지켜야 함.
@Slf4j
@Component
@RequiredArgsConstructor
public class GitReleaseManager {

    private static final long GIT_TIMEOUT_MS = 120_000; // clone/fetch는 레포 크기에 따라 오래 걸릴 수 있음
    private static final int GIT_NETWORK_RETRY_ATTEMPTS = 3;
    private static final long GIT_NETWORK_RETRY_DELAY_MS = 5_000;
    private static final String BASE_DIR_TEMPLATE = "/home/%s/gamjabox/apps/%s";
    private static final String GIT_RESOURCE_LIMITS = "timeout --signal=TERM --kill-after=5s 120s "
            + "prlimit --as=536870912 --fsize=536870912 --cpu=120 --nproc=64 --nofile=256 -- ";

    // repoUrl/branch는 사용자 입력이 그대로 셸 커맨드 문자열에 꽂히므로 반드시 사전 검증함.
    // https:// 로만 제한하는 이유: git의 ext:: 트랜스포트는 임의 셸 명령을 실행할 수 있는 알려진 벡터라
    // 스킴을 아예 화이트리스트로 막아야 함 (D.3도 HTTPS + PAT 흐름만 다룸, ssh://·file:// 등은 스코프 밖)
    private static final Pattern SAFE_REPO_URL = Pattern.compile("^https://[A-Za-z0-9._/:@-]+$");
    private static final Pattern SAFE_BRANCH_NAME = Pattern.compile("^[A-Za-z0-9._/-]+$");
    private static final Pattern SAFE_COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    // installPath는 사용자가 지정한 VM 절대경로 — 그대로 ln/mkdir 셸 커맨드에 꽂히므로 반드시 검증
    private static final Pattern UNSAFE_SHELL_CHARS = Pattern.compile("[;&`|$'\"\\\\]");
    private static final Set<String> PROTECTED_INSTALL_PATHS = Set.of(
            "/", "/etc", "/bin", "/usr", "/var", "/root", "/boot", "/sys", "/proc", "/dev", "/sbin", "/lib", "/lib64", "/home");

    private final SshCommandExecutor sshCommandExecutor;
    private final VmSshSessionFactory sshSessionFactory;
    private final GitCloneSecurityValidator gitCloneSecurityValidator;

    // 사용자 VM에서 실행되는 Git은 Ops 컨테이너의 Docker DNS 이름을 사용할 수 없다.
    // 원격 VM에서 실제로 도달 가능한 프록시 주소가 명시된 경우에만 적용한다.
    @Value("${ops.git.remote-egress-proxy-url:}")
    private String gitRemoteEgressProxyUrl;

    public String appBaseDir(String appId) {
        return String.format(BASE_DIR_TEMPLATE, sshSessionFactory.vmSshUsername(), appId);
    }

    public String releaseDir(String appId, String deploymentId) {
        return appBaseDir(appId) + "/releases/" + deploymentId;
    }

    private String bareRepoDir(String appId) {
        return appBaseDir(appId) + "/repository.git";
    }

    // bare mirror가 이미 있으면 그대로 재사용 (매번 전체 clone 방지)
    public void ensureBareRepo(Session session, String appId, String repoUrl, String patToken) {
        validateRepoUrl(repoUrl);
        // DEP-001: 고객 VM에서 실행되지만, 내부망(사설대역/링크로컬/클라우드 메타데이터) 주소로 클론을
        // 요청하면 그 VM의 네트워크 위치에서 SSRF가 가능하므로 동일하게 사전 차단(방어 심층화)
        gitCloneSecurityValidator.assertHostNotInternal(repoUrl);
        String baseDir = appBaseDir(appId);
        String bareDir = bareRepoDir(appId);

        CommandResult check = sshCommandExecutor.exec(session, "test -d '" + bareDir + "'", 10_000);
        if (check.isSuccess()) {
            return;
        }

        sshCommandExecutor.execOrThrow(session, "mkdir -p '" + baseDir + "' '" + baseDir + "/releases'", 10_000);

        withAskpass(session, patToken, askpassPath -> {
            // http.followRedirects=false: 사전 검증을 통과한 뒤 리다이렉트로 내부 주소로 우회하는 경로 차단
            String cloneCmd = "GIT_ASKPASS='" + askpassPath + "' GIT_TERMINAL_PROMPT=0 "
                    + limitedGitCommand() + " clone --mirror --filter=blob:none '"
                    + repoUrl + "' '" + bareDir + "'";
            execGitNetworkCommandWithRetry(session, cloneCmd, "저장소 최초 clone");
        });
    }

    // fetch 후 브랜치의 HEAD commit SHA를 고정해 반환 (worktree add에 사용).
    // --mirror clone은 refs/heads/*가 원격과 1:1 매핑되므로 origin/{branch}가 아니라 {branch}로 직접 resolve.
    public String fetchAndResolveCommit(Session session, String appId, String branch, String patToken) {
        return fetchAndResolveCommit(session, appId, branch, patToken, null);
    }

    public String fetchAndResolveCommit(
            Session session,
            String appId,
            String branch,
            String patToken,
            String requestedRevision
    ) {
        validateBranch(branch);
        if (requestedRevision != null && !requestedRevision.isBlank()
                && !SAFE_COMMIT_SHA.matcher(requestedRevision).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
        String bareDir = bareRepoDir(appId);

        withAskpass(session, patToken, askpassPath -> {
            String fetchCmd = "GIT_ASKPASS='" + askpassPath + "' GIT_TERMINAL_PROMPT=0 "
                    + limitedGitCommand() + " -C '" + bareDir
                    + "' fetch --filter=blob:none --prune origin";
            execGitNetworkCommandWithRetry(session, fetchCmd, "저장소 fetch");
        });

        String revision = branch;
        if (requestedRevision != null && !requestedRevision.isBlank()) {
            revision = requestedRevision;
            sshCommandExecutor.execOrThrow(session,
                    "git -C '" + bareDir + "' cat-file -e '" + requestedRevision + "^{commit}'", 15_000);
        }
        CommandResult revParse = sshCommandExecutor.execOrThrow(session,
                "git -C '" + bareDir + "' rev-parse '" + revision + "'", 15_000);
        return revParse.stdout().trim();
    }

    // VM 부팅 직후 systemd-resolved/network-online 정착이 늦거나 외부 DNS가 순간적으로 응답하지 않으면
    // git은 exit=128로 즉시 종료한다. 인증 실패/없는 저장소 같은 확정 오류까지 재시도하면 사용자 대기 시간만
    // 늘어나므로 DNS·연결 계층의 일시 오류와 명령 타임아웃만 짧게 재시도한다.
    CommandResult execGitNetworkCommandWithRetry(Session session, String command, String operation) {
        for (int attempt = 1; attempt <= GIT_NETWORK_RETRY_ATTEMPTS; attempt++) {
            try {
                CommandResult result = sshCommandExecutor.exec(session, command, GIT_TIMEOUT_MS);
                if (result.isSuccess()) {
                    return result;
                }
                if (!isTransientGitNetworkFailure(result.stderr())) {
                    log.error("Git 명령 실패(operation={}, exit={}): stderr={}", operation, result.exitStatus(), result.stderr());
                    throw new OpsException(
                            OpsErrorCode.SSH_COMMAND_FAILED,
                            operation + " 실패 (exit=" + result.exitStatus() + "): "
                                    + DeploymentCommandLogPayload.failureSummary(result));
                }
                if (attempt == GIT_NETWORK_RETRY_ATTEMPTS) {
                    log.error("Git 네트워크 오류 재시도 소진(operation={}, attempts={}): stderr={}",
                            operation, GIT_NETWORK_RETRY_ATTEMPTS, result.stderr());
                    throw new OpsException(
                            OpsErrorCode.REPOSITORY_NETWORK_UNAVAILABLE,
                            operation + " 네트워크 오류 (" + GIT_NETWORK_RETRY_ATTEMPTS
                                    + "회 재시도 소진, exit=" + result.exitStatus() + "): "
                                    + DeploymentCommandLogPayload.failureSummary(result));
                }
                log.warn("Git 네트워크 일시 오류 — 재시도 예정: operation={}, attempt={}/{}, stderr={}",
                        operation, attempt, GIT_NETWORK_RETRY_ATTEMPTS, result.stderr());
            } catch (OpsException e) {
                if (e.getErrorCode() != OpsErrorCode.SSH_COMMAND_TIMEOUT) {
                    throw e;
                }
                if (attempt == GIT_NETWORK_RETRY_ATTEMPTS) {
                    log.error("Git 명령 타임아웃 재시도 소진(operation={}, attempts={})",
                            operation, GIT_NETWORK_RETRY_ATTEMPTS);
                    throw new OpsException(
                            OpsErrorCode.REPOSITORY_NETWORK_UNAVAILABLE,
                            operation + " 시간 초과 (" + GIT_NETWORK_RETRY_ATTEMPTS + "회 재시도 소진)");
                }
                log.warn("Git 명령 일시 타임아웃 — 재시도 예정: operation={}, attempt={}/{}",
                        operation, attempt, GIT_NETWORK_RETRY_ATTEMPTS);
            }
            pauseBeforeGitRetry();
        }
        throw new OpsException(OpsErrorCode.REPOSITORY_NETWORK_UNAVAILABLE);
    }

    static boolean isTransientGitNetworkFailure(String stderr) {
        if (stderr == null) {
            return false;
        }
        String message = stderr.toLowerCase();
        return message.contains("could not resolve host")
                || message.contains("temporary failure in name resolution")
                || message.contains("name or service not known")
                || message.contains("failed to connect")
                || message.contains("connection timed out")
                || message.contains("connection reset")
                || message.contains("network is unreachable")
                || message.contains("remote end hung up unexpectedly")
                || message.contains("the requested url returned error: 502")
                || message.contains("the requested url returned error: 503")
                || message.contains("the requested url returned error: 504");
    }

    void pauseBeforeGitRetry() {
        try {
            Thread.sleep(GIT_NETWORK_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        }
    }

    public void createWorktree(
            Session session,
            String appId,
            String deploymentId,
            String commitSha,
            String patToken
    ) {
        String bareDir = bareRepoDir(appId);
        String targetDir = releaseDir(appId, deploymentId);
        // blob:none partial clone은 최초 checkout 시 누락 blob을 가져올 수 있으므로 이 단계에도 단기 askpass를
        // 제공한다. 토큰은 명령줄에 넣지 않고 작업 직후 askpass 파일을 삭제한다.
        withAskpass(session, patToken, askpassPath -> {
            String command = "GIT_ASKPASS='" + askpassPath + "' GIT_TERMINAL_PROMPT=0 "
                    + limitedGitCommand() + " -C '" + bareDir + "' worktree add '"
                    + targetDir + "' '" + commitSha + "'";
            execGitNetworkCommandWithRetry(session, command, "worktree checkout");
        });
    }

    // 정리 목적이므로 실패해도(이미 지워졌거나 등) 예외를 던지지 않음
    public void removeWorktree(Session session, String appId, String deploymentId) {
        String bareDir = bareRepoDir(appId);
        String targetDir = releaseDir(appId, deploymentId);
        sshCommandExecutor.exec(session, "git -C '" + bareDir + "' worktree remove --force '" + targetDir + "'", GIT_TIMEOUT_MS);
    }

    // 배포 대상 완전 삭제용 — bare mirror(repository.git)·모든 릴리스 워크트리·current 심볼릭 링크를
    // 통째로 제거한다. appId는 항상 우리가 발급한 UUID(vmId 또는 targetId)이므로 셸에 안전하게 꽂을 수 있음.
    public void removeAppDirectory(Session session, String appId) {
        sshCommandExecutor.execOrThrow(session, "rm -rf '" + appBaseDir(appId) + "'", GIT_TIMEOUT_MS);
    }

    // linkInstallPath가 만든 심볼릭 링크만 제거(대상 콘텐츠는 건드리지 않음 — rm -f는 링크 자체만 지움).
    public void unlinkInstallPath(Session session, String installPath) {
        String validated = validateInstallPath(installPath);
        sshCommandExecutor.exec(session, "rm -f '" + validated + "'", 10_000);
    }

    // current 심볼릭 링크를 이번 배포로 갱신 (D.7 9단계 / 롤백 시 이전 배포로 되돌릴 때도 사용)
    public void updateCurrentSymlink(Session session, String appId, String deploymentId) {
        String current = appBaseDir(appId) + "/current";
        String target = releaseDir(appId, deploymentId);
        sshCommandExecutor.execOrThrow(session, "ln -sfn '" + target + "' '" + current + "'", 10_000);
    }

    // 사용자가 지정한 VM 내 임의 경로에 "current"를 가리키는 심볼릭 링크를 추가로 생성 — release/rollback
    // 디렉토리 체계(releases/{deploymentId} + current)는 그대로 두고, 접근하기 쉬운 별칭 경로만 하나 더 만드는
    // 방식. current 자체를 가리키므로 이후 롤백으로 current가 재지정돼도 이 경로는 자동으로 최신 상태를 따라감.
    public void linkInstallPath(Session session, String appId, String installPath) {
        String validated = validateInstallPath(installPath);
        String current = appBaseDir(appId) + "/current";
        int lastSlash = validated.lastIndexOf('/');
        String parent = lastSlash > 0 ? validated.substring(0, lastSlash) : null;
        if (parent != null && !parent.isBlank()) {
            sshCommandExecutor.execOrThrow(session, "mkdir -p '" + parent + "'", 10_000);
        }
        sshCommandExecutor.execOrThrow(session, "ln -sfn '" + current + "' '" + validated + "'", 10_000);
    }

    // installPath는 사용자 입력 그대로 셸 커맨드에 꽂히므로 절대경로·이스케이프 문자·주요 시스템 경로를 차단.
    // VM SSH 세션이 이미 일반 사용자 권한이라 실제 시스템 디렉토리 접근은 OS 권한으로도 막히지만, 방어 계층을 더함.
    private String validateInstallPath(String installPath) {
        if (installPath == null) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        String trimmed = installPath.trim();
        if (!trimmed.startsWith("/") || trimmed.contains("..") || UNSAFE_SHELL_CHARS.matcher(trimmed).find()) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        String normalized = trimmed.length() > 1 && trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (PROTECTED_INSTALL_PATHS.contains(normalized)) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        return normalized;
    }

    private void validateRepoUrl(String repoUrl) {
        if (repoUrl == null || !SAFE_REPO_URL.matcher(repoUrl).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
        try {
            URI uri = URI.create(repoUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
            }
        } catch (IllegalArgumentException e) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    // OPS-SEC-003: SAFE_BRANCH_NAME은 셸 메타문자는 막지만 '-'가 허용 문자 집합에 포함돼 있어 선행 위치 검사가 없으면
    // "-oProxyCommand=..." 같은 git/ssh 옵션 인젝션 브랜치명이 그대로 통과함 — 첫 글자가 '-'인 경우 별도로 차단
    private void validateBranch(String branch) {
        if (branch == null || !SAFE_BRANCH_NAME.matcher(branch).matches() || branch.startsWith("-")) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    private String limitedGitCommand() {
        StringBuilder command = new StringBuilder(GIT_RESOURCE_LIMITS).append("git");
        if (gitRemoteEgressProxyUrl != null && !gitRemoteEgressProxyUrl.isBlank()) {
            try {
                URI proxy = URI.create(gitRemoteEgressProxyUrl);
                if (!("http".equalsIgnoreCase(proxy.getScheme()) || "https".equalsIgnoreCase(proxy.getScheme()))
                        || proxy.getHost() == null
                        || proxy.getUserInfo() != null) {
                    throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
                }
            } catch (IllegalArgumentException e) {
                throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
            }
            command.append(" -c http.proxy=").append(gj.cloud.ops.global.ssh.PosixShellArgument.quote(gitRemoteEgressProxyUrl));
        }
        return command.append(" -c http.followRedirects=false").toString();
    }

    // GIT_ASKPASS 스크립트: PAT는 SFTP로 파일 내용만 전송(ps aux에 노출되지 않음), exec 커맨드 라인에는 경로만 등장시킴.
    // 사용 직후 삭제 (D.3 "credential helper 실행 직후 삭제").
    private void withAskpass(Session session, String patToken, Consumer<String> action) {
        if (patToken == null || patToken.isBlank()) {
            action.accept("/bin/true"); // public repo — 방어적으로 무해한 값 지정 (호출될 일 없음)
            return;
        }
        String scriptPath = "/tmp/gj-askpass-" + UUID.randomUUID() + ".sh";
        writeAskpassScript(session, scriptPath, patToken);
        try {
            action.accept(scriptPath);
        } finally {
            sshCommandExecutor.exec(session, "rm -f '" + scriptPath + "'", 10_000);
        }
    }

    // git이 askpass를 "Username for ...", "Password for ..." 두 번 호출하므로 프롬프트 종류별로 답을 구분
    private void writeAskpassScript(Session session, String scriptPath, String patToken) {
        String escapedPat = patToken.replace("'", "'\\''");
        String content = "#!/bin/sh\n"
                + "case \"$1\" in\n"
                + "  Username*) echo 'oauth2' ;;\n"
                + "  *) echo '" + escapedPat + "' ;;\n"
                + "esac\n";

        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(10_000);
            sftp.put(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), scriptPath);
        } catch (JSchException | SftpException e) {
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
        }
        sshCommandExecutor.execOrThrow(session, "chmod 700 '" + scriptPath + "'", 10_000);
    }
}
