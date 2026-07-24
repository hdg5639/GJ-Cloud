package gj.cloud.ops.application.deployment.git;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
            String cloneCmd = "GIT_ASKPASS='" + askpassPath + "' GIT_TERMINAL_PROMPT=0 git -c http.followRedirects=false clone --mirror '"
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
            String fetchCmd = "GIT_ASKPASS='" + askpassPath + "' GIT_TERMINAL_PROMPT=0 git -c http.followRedirects=false -C '" + bareDir
                    + "' fetch --prune origin";
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
                    throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
                }
                if (attempt == GIT_NETWORK_RETRY_ATTEMPTS) {
                    log.error("Git 네트워크 오류 재시도 소진(operation={}, attempts={}): stderr={}",
                            operation, GIT_NETWORK_RETRY_ATTEMPTS, result.stderr());
                    throw new OpsException(OpsErrorCode.REPOSITORY_NETWORK_UNAVAILABLE);
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
                    throw new OpsException(OpsErrorCode.REPOSITORY_NETWORK_UNAVAILABLE);
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

    public void createWorktree(Session session, String appId, String deploymentId, String commitSha) {
        String bareDir = bareRepoDir(appId);
        String targetDir = releaseDir(appId, deploymentId);
        sshCommandExecutor.execOrThrow(session,
                "git -C '" + bareDir + "' worktree add '" + targetDir + "' '" + commitSha + "'", GIT_TIMEOUT_MS);
    }

    // 정리 목적이므로 실패해도(이미 지워졌거나 등) 예외를 던지지 않음
    public void removeWorktree(Session session, String appId, String deploymentId) {
        String bareDir = bareRepoDir(appId);
        String targetDir = releaseDir(appId, deploymentId);
        sshCommandExecutor.exec(session, "git -C '" + bareDir + "' worktree remove --force '" + targetDir + "'", GIT_TIMEOUT_MS);
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
