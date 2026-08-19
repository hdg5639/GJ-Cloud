package gj.cloud.ops.application.vmclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.web.client.RestClientResponseException;

final class VmClientErrorMapper {

    private static final int MAX_REMOTE_MESSAGE_LENGTH = 500;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VmClientErrorMapper() {
    }

    static OpsException routeSync(RestClientResponseException error) {
        int status = error.getStatusCode().value();
        OpsErrorCode code = switch (status) {
            case 400, 422 -> OpsErrorCode.DEPLOYMENT_ROUTE_INVALID;
            case 401 -> OpsErrorCode.INVALID_TOKEN;
            case 403 -> OpsErrorCode.FORBIDDEN;
            case 404 -> OpsErrorCode.VM_NOT_FOUND;
            case 409 -> OpsErrorCode.DEPLOYMENT_ROUTE_CONFLICT;
            default -> OpsErrorCode.DEPLOYMENT_ROUTE_SYNC_FAILED;
        };
        return new OpsException(code, remoteMessage(error, code.getMessage()));
    }

    private static String remoteMessage(RestClientResponseException error, String fallback) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(error.getResponseBodyAsString());
            String message = root.path("message").asText("").trim();
            if (message.isBlank()) return fallback;
            String sanitized = message.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
            return sanitized.length() <= MAX_REMOTE_MESSAGE_LENGTH
                    ? sanitized
                    : sanitized.substring(0, MAX_REMOTE_MESSAGE_LENGTH);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
