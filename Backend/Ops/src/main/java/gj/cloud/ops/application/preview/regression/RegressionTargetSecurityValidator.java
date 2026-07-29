package gj.cloud.ops.application.preview.regression;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
@Slf4j
public class RegressionTargetSecurityValidator {

    public void validate(String targetUrl) {
        URI uri;
        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException error) {
            throw invalid();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw invalid();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()
                        || address.isAnyLocalAddress()
                        || "169.254.169.254".equals(address.getHostAddress())) {
                    log.warn("회귀 테스트 대상 URL 거부(내부/예약 주소): host={}, resolved={}",
                            uri.getHost(), address.getHostAddress());
                    throw invalid();
                }
            }
        } catch (UnknownHostException error) {
            log.warn("회귀 테스트 대상 호스트 DNS 해석 실패: host={}", uri.getHost());
            throw invalid();
        }
    }

    private OpsException invalid() {
        return new OpsException(OpsErrorCode.REGRESSION_TARGET_URL_INVALID);
    }
}
