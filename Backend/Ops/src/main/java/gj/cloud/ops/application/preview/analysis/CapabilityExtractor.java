package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// GamjaBox_2.0_Key_Features.md 5·8절 규칙 — RuleBasedSpecInferrer와 같은 역할을 OpenAPI 오퍼레이션에
// 대해 수행. AI를 부르지 않고 경로/메서드/쿼리 파라미터 패턴만으로 확정 가능한 capability를 뽑는다.
@Component
public class CapabilityExtractor {

    private static final Set<String> SEARCH_PARAM_NAMES = Set.of("search", "keyword", "q", "query");
    private static final Set<String> SORT_PARAM_NAMES = Set.of("sort", "orderby", "order");
    private static final Set<String> PAGINATION_PARAM_NAMES =
            Set.of("page", "size", "limit", "offset", "pagesize", "perpage");
    private static final Set<String> LOGIN_TEXT_HINTS = Set.of("login", "signin", "sign-in", "authenticate");
    private static final Set<String> USERNAME_FIELD_HINTS =
            Set.of("email", "username", "userid", "loginid", "login_id", "user");
    private static final Set<String> PASSWORD_FIELD_HINTS = Set.of("password", "pw", "pwd");

    public List<Capability> extract(OpenApiEvidence evidence) {
        List<Capability> capabilities = new ArrayList<>();
        Optional<Capability> login = extractLoginCapability(evidence);
        login.ifPresent(capabilities::add);

        for (ApiOperationEvidence operation : evidence.operations()) {
            // 로그인으로 이미 분류된 오퍼레이션은 일반 CRUD 규칙("POST /auth/login" → 생성)으로
            // 또 잡히지 않게 제외한다 — 같은 엔드포인트가 두 capability로 중복되면 안 됨.
            if (login.isPresent() && isSameOperation(operation, login.get())) {
                continue;
            }
            extractCrudCapability(operation).ifPresent(capabilities::add);
        }
        return capabilities;
    }

    private boolean isSameOperation(ApiOperationEvidence operation, Capability capability) {
        return operation.method().equals(capability.method()) && operation.path().equals(capability.path());
    }

    private Optional<Capability> extractCrudCapability(ApiOperationEvidence operation) {
        String resourceName = resourceNameOf(operation.path());
        if (resourceName == null) {
            return Optional.empty();
        }
        boolean lastSegmentIsParam = lastSegmentIsParam(operation.path());

        CapabilityType type = switch (operation.method()) {
            case "GET" -> lastSegmentIsParam ? CapabilityType.DETAIL
                    : (operation.responseIsArray() ? CapabilityType.LIST : null);
            case "POST" -> lastSegmentIsParam ? null : CapabilityType.CREATE;
            case "PUT", "PATCH" -> lastSegmentIsParam ? CapabilityType.UPDATE : null;
            case "DELETE" -> lastSegmentIsParam ? CapabilityType.DELETE : null;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }

        List<String> evidenceLines = new ArrayList<>();
        evidenceLines.add(operation.method() + " " + operation.path()
                + (operation.operationId() != null ? " (operationId=" + operation.operationId() + ")" : ""));

        boolean hasSearch = false;
        boolean hasSort = false;
        boolean hasPagination = false;
        if (type == CapabilityType.LIST) {
            for (ApiParameterEvidence param : operation.parameters()) {
                if (!"query".equalsIgnoreCase(param.in())) {
                    continue;
                }
                String name = param.name().toLowerCase();
                if (SEARCH_PARAM_NAMES.contains(name)) {
                    hasSearch = true;
                }
                if (SORT_PARAM_NAMES.contains(name)) {
                    hasSort = true;
                }
                if (PAGINATION_PARAM_NAMES.contains(name)) {
                    hasPagination = true;
                }
            }
        }

        String confidence = operation.operationId() != null
                ? RepositoryEvidence.CONFIDENCE_HIGH
                : RepositoryEvidence.CONFIDENCE_MEDIUM;
        if (operation.operationId() == null) {
            evidenceLines.add("operationId가 없어 경로/메서드 패턴만으로 추정함");
        }

        List<String> fields = (type == CapabilityType.CREATE || type == CapabilityType.UPDATE)
                ? operation.requestBodyFields()
                : List.of();

        return Optional.of(new Capability(
                Capability.idOf(resourceName, type), resourceName, type,
                operation.operationId(), operation.path(), operation.method(),
                hasSearch, hasSort, hasPagination, confidence, evidenceLines, fields));
    }

