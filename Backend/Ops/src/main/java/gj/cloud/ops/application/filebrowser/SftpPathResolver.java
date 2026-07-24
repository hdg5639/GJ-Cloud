package gj.cloud.ops.application.filebrowser;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 파일 브라우저 접근 루트를 /home/{vmSshUsername} 이하로 제한.
// 심볼릭 링크를 통한 루트 탈출까지 막기 위해 SFTP REALPATH(원격 서버가 직접 해석)로 canonical path를 확인함 —
// 클라이언트 측 문자열 비교만으로는 심볼릭 링크 우회를 막을 수 없음.
@Component
@RequiredArgsConstructor
public class SftpPathResolver {

    private final VmSshSessionFactory sshSessionFactory;

    public String rootPath() {
        return "/home/" + sshSessionFactory.vmSshUsername();
    }

    // 이미 존재하는 파일/디렉토리 경로 검증
    public String resolveExisting(ChannelSftp sftp, String requestedPath) {
        String candidate = normalize(requestedPath);
        String real;
        try {
            real = sftp.realpath(candidate);
        } catch (SftpException e) {
            throw new OpsException(OpsErrorCode.FILE_NOT_FOUND);
        }
        assertWithinRoot(real);
        return real;
    }

    // 아직 존재하지 않는 대상(업로드 파일, 새 디렉토리) 경로 검증 — 부모만 realpath로 확인 후 이름을 덧붙임
    public String resolveNewEntry(ChannelSftp sftp, String parentPath, String rawName) {
        String parentReal = resolveExisting(sftp, parentPath);
        String name = sanitizeFileName(rawName);
        return parentReal + "/" + name;
    }

    public String sanitizeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new OpsException(OpsErrorCode.INVALID_FILE_NAME);
        }
        String name = rawName.trim();
        if (name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.contains("\0")) {
            throw new OpsException(OpsErrorCode.INVALID_FILE_NAME);
        }
        if (name.length() > 255) {
            throw new OpsException(OpsErrorCode.INVALID_FILE_NAME);
        }
        return name;
    }

    private String normalize(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return rootPath();
        }
        // 1차 방어: 텍스트 상의 ".." 세그먼트부터 차단 (realpath 호출 전 조기 차단)
        if (requestedPath.contains("..")) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        return requestedPath.startsWith("/") ? requestedPath : rootPath() + "/" + requestedPath;
    }

    private void assertWithinRoot(String real) {
        String root = rootPath();
        if (!real.equals(root) && !real.startsWith(root + "/")) {
            throw new OpsException(OpsErrorCode.PATH_ACCESS_DENIED);
        }
    }
}
