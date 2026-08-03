package gj.cloud.ops.application.deployment.git;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// DEP-001: 저장소 URL은 스킴/문자셋만 검증되고 호스트가 실제로 어디를 가리키는지는 확인되지 않았음 —
// 로그인 유저가 repoUrl에 내부망 주소(루프백/사설대역/링크로컬/클라우드 메타데이터 169.254.169.254 등)를
// 넣으면 Ops 컨테이너 자신의 네트워크 위치에서 SSRF가 가능했다(RepositorySnapshotBuilder는 Ops 컨테이너
// 로컬 프로세스에서 직접 클론하므로 크로스 테넌트 영향이 크다). 운영자가 신뢰한 호스트 allowlist를 먼저 강제해
// 공격자 소유 DNS rebinding 경로를 제거하고, 클론 직전 DNS 해석 결과가 내부/예약 대역이면 다시 거부한다.
@Slf4j
@Component
public class GitCloneSecurityValidator {

    private final Set<String> allowedHosts;

    public GitCloneSecurityValidator(
            @Value("${ops.git.allowed-hosts:github.com,gitlab.com,bitbucket.org}") String allowedHosts
    ) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public void assertHostNotInternal(String repoUrl) {
        String host;
        try {
            host = new URL(repoUrl).getHost();
        } catch (MalformedURLException e) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(normalizedHost)) {
            log.warn("저장소 클론 거부(allowlist 외 호스트): host={}", normalizedHost);
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException e) {
            log.warn("저장소 호스트 DNS 해석 실패: host={}", host);
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isMulticastAddress()
                    || address.isAnyLocalAddress()
                    || isIpv6UniqueLocal(address)
                    || isCloudMetadataAddress(address)) {
                log.warn("저장소 클론 거부(내부/예약 주소 해석됨): host={}, resolved={}", host, address.getHostAddress());
                throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
            }
        }
    }

    private boolean isCloudMetadataAddress(InetAddress address) {
        // AWS/GCP/Azure/OCI 공통 메타데이터 엔드포인트
        return "169.254.169.254".equals(address.getHostAddress());
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