    // 문서 전체에서 로그인 오퍼레이션 후보를 찾는다 — 텍스트 힌트(경로/operationId/태그)와 요청 필드
    // (username-like + password-like)를 함께 확인해 신뢰도를 나눈다.
    private Optional<Capability> extractLoginCapability(OpenApiEvidence evidence) {
        Capability best = null;
        for (ApiOperationEvidence operation : evidence.operations()) {
            if (!"POST".equals(operation.method())) {
                continue;
            }
            boolean textHint = containsLoginHint(operation.path())
                    || containsLoginHint(operation.operationId())
                    || containsLoginHint(operation.summary())
                    || operation.tags().stream().anyMatch(this::containsLoginHint);
            boolean hasUsernameField = operation.requestBodyFields().stream()
                    .anyMatch(field -> matchesAny(field, USERNAME_FIELD_HINTS));
            boolean hasPasswordField = operation.requestBodyFields().stream()
                    .anyMatch(field -> matchesAny(field, PASSWORD_FIELD_HINTS));

            if (!textHint && !(hasUsernameField && hasPasswordField)) {
                continue;
            }

            List<String> evidenceLines = new ArrayList<>();
            evidenceLines.add("POST " + operation.path()
                    + (operation.operationId() != null ? " (operationId=" + operation.operationId() + ")" : ""));
            String confidence;
            if (hasUsernameField && hasPasswordField) {
                confidence = RepositoryEvidence.CONFIDENCE_HIGH;
                evidenceLines.add("요청 필드에서 아이디/비밀번호로 보이는 필드를 모두 확인함");
            } else if (textHint) {
                confidence = RepositoryEvidence.CONFIDENCE_LOW;
                evidenceLines.add("경로/operationId/태그에 로그인 관련 이름만 있고 요청 필드로는 확인하지 못함");
            } else {
                continue;
            }

            Capability candidate = new Capability(
                    "auth.login", "auth", CapabilityType.LOGIN,
                    operation.operationId(), operation.path(), operation.method(),
                    false, false, false, confidence, evidenceLines, operation.requestBodyFields());
            if (best == null || isHigherConfidence(candidate.confidence(), best.confidence())) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isHigherConfidence(String candidate, String current) {
        return rank(candidate) > rank(current);
    }

    private int rank(String confidence) {
        return switch (confidence) {
            case RepositoryEvidence.CONFIDENCE_HIGH -> 2;
            case RepositoryEvidence.CONFIDENCE_MEDIUM -> 1;
            default -> 0;
        };
    }

    private boolean containsLoginHint(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return LOGIN_TEXT_HINTS.stream().anyMatch(lower::contains);
    }

    private boolean matchesAny(String field, Set<String> hints) {
        String lower = field.toLowerCase();
        return hints.stream().anyMatch(lower::contains);
    }

    private boolean lastSegmentIsParam(String path) {
        List<String> segments = nonEmptySegments(path);
        return !segments.isEmpty() && segments.get(segments.size() - 1).startsWith("{");
    }

    // 경로의 마지막 비-파라미터 세그먼트를 리소스명으로 취급한다.
    // "/organizations/{id}/members" -> "members" (하위 리소스), "/organizations/{id}" -> "organizations".
    private String resourceNameOf(String path) {
        List<String> segments = nonEmptySegments(path);
        int idx = segments.size() - 1;
        if (idx >= 0 && segments.get(idx).startsWith("{")) {
            idx--;
        }
        return idx >= 0 ? segments.get(idx) : null;
    }

    private List<String> nonEmptySegments(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();
    }
}
