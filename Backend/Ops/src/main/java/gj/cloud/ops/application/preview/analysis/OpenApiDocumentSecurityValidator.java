package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

// GitCloneSecurityValidator와 동일한 SSRF 차단 규칙(DEP-001) — API 문서 URL도 사용자 입력이고 Ops
// 컨테이너 자신이 서버 사이드로 fetch하므로 내부망/클라우드 메타데이터 주소를 가리키면 안 된다.
// 기존 클래스를 건드리지 않기 위해(git 패키지 전용 문구/에러코드) 의도적으로 중복 정의함
// (RepositorySnapshotBuilder도 같은 이유로 SAFE_REPO_URL을 중복 정의한 전례를 따름).
@Slf4j
@Component
public class OpenApiDocumentSecurityValidator {

    private static final Pattern SAFE_DOC_URL = Pattern.compile("^https://[A-Za-z0-9._/:@?&=%-]+$");

    public void validate(String apiDocsUrl) {
        if (apiDocsUrl == null || !SAFE_DOC_URL.matcher(apiDocsUrl).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_API_DOCS_URL);
        }

        String host;
        try {
            URL url = new URL(apiDocsUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) {
                throw new OpsException(OpsErrorCode.INVALID_API_DOCS_URL);
            }
            host = url.getHost();
        } catch (MalformedURLException e) {
            throw new OpsException(OpsErrorCode.INVALID_API_DOCS_URL);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            log.warn("API 문서 호스트 DNS 해석 실패: host={}", host);
            throw new OpsException(OpsErrorCode.INVALID_API_DOCS_URL);
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isMulticastAddress()
                    || address.isAnyLocalAddress()
                    || isCloudMetadataAddress(address)) {
                log.warn("API 문서 조회 거부(내부/예약 주소 해석됨): host={}, resolved={}", host, address.getHostAddress());
                throw new OpsException(OpsErrorCode.INVALID_API_DOCS_URL);
            }
        }
    }

    private boolean isCloudMetadataAddress(InetAddress address) {
        return "169.254.169.254".equals(address.getHostAddress());
    }
}
